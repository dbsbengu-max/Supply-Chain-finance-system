package com.scf.contract.provider.outbound;

public enum OutboundSignAuthMode {
    HMAC_SHA256,
    RSA_SHA256,
    SM2;

    public static OutboundSignAuthMode fromConfig(String value) {
        if (value == null || value.isBlank()) {
            return HMAC_SHA256;
        }
        return switch (value.trim().toUpperCase()) {
            case "RSA", "RSA_SHA256", "RSA-SHA256" -> RSA_SHA256;
            case "SM2", "SM2_SM3", "国密", "GUOMI" -> SM2;
            default -> HMAC_SHA256;
        };
    }
}
