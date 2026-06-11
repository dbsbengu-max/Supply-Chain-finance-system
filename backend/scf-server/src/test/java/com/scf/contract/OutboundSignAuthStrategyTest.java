package com.scf.contract;

import com.scf.contract.provider.outbound.HmacSha256OutboundSignAuth;
import com.scf.contract.provider.outbound.OutboundSignAuthContext;
import com.scf.contract.provider.outbound.RsaSha256OutboundSignAuth;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundSignAuthStrategyTest {

    @Test
    void ea045HmacStrategyProducesScfHeaders() {
        HttpHeaders headers = new HttpHeaders();
        new HmacSha256OutboundSignAuth().apply(headers, new OutboundSignAuthContext(
                "app-1", "secret-1", null, null, "TRACE-1"), "{\"task_id\":\"T1\"}");

        assertThat(headers.getFirst("X-Scf-App-Id")).isEqualTo("app-1");
        assertThat(headers.getFirst("X-Scf-Signature")).isNotBlank();
    }

    @Test
    void ea045RsaStrategyProducesBase64Signature() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String privatePem = toPkcs8Pem(keyPair.getPrivate().getEncoded());
        HttpHeaders headers = new HttpHeaders();
        new RsaSha256OutboundSignAuth().apply(headers, new OutboundSignAuthContext(
                "app-rsa", null, privatePem, null, "TRACE-RSA"), "{\"task_id\":\"T2\"}");

        String signature = headers.getFirst("X-Scf-Signature");
        assertThat(signature).isNotBlank();
        assertThat(verifyRsa(keyPair.getPublic(), canonical(headers, "{\"task_id\":\"T2\"}"), signature)).isTrue();
    }

    private static boolean verifyRsa(PublicKey publicKey, String payload, String signatureBase64) throws Exception {
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(payload.getBytes());
        return verifier.verify(Base64.getDecoder().decode(signatureBase64));
    }

    private static String canonical(HttpHeaders headers, String body) {
        return headers.getFirst("X-Scf-Timestamp") + "\n"
                + headers.getFirst("X-Scf-Nonce") + "\n"
                + body;
    }

    private static String toPkcs8Pem(byte[] encoded) {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(encoded);
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
