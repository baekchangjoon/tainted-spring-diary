package com.tainted.diary.web.dto;

public record CreateDiaryResponse(
        String diaryId,
        String userId,
        String primaryEmotion,
        int energyScore,
        String timestamp
) {}
