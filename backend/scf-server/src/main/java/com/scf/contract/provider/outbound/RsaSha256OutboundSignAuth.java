package com.scf.contract.provider.outbound;

import com.scf.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Component
public class RsaSha256OutboundSignAuth implements OutboundSignAuthStrategy {

    @Override
    public OutboundSignAuthMode mode() {
        return OutboundSignAuthMode.RSA_SHA256;
    }

    @Override
    public void apply(HttpHeaders headers, OutboundSignAuthContext context, String requestBody) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String canonical = timestamp + "\n" + nonce + "\n" + requestBody;
        PrivateKey privateKey = PemKeys.loadPrivateKey(context.privateKeyPem());
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Scf-App-Id", context.appId());
        headers.add("X-Scf-Timestamp", timestamp);
        headers.add("X-Scf-Nonce", nonce);
        headers.add("X-Scf-Signature", signRsa(privateKey, canonical));
    }

    static String signRsa(PrivateKey privateKey, String payload) {
        try {
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception ex) {
            throw new BusinessException("CONTRACT_SIGN_500", "RSA 签名生成失败", 500);
        }
    }
}
