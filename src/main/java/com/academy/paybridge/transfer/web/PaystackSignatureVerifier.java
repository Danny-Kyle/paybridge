package com.academy.paybridge.transfer.web;

import com.academy.paybridge.transfer.client.paystack.PaystackProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

@Component
public class PaystackSignatureVerifier {

    private final byte[] key;

    public PaystackSignatureVerifier(PaystackProperties props) {
        this.key = (props.secretKey() == null ? "" : props.secretKey()).getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(byte[] rawBody, String signatureHex) {
        if (signatureHex == null || signatureHex.isBlank() || key.length == 0) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key, "HmacSHA512"));
            byte[] expected = mac.doFinal(rawBody);
            byte[] provided = HexFormat.of().parseHex(signatureHex.trim());
            return MessageDigest.isEqual(expected, provided);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}