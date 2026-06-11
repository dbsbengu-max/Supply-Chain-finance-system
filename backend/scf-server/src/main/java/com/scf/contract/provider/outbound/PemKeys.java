package com.scf.contract.provider.outbound;

import com.scf.common.exception.BusinessException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

final class PemKeys {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    private PemKeys() {
    }

    static PrivateKey loadPrivateKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new BusinessException("CONTRACT_SIGN_500", "出站私钥未配置", 500);
        }
        byte[] decoded = decodePemBody(pem, "PRIVATE KEY", "EC PRIVATE KEY", "RSA PRIVATE KEY");
        Exception rsaFailure = null;
        for (String algorithm : new String[]{"RSA", "EC"}) {
            try {
                KeyFactory factory = KeyFactory.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
                return factory.generatePrivate(new PKCS8EncodedKeySpec(decoded));
            } catch (Exception ex) {
                rsaFailure = ex;
            }
        }
        throw new BusinessException("CONTRACT_SIGN_500", "出站私钥解析失败: " + rsaFailure.getMessage(), 500);
    }

    static PublicKey loadPublicKey(String pem) {
        if (pem == null || pem.isBlank()) {
            throw new BusinessException("CONTRACT_SIGN_500", "出站公钥未配置", 500);
        }
        byte[] decoded = decodePemBody(pem, "PUBLIC KEY");
        Exception failure = null;
        for (String algorithm : new String[]{"RSA", "EC"}) {
            try {
                KeyFactory factory = KeyFactory.getInstance(algorithm, BouncyCastleProvider.PROVIDER_NAME);
                return factory.generatePublic(new X509EncodedKeySpec(decoded));
            } catch (Exception ex) {
                failure = ex;
            }
        }
        throw new BusinessException("CONTRACT_SIGN_500", "出站公钥解析失败: " + failure.getMessage(), 500);
    }

    private static byte[] decodePemBody(String pem, String... labels) {
        String normalized = pem.trim();
        for (String label : labels) {
            normalized = normalized
                    .replace("-----BEGIN " + label + "-----", "")
                    .replace("-----END " + label + "-----", "");
        }
        normalized = normalized.replaceAll("\\s", "");
        try {
            return Base64.getDecoder().decode(normalized);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException("CONTRACT_SIGN_500", "PEM Base64 解码失败", 500);
        }
    }
}
