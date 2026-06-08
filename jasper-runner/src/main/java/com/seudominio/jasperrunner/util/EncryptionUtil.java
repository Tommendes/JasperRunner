package com.seudominio.jasperrunner.util;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Utilitário de criptografia AES-128 com CBC + IV aleatório.
 * Formato armazenado: Base64(IV[16 bytes] + ciphertext)
 */
public class EncryptionUtil {

    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
    private static final int IV_SIZE = 16;
    private static final int KEY_SIZE = 16; // AES-128

    private final byte[] key;

    public EncryptionUtil(String encryptionKey) {
        byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
        this.key = Arrays.copyOf(keyBytes, KEY_SIZE);
    }

    public String encrypt(String plainText) {
        try {
            SecureRandom random = new SecureRandom();
            byte[] iv = new byte[IV_SIZE];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // Combina IV + ciphertext em um único array
            byte[] combined = new byte[IV_SIZE + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, IV_SIZE);
            System.arraycopy(encrypted, 0, combined, IV_SIZE, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar valor", e);
        }
    }

    public String decrypt(String encryptedBase64) {
        try {
            byte[] combined = Base64.getDecoder().decode(encryptedBase64);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_SIZE);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_SIZE, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));

            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao descriptografar valor", e);
        }
    }
}
