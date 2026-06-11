package com.scf.contract.service;

import com.scf.common.exception.BusinessException;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.config.ContractSignProperties.ProductionRollout;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ContractSignRolloutResolver {

    private final ContractSignProperties properties;

    public ContractSignRolloutResolver(ContractSignProperties properties) {
        this.properties = properties;
    }

    /**
     * Resolves the provider for a new sign request. Explicit {@code requestedProvider}
     * is accepted only when rollout is OFF; once production rollout is enabled, request
     * payloads cannot bypass the configured gray route.
     */
    public String resolveProviderCode(String requestedProvider, String projectId, String operatorId) {
        ProductionRollout rollout = properties.getProductionRollout();
        String mode = normalizeMode(rollout.getMode());
        if ("OFF".equals(mode)) {
            if (requestedProvider != null && !requestedProvider.isBlank()) {
                return requestedProvider.trim();
            }
            return properties.getDefaultProvider();
        }
        String production = blankToDefault(rollout.getProductionProvider(), "ESIGN_HTTP");
        String fallback = blankToDefault(rollout.getFallbackProvider(), "MOCK");
        if (!shouldRouteToProduction(rollout, mode, projectId, operatorId)) {
            return fallback;
        }
        if (rollout.isRequireHttpConfigured() && production.equalsIgnoreCase(properties.getHttpProvider().getProviderCode())) {
            if (!properties.getHttpProvider().isConfigured()) {
                if (rollout.isBlockWhenMisconfigured()) {
                    throw new BusinessException(
                            "CONFIG_503",
                            "生产签章供应商未配置完整，灰度路由已阻断",
                            503);
                }
                return fallback;
            }
        }
        return production;
    }

    public ProductionRolloutSnapshot snapshot(String projectId, String operatorId) {
        ProductionRollout rollout = properties.getProductionRollout();
        String mode = normalizeMode(rollout.getMode());
        String effective = resolveProviderCode(null, projectId, operatorId);
        return new ProductionRolloutSnapshot(
                mode,
                rollout.getProductionProvider(),
                rollout.getFallbackProvider(),
                rollout.getProjectAllowlist(),
                rollout.getOperatorAllowlist(),
                rollout.getProjectHashPercent(),
                rollout.isRequireHttpConfigured(),
                rollout.isBlockWhenMisconfigured(),
                effective,
                shouldRouteToProduction(rollout, mode, projectId, operatorId));
    }

    private boolean shouldRouteToProduction(
            ProductionRollout rollout, String mode, String projectId, String operatorId) {
        return switch (mode) {
            case "OFF" -> false;
            case "FULL" -> true;
            case "ALLOWLIST" -> isListed(projectId, rollout.getProjectAllowlist())
                    || isListed(operatorId, rollout.getOperatorAllowlist());
            case "PERCENT" -> hashPercent(projectId) < clampPercent(rollout.getProjectHashPercent());
            default -> false;
        };
    }

    private static boolean isListed(String value, String allowlistCsv) {
        if (value == null || value.isBlank() || allowlistCsv == null || allowlistCsv.isBlank()) {
            return false;
        }
        Set<String> allowed = Arrays.stream(allowlistCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        return allowed.contains(value.trim());
    }

    private static int hashPercent(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return 100;
        }
        int hash = Math.abs(projectId.trim().hashCode());
        return hash % 100;
    }

    private static int clampPercent(int percent) {
        return Math.max(0, Math.min(100, percent));
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "OFF";
        }
        return mode.trim().toUpperCase(Locale.ROOT);
    }

    private static String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    public record ProductionRolloutSnapshot(
            String mode,
            String productionProvider,
            String fallbackProvider,
            String projectAllowlist,
            String operatorAllowlist,
            int projectHashPercent,
            boolean requireHttpConfigured,
            boolean blockWhenMisconfigured,
            String effectiveProviderForContext,
            boolean routedToProduction) {
    }
}
