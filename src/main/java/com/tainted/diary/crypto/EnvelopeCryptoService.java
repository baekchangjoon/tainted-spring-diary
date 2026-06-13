package com.tainted.diary.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 서버측 시뮬레이션 KMS Envelope Encryption.
 *
 * <ul>
 *   <li>per-entry 랜덤 256-bit DEK 생성 → AES/GCM으로 plaintext 암호화</li>
 *   <li>DEK 자체를 설정 KEK(256-bit)로 AES/GCM 암호화 → encryptedDek 저장</li>
 *   <li>복호화 시: encryptedDek → DEK 복원 → plaintext 복원</li>
 * </ul>
 *
 * 모든 바이너리 값은 Base64(URL-safe 아닌 기본) 문자열로 교환.
 */
@Component
public class EnvelopeCryptoService {

    private static final String AES_GCM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LEN = 12;   // bytes
    private static final int GCM_TAG_LEN = 128;  // bits

    private final SecretKey kek;
    private final SecureRandom random = new SecureRandom();

    public EnvelopeCryptoService(@Value("${diary.kek-base64}") String kekBase64) {
        byte[] kekBytes = Base64.getDecoder().decode(kekBase64);
        if (kekBytes.length != 32) {
            throw new IllegalArgumentException(
                    "KEK must be 32 bytes (256 bits); got " + kekBytes.length);
        }
        this.kek = new SecretKeySpec(kekBytes, "AES");
    }

    /**
     * plaintext를 envelope 암호화하여 {@link CipherResult}를 반환한다.
     * DEK·IV가 매 호출마다 랜덤이므로 같은 입력도 암호문이 다르다.
     */
    public CipherResult encrypt(String plaintext) {
        try {
            // 1. 랜덤 DEK 생성
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256, random);
            SecretKey dek = kg.generateKey();

            // 2. plaintext → DEK로 암호화
            byte[] contentIv = randomIv();
            byte[] ciphertext = aesGcmEncrypt(
                    plaintext.getBytes(StandardCharsets.UTF_8), dek, contentIv);

            // 3. DEK → KEK로 암호화
            byte[] dekIv = randomIv();
            byte[] encryptedDek = aesGcmEncrypt(dek.getEncoded(), kek, dekIv);

            return new CipherResult(
                    b64(ciphertext),
                    b64(contentIv),
                    b64(encryptedDek),
                    b64(dekIv)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /**
     * 제목·본문을 <b>하나의 DEK</b>로 함께 암호화한다.
     * DiaryEntry 가 encryptedDek 를 1개만 보관하므로 두 필드는 동일 DEK로 암호화돼야 하며,
     * 그래야 같은 encryptedDek/dekIv 로 양쪽 모두 복호화할 수 있다.
     * (IV는 필드마다 독립이므로 동일 DEK 재사용에도 GCM 안전성이 유지된다.)
     */
    public EnvelopeResult encryptFields(String title, String content) {
        try {
            // 1. 단일 랜덤 DEK 생성 (제목·본문 공용)
            KeyGenerator kg = KeyGenerator.getInstance("AES");
            kg.init(256, random);
            SecretKey dek = kg.generateKey();

            // 2. 제목·본문을 각각 독립 IV로, 그러나 동일 DEK로 암호화
            byte[] titleIv = randomIv();
            byte[] titleCipher = aesGcmEncrypt(title.getBytes(StandardCharsets.UTF_8), dek, titleIv);
            byte[] contentIv = randomIv();
            byte[] contentCipher = aesGcmEncrypt(content.getBytes(StandardCharsets.UTF_8), dek, contentIv);

            // 3. DEK를 KEK로 1회 암호화
            byte[] dekIv = randomIv();
            byte[] encryptedDek = aesGcmEncrypt(dek.getEncoded(), kek, dekIv);

            return new EnvelopeResult(
                    b64(titleCipher), b64(titleIv),
                    b64(contentCipher), b64(contentIv),
                    b64(encryptedDek), b64(dekIv)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    /**
     * envelope 복호화. encryptedDek → DEK 복원 → plaintext 복원.
     */
    public String decrypt(String ciphertext, String iv,
                          String encryptedDek, String dekIv) {
        try {
            // 1. KEK로 DEK 복호화
            byte[] dekBytes = aesGcmDecrypt(
                    b64d(encryptedDek), kek, b64d(dekIv));
            SecretKey dek = new SecretKeySpec(dekBytes, "AES");

            // 2. DEK로 plaintext 복호화
            byte[] plain = aesGcmDecrypt(b64d(ciphertext), dek, b64d(iv));
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Decryption failed", e);
        }
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private byte[] aesGcmEncrypt(byte[] data, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
        return cipher.doFinal(data);
    }

    private byte[] aesGcmDecrypt(byte[] data, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(AES_GCM);
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LEN, iv));
        return cipher.doFinal(data);
    }

    private byte[] randomIv() {
        byte[] iv = new byte[GCM_IV_LEN];
        random.nextBytes(iv);
        return iv;
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static byte[] b64d(String s) {
        return Base64.getDecoder().decode(s);
    }
}
