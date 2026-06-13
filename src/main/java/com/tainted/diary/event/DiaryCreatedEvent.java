package com.tainted.diary.event;

/**
 * diary-service 내부에서만 사용하는 Spring ApplicationEvent 페이로드.
 * @TransactionalEventListener(AFTER_COMMIT) 이 이 이벤트를 수신해 Kafka로 전달한다.
 */
public record DiaryCreatedEvent(
        String eventId,
        String userId,
        String diaryId,
        String primaryEmotion,
        int energyScore,
        String occurredAt
) {}
