package com.tainted.diary.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * EnvelopeCryptoService 의 에러/경계 분기 커버용 테스트.
 * 정상 라운드트립은 EnvelopeCryptoServiceTest 가 담당하고, 여기서는 실패 경로를 검증한다.
 */
class EnvelopeCryptoServiceEdgeTest {

    private static final String KEK_B64 =
            Base64.getEncoder().encodeToString("test-kek-32bytes-1234567890abcd!".getBytes());

    private final EnvelopeCryptoService crypto = new EnvelopeCryptoService(KEK_B64);

    @Test
    void constructor_rejectsKekThatIsNot32Bytes() {
        // 16바이트 KEK → IllegalArgumentException
        String shortKek = Base64.getEncoder().encodeToString("0123456789abcdef".getBytes());

        assertThatThrownBy(() -> new EnvelopeCryptoService(shortKek))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    void decrypt_failsLoudly_onTamperedCiphertext() {
        CipherResult enc = crypto.encrypt("민감한 본문");

        // 암호문을 다른 유효 Base64 로 바꿔 GCM 인증 태그 검증 실패를 유도
        String tampered = Base64.getEncoder().encodeToString(
                "this-is-not-the-real-ciphertext-bytes".getBytes());

        assertThatThrownBy(() -> crypto.decrypt(
                tampered, enc.iv(), enc.encryptedDek(), enc.dekIv()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Decryption failed");
    }

    @Test
    void decrypt_failsLoudly_onWrongDek() {
        CipherResult a = crypto.encrypt("문장 A");
        CipherResult b = crypto.encrypt("문장 B");

        // a 의 암호문을 b 의 DEK 로 풀려고 하면 실패해야 한다
        assertThatThrownBy(() -> crypto.decrypt(
                a.ciphertext(), a.iv(), b.encryptedDek(), b.dekIv()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void encrypt_singleFieldRoundTrips() {
        // encrypt() (단일 필드) 경로도 명시적으로 커버
        CipherResult r = crypto.encrypt("");
        assertThat(crypto.decrypt(r.ciphertext(), r.iv(), r.encryptedDek(), r.dekIv()))
                .isEmpty();
    }
}
