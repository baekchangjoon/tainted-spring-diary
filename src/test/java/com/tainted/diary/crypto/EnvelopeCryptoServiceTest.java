package com.tainted.diary.crypto;

import org.junit.jupiter.api.Test;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class EnvelopeCryptoServiceTest {

    // 테스트용 32바이트 KEK (Base64 인코딩)
    private static final String KEK_B64 =
            Base64.getEncoder().encodeToString("test-kek-32bytes-1234567890abcd!".getBytes());

    private final EnvelopeCryptoService crypto = new EnvelopeCryptoService(KEK_B64);

    @Test
    void roundTripAscii() {
        String plaintext = "Hello, diary!";
        CipherResult result = crypto.encrypt(plaintext);

        assertNotNull(result.ciphertext(), "ciphertext must not be null");
        assertNotNull(result.iv(), "iv must not be null");
        assertNotNull(result.encryptedDek(), "encryptedDek must not be null");
        assertNotNull(result.dekIv(), "dekIv must not be null");

        String decrypted = crypto.decrypt(
                result.ciphertext(), result.iv(),
                result.encryptedDek(), result.dekIv());
        assertEquals(plaintext, decrypted, "복호화 결과가 원문과 같아야 한다");
    }

    @Test
    void roundTripKorean() {
        String plaintext = "오늘 하루도 참 힘들었다. 그래도 괜찮아, 내일은 더 나을 거야. 🌱";
        CipherResult result = crypto.encrypt(plaintext);

        String decrypted = crypto.decrypt(
                result.ciphertext(), result.iv(),
                result.encryptedDek(), result.dekIv());
        assertEquals(plaintext, decrypted, "한국어 유니코드 텍스트 라운드트립 일치해야 한다");
    }

    @Test
    void eachEncryptProducesDifferentCiphertext() {
        String plaintext = "같은 입력";
        CipherResult r1 = crypto.encrypt(plaintext);
        CipherResult r2 = crypto.encrypt(plaintext);

        // 랜덤 DEK/IV 사용이므로 암호문은 매번 달라야 한다
        assertNotEquals(r1.ciphertext(), r2.ciphertext(),
                "랜덤 DEK/IV 덕분에 같은 입력도 암호문이 달라야 한다");

        // 양쪽 모두 복호화 성공해야 한다
        assertEquals(plaintext, crypto.decrypt(r1.ciphertext(), r1.iv(), r1.encryptedDek(), r1.dekIv()));
        assertEquals(plaintext, crypto.decrypt(r2.ciphertext(), r2.iv(), r2.encryptedDek(), r2.dekIv()));
    }

    @Test
    void encryptFieldsSharesOneDekForTitleAndContent() {
        String title = "오늘의 제목";
        String content = "오늘 하루의 본문 내용 🌱";
        EnvelopeResult env = crypto.encryptFields(title, content);

        // 제목·본문은 하나의 DEK로 암호화되므로, 동일한 encryptedDek/dekIv 로 양쪽 모두 복호화돼야 한다.
        assertEquals(title, crypto.decrypt(
                env.titleCipher(), env.titleIv(), env.encryptedDek(), env.dekIv()));
        assertEquals(content, crypto.decrypt(
                env.contentCipher(), env.contentIv(), env.encryptedDek(), env.dekIv()));
    }
}
