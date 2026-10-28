package com.hostelmanagement.payments.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HexFormat;

/** HMAC-SHA256 helpers shared by adapters whose providers sign payloads that way. */
final class HmacSignatures {

    private HmacSignatures() {
    }

    static String hmacSha256Hex(String secret, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }

    static String hmacSha256Hex(String secret, String data) {
        return hmacSha256Hex(secret, data.getBytes(StandardCharsets.UTF_8));
    }

    /** Constant-time comparison so signature checks do not leak how many leading characters matched. */
    static boolean matches(String expectedHex, String providedHex) {
        if (expectedHex == null || providedHex == null) {
            return false;
        }
        return MessageDigest.isEqual(expectedHex.toLowerCase().getBytes(StandardCharsets.UTF_8),
                providedHex.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}
