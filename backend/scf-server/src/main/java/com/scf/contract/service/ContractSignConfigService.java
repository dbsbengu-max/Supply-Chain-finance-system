package com.scf.contract.service;

import com.scf.common.security.TenantContext;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.dto.ContractSignDtos.ContractSignConfigView;
import com.scf.contract.dto.ContractSignDtos.ContractSignProviderConnectionView;
import com.scf.contract.dto.ContractSignDtos.ContractSignProviderView;
import com.scf.contract.dto.ContractSignDtos.ContractSignRolloutView;
import com.scf.contract.provider.ContractSignProvider;
import com.scf.contract.provider.ContractSignProviderRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ContractSignConfigService {

    private static final String CALLBACK_PATH = "/integrations/contracts/sign-callback";

    private final ContractSignProperties properties;
    private final ContractSignProviderRegistry providerRegistry;
    private final ContractSignRolloutResolver rolloutResolver;
    private final TenantContext tenantContext;

    public ContractSignConfigService(
            ContractSignProperties properties,
            ContractSignProviderRegistry providerRegistry,
            ContractSignRolloutResolver rolloutResolver,
            TenantContext tenantContext) {
        this.properties = properties;
        this.providerRegistry = providerRegistry;
        this.rolloutResolver = rolloutResolver;
        this.tenantContext = tenantContext;
    }

    @Transactional(readOnly = true)
    public ContractSignConfigView getConfig() {
        tenantContext.requirePermission("CONTRACT_SIGN_CONFIG_VIEW");
        boolean hmacPlanned = "TIMESTAMP_NONCE_SIGNATURE".equalsIgnoreCase(properties.getCallbackVerificationMode());
        var rolloutSnapshot = rolloutResolver.snapshot(
                tenantContext.projectId(),
                tenantContext.requireOperatorId());
        return new ContractSignConfigView(
                properties.getDefaultProvider(),
                properties.getMaxRetryCount(),
                properties.getCallbackVerificationMode(),
                properties.getCallbackSignatureWindowSeconds(),
                maskSecret(properties.getCallbackToken()),
                CALLBACK_PATH,
                List.of("X-Contract-Sign-Callback-Token", "X-Idempotency-Key"),
                hmacPlanned
                        ? List.of("X-Contract-Sign-Timestamp", "X-Contract-Sign-Nonce", "X-Contract-Sign-Signature", "X-Idempotency-Key")
                        : List.of("X-Contract-Sign-Timestamp", "X-Contract-Sign-Nonce", "X-Contract-Sign-Signature", "X-Idempotency-Key"),
                properties.isCompensationPoolEnabled(),
                List.of(toHttpProviderConnection()),
                toRolloutView(rolloutSnapshot));
    }

    private ContractSignRolloutView toRolloutView(ContractSignRolloutResolver.ProductionRolloutSnapshot snapshot) {
        return new ContractSignRolloutView(
                snapshot.mode(),
                snapshot.productionProvider(),
                snapshot.fallbackProvider(),
                snapshot.projectAllowlist(),
                snapshot.operatorAllowlist(),
                snapshot.projectHashPercent(),
                snapshot.requireHttpConfigured(),
                snapshot.blockWhenMisconfigured(),
                snapshot.effectiveProviderForContext(),
                snapshot.routedToProduction());
    }

    private ContractSignProviderConnectionView toHttpProviderConnection() {
        ContractSignProperties.HttpProvider http = properties.getHttpProvider();
        return new ContractSignProviderConnectionView(
                http.getProviderCode(),
                http.isEnabled(),
                http.isConfigured(),
                http.getOutboundAuthMode(),
                http.getPlatformTraceHeader(),
                blankToDash(http.getBaseUrl()),
                blankToDash(http.getAppId()),
                maskSecret(http.getAppSecret()));
    }

    private static String blankToDash(String value) {
        return value == null || value.isBlank() ? "—" : value.trim();
    }

    @Transactional(readOnly = true)
    public List<ContractSignProviderView> listProviders() {
        tenantContext.requirePermission("CONTRACT_SIGN_CONFIG_VIEW");
        return providerRegistry.listProviders().stream()
                .map(this::toProviderView)
                .toList();
    }

    private ContractSignProviderView toProviderView(ContractSignProvider provider) {
        return new ContractSignProviderView(
                provider.providerCode(),
                provider.displayName(),
                provider.description(),
                provider.supportsStatusQuery());
    }

    private static String maskSecret(String token) {
        if (token == null || token.isBlank()) {
            return "—";
        }
        String trimmed = token.trim();
        if (trimmed.length() <= 8) {
            return "****";
        }
        return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
    }
}
