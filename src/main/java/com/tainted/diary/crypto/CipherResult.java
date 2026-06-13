package com.tainted.diary.crypto;

/**
 * 암호화 결과. ciphertext·iv는 텍스트 암호화 산출물,
 * encryptedDek·dekIv는 DEK 자체의 KEK 암호화 산출물.
 * 모든 필드는 Base64 문자열.
 */
public record CipherResult(
        String ciphertext,
        String iv,
        String encryptedDek,
        String dekIv
) {}
