package com.tainted.diary.service;

import com.tainted.diary.crypto.EnvelopeResult;
import com.tainted.diary.crypto.EnvelopeCryptoService;
import com.tainted.diary.domain.DiaryEntry;
import com.tainted.diary.domain.DiaryEntryRepository;
import com.tainted.diary.error.DiaryNotFoundException;
import com.tainted.diary.event.DiaryCreatedEvent;
import com.tainted.diary.id.IdGenerator;
import com.tainted.diary.web.dto.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class DiaryService {

    private final DiaryEntryRepository repository;
    private final EnvelopeCryptoService crypto;
    private final IdGenerator idGenerator;
    private final Clock clock;
    private final ApplicationEventPublisher eventPublisher;

    public DiaryService(DiaryEntryRepository repository,
                        EnvelopeCryptoService crypto,
                        IdGenerator idGenerator,
                        Clock clock,
                        ApplicationEventPublisher eventPublisher) {
        this.repository = repository;
        this.crypto = crypto;
        this.idGenerator = idGenerator;
        this.clock = clock;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 일기를 저장하고 AFTER_COMMIT 이벤트를 예약한다.
     * 트랜잭션 커밋 후 {@link com.tainted.diary.event.DiaryEventListener} 가
     * diary.created 를 Kafka 로 발행한다.
     */
    @Transactional
    public CreateDiaryResponse create(CreateDiaryRequest request) {
        String diaryId = idGenerator.newId();
        Instant now = Instant.now(clock);

        // 제목·본문을 하나의 DEK로 암호화 → 단일 encryptedDek 로 양쪽 복호화 가능
        EnvelopeResult env = crypto.encryptFields(request.title(), request.content());

        DiaryEntry entry = new DiaryEntry(
                diaryId,
                request.userId(),
                env.titleCipher(), env.titleIv(),
                env.contentCipher(), env.contentIv(),
                env.encryptedDek(), env.dekIv(),
                request.primaryEmotion(),
                request.energyScore(),
                now
        );
        repository.save(entry);

        // 트랜잭션 AFTER_COMMIT 시 Kafka 발행 예약
        String eventId = idGenerator.newId();
        eventPublisher.publishEvent(new DiaryCreatedEvent(
                eventId,
                request.userId(),
                diaryId,
                request.primaryEmotion(),
                request.energyScore(),
                now.toString()
        ));

        return new CreateDiaryResponse(
                diaryId,
                request.userId(),
                request.primaryEmotion(),
                request.energyScore(),
                now.toString()
        );
    }

    @Transactional(readOnly = true)
    public DiaryMetaResponse getMeta(String diaryId) {
        DiaryEntry entry = findOrThrow(diaryId);
        return toMeta(entry);
    }

    /**
     * 서버측 복호화 — mindgraph 가 그래프 생성을 위해 호출한다.
     */
    @Transactional(readOnly = true)
    public DiaryContentResponse getContent(String diaryId) {
        DiaryEntry entry = findOrThrow(diaryId);

        // 제목·본문은 하나의 DEK로 암호화되어 있으므로, 동일 encryptedDek/dekIv 로 각각 복호화
        String title = crypto.decrypt(
                entry.getEncryptedTitle(), entry.getTitleIv(),
                entry.getEncryptedDek(), entry.getDekIv());
        String content = crypto.decrypt(
                entry.getEncryptedContent(), entry.getContentIv(),
                entry.getEncryptedDek(), entry.getDekIv());

        return new DiaryContentResponse(diaryId, title, content);
    }

    @Transactional
    public DiaryMetaResponse update(String diaryId, UpdateDiaryRequest request) {
        DiaryEntry entry = findOrThrow(diaryId);

        EnvelopeResult env = crypto.encryptFields(request.title(), request.content());

        entry.update(
                env.titleCipher(), env.titleIv(),
                env.contentCipher(), env.contentIv(),
                env.encryptedDek(), env.dekIv(),
                request.primaryEmotion(),
                request.energyScore()
        );

        return toMeta(entry);
    }

    @Transactional
    public void delete(String diaryId) {
        DiaryEntry entry = findOrThrow(diaryId);
        repository.delete(entry);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────────────

    private DiaryEntry findOrThrow(String diaryId) {
        return repository.findById(diaryId)
                .orElseThrow(() -> new DiaryNotFoundException("diary not found: " + diaryId));
    }

    private DiaryMetaResponse toMeta(DiaryEntry e) {
        return new DiaryMetaResponse(
                e.getId(),
                e.getUserId(),
                e.getPrimaryEmotion(),
                e.getEnergyScore(),
                e.getTimestamp().toString()
        );
    }
}
