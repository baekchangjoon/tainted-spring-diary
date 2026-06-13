package com.tainted.diary.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * DB 트랜잭션이 커밋된 이후에만 Kafka로 diary.created 를 발행한다.
 *
 * 이유: mindgraph 가 diary.created 를 소비한 직후 /internal/diaries/{id}/content 를
 * 호출하는데, 트랜잭션 커밋 전에 Kafka 메시지가 나가면 해당 일기를 조회할 수 없는
 * race condition 이 발생한다. AFTER_COMMIT phase 는 이 race 를 구조적으로 방지한다.
 *
 * 참고: AFTER_COMMIT 리스너는 활성 트랜잭션 밖에서 실행된다. KafkaTemplate 은
 * 트랜잭션 없이 독립적으로 send 를 수행한다.
 */
@Component
public class DiaryEventListener {

    private static final String TOPIC = "diary.created";

    private final KafkaTemplate<String, DiaryCreatedKafkaPayload> kafkaTemplate;

    public DiaryEventListener(KafkaTemplate<String, DiaryCreatedKafkaPayload> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDiaryCreated(DiaryCreatedEvent event) {
        DiaryCreatedKafkaPayload payload = new DiaryCreatedKafkaPayload(
                event.eventId(),
                event.userId(),
                event.diaryId(),
                event.primaryEmotion(),
                event.energyScore(),
                event.occurredAt()
        );
        // key = diaryId → 같은 일기의 이벤트가 같은 파티션으로 순서 보장
        kafkaTemplate.send(TOPIC, event.diaryId(), payload);
    }
}
