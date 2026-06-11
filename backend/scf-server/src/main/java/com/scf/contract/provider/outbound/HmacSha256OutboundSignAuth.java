package com.scf.contract.provider.outbound;

import com.scf.common.exception.BusinessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class HmacSha256OutboundSignAuth implements OutboundSignAuthStrategy {

    @Override
    public OutboundSignAuthMode mode() {
        return OutboundSignAuthMode.HMAC_SHA256;
    }

    @Override
    public void apply(HttpHeaders headers, OutboundSignAuthContext context, String requestBody) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = UUID.randomUUID().toString().replace("-", "");
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add("X-Scf-App-Id", context.appId());
        headers.add("X-Scf-Timestamp", timestamp);
        headers.add("X-Scf-Nonce", nonce);
        headers.add("X-Scf-Signature", hmacSha256Hex(context.appSecret(), timestamp + "\n" + nonce + "\n" + requestBody));
    }

    static String hmacSha256Hex(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new BusinessException("CONTRACT_SIGN_500", "HMAC 签名生成失败", 500);
        }
    }
}
