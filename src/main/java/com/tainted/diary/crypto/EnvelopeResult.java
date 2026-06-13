package com.tainted.diary.crypto;

/**
 * 제목·본문을 하나의 DEK로 함께 암호화한 결과.
 * DiaryEntry 는 encryptedDek 를 1개만 보관하므로, 두 필드가 같은 DEK로 암호화되어야
 * 동일 encryptedDek 로 양쪽을 복호화할 수 있다. 모든 필드는 Base64 문자열.
 */
public record EnvelopeResult(
        String titleCipher,
        String titleIv,
        String contentCipher,
        String contentIv,
        String encryptedDek,
        String dekIv
) {}
