package com.tainted.diary.web.dto;

/**
 * 메타데이터 응답 — 복호화된 제목/본문은 포함하지 않는다.
 */
public record DiaryMetaResponse(
        String diaryId,
        String userId,
        String primaryEmotion,
        int energyScore,
        String timestamp
) {}
