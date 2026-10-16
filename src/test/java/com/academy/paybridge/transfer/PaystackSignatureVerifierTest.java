package com.academy.paybridge.transfer;

import com.academy.paybridge.transfer.client.paystack.PaystackProperties;
import com.academy.paybridge.transfer.web.PaystackSignatureVerifier;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

class PaystackSignatureVerifierTest {

    private static final String SECRET = "sk_test_secret";
    private final PaystackSignatureVerifier verifier =
            new PaystackSignatureVerifier(new PaystackProperties("http://x", SECRET));

    private static String sign(byte[] body, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
        return HexFormat.of().formatHex(mac.doFinal(body));
    }

    @Test void acceptsValidSignature() throws Exception {
        byte[] body = "{\"event\":\"transfer.success\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(body, sign(body, SECRET))).isTrue();
    }

    @Test void rejectsTamperedBody() throws Exception {
        byte[] body = "{\"event\":\"transfer.success\"}".getBytes(StandardCharsets.UTF_8);
        String sig = sign(body, SECRET);
        assertThat(verifier.isValid("{\"event\":\"transfer.failed\"}".getBytes(StandardCharsets.UTF_8), sig)).isFalse();
    }

    @Test void rejectsWrongSecretMissingAndGarbageSignature() throws Exception {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThat(verifier.isValid(body, sign(body, "other"))).isFalse();
        assertThat(verifier.isValid(body, null)).isFalse();
        assertThat(verifier.isValid(body, "not-hex!")).isFalse();
    }
}