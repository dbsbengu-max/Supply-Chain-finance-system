package com.scf.contract.provider.outbound;

public record OutboundSignAuthContext(
        String appId,
        String appSecret,
        String privateKeyPem,
        String publicKeyPem,
        String platformTraceId) {
}
