package com.hostelmanagement.common.crypto;

import com.hostelmanagement.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Authenticated encryption (AES-256-GCM) for individual sensitive columns such as bank account numbers.
 *
 * <p>Each value gets a fresh random IV and is bound to a {@code context} string (for example the owner id) as additional
 * authenticated data, so a stolen ciphertext cannot be pasted into another owner's row, and any tampering is detected.
 *
 * <p>Key: {@code DATA_ENCRYPTION_KEY} (Base64 of 32 random bytes, for example {@code openssl rand -base64 32}). In
 * development a key derived from the JWT secret is used so features work out of the box. In production there is no
 * fallback: without the key {@link #available()} is false and callers refuse to store the data.
 */
@Slf4j
@Component
public class FieldCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    public FieldCipher(AppProperties props, Environment env) {
        String configured = props.security().dataKey();
        if (configured != null && !configured.isBlank()) {
            byte[] raw;
            try {
                raw = Base64.getDecoder().decode(configured.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalStateException("DATA_ENCRYPTION_KEY is not valid Base64. Generate one with: openssl rand -base64 32");
            }
            if (raw.length != 32) {
                throw new IllegalStateException("DATA_ENCRYPTION_KEY must decode to exactly 32 bytes (it decodes to " + raw.length
                        + "). Generate one with: openssl rand -base64 32");
            }
            this.key = new SecretKeySpec(raw, "AES");
        } else if (env.acceptsProfiles(Profiles.of("prod"))) {
            this.key = null;
            log.warn("DATA_ENCRYPTION_KEY is not set: bank account numbers cannot be stored until it is (see docs/PAYMENT_METHODS.md)");
        } else {
            this.key = new SecretKeySpec(sha256("hostelhub:dev-data-key:" + props.security().jwtSecret()), "AES");
            log.warn("DATA_ENCRYPTION_KEY is not set: using a development key derived from JWT_SECRET (do not rely on this in production)");
        }
    }

    public boolean available() {
        return key != null;
    }

    public byte[] encrypt(String plaintext, String context) {
        requireKey();
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(byte[] data, String context) {
        requireKey();
        if (data == null || data.length <= IV_BYTES) {
            throw new IllegalArgumentException("Not an encrypted value");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, Arrays.copyOfRange(data, 0, IV_BYTES)));
            cipher.updateAAD(context.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(data, IV_BYTES, data.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            // Wrong key, wrong context or tampered data: never reveal which
            throw new IllegalStateException("The stored value could not be decrypted");
        }
    }

    private void requireKey() {
        if (key == null) {
            throw new IllegalStateException("No data encryption key is configured");
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
