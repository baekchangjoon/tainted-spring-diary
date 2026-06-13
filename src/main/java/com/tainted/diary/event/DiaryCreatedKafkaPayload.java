package com.tainted.diary.event;

/**
 * diary.created 토픽에 발행되는 JSON 페이로드.
 * 스키마 카탈로그: {eventId, userId, diaryId, primaryEmotion, energyScore, occurredAt}
 */
public record DiaryCreatedKafkaPayload(
        String eventId,
        String userId,
        String diaryId,
        String primaryEmotion,
        int energyScore,
        String occurredAt
) {}
