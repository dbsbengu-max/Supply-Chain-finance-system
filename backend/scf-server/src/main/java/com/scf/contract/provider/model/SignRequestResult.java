package com.scf.contract.provider.model;

public record SignRequestResult(
        String externalSignRef,
        String providerStatus,
        String providerMessage,
        String platformTraceId,
        String providerRequestId,
        String providerTraceId,
        String providerExchangeSummary) {

    public SignRequestResult(String externalSignRef, String providerStatus, String providerMessage) {
        this(externalSignRef, providerStatus, providerMessage, null, null, null, null);
    }
}
