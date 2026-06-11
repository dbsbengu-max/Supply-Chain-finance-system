package com.scf.contract.service;

import com.scf.common.exception.BusinessException;
import com.scf.contract.config.ContractSignProperties;
import com.scf.contract.config.ContractSignProperties.ProductionRollout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContractSignRolloutResolverTest {

    private ContractSignProperties properties;
    private ContractSignRolloutResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new ContractSignProperties();
        properties.setDefaultProvider("ESIGN_HTTP");
        properties.getHttpProvider().setEnabled(true);
        properties.getHttpProvider().setProviderCode("ESIGN_HTTP");
        properties.getHttpProvider().setBaseUrl("https://vendor.example");
        properties.getHttpProvider().setAppId("app");
        properties.getHttpProvider().setAppSecret("secret");
        resolver = new ContractSignRolloutResolver(properties);
    }

    @Test
    void offModeUsesDefaultProvider() {
        rolloutMode("OFF");
        assertThat(resolver.resolveProviderCode(null, "PJ001", "OP001")).isEqualTo("ESIGN_HTTP");
    }

    @Test
    void allowlistRoutesOnlyListedProjects() {
        rolloutMode("ALLOWLIST");
        properties.getProductionRollout().setProjectAllowlist("PJ_PILOT");
        assertThat(resolver.resolveProviderCode(null, "PJ_PILOT", "OP001")).isEqualTo("ESIGN_HTTP");
        assertThat(resolver.resolveProviderCode(null, "PJ_OTHER", "OP001")).isEqualTo("MOCK");
    }

    @Test
    void percentModeIsDeterministicByProject() {
        rolloutMode("PERCENT");
        properties.getProductionRollout().setProjectHashPercent(100);
        assertThat(resolver.resolveProviderCode(null, "PJ001", "OP001")).isEqualTo("ESIGN_HTTP");
        properties.getProductionRollout().setProjectHashPercent(0);
        assertThat(resolver.resolveProviderCode(null, "PJ001", "OP001")).isEqualTo("MOCK");
    }

    @Test
    void explicitProviderOverridesOnlyWhenRolloutOff() {
        rolloutMode("OFF");
        assertThat(resolver.resolveProviderCode("MOCK", "PJ001", "OP001")).isEqualTo("MOCK");

        rolloutMode("FULL");
        assertThat(resolver.resolveProviderCode("MOCK", "PJ001", "OP001")).isEqualTo("ESIGN_HTTP");
    }

    @Test
    void blocksWhenProductionMisconfigured() {
        rolloutMode("FULL");
        properties.getHttpProvider().setAppSecret("");
        properties.getProductionRollout().setBlockWhenMisconfigured(true);
        assertThatThrownBy(() -> resolver.resolveProviderCode(null, "PJ001", "OP001"))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getCode())
                .isEqualTo("CONFIG_503");
    }

    @Test
    void fallsBackWhenMisconfiguredAndBlockDisabled() {
        rolloutMode("FULL");
        properties.getHttpProvider().setAppSecret("");
        properties.getProductionRollout().setBlockWhenMisconfigured(false);
        assertThat(resolver.resolveProviderCode(null, "PJ001", "OP001")).isEqualTo("MOCK");
    }

    private void rolloutMode(String mode) {
        ProductionRollout rollout = properties.getProductionRollout();
        rollout.setMode(mode);
        rollout.setProductionProvider("ESIGN_HTTP");
        rollout.setFallbackProvider("MOCK");
    }
}
