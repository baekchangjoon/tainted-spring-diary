package com.tainted.diary.service;

import com.tainted.diary.crypto.EnvelopeCryptoService;
import com.tainted.diary.crypto.EnvelopeResult;
import com.tainted.diary.domain.DiaryEntry;
import com.tainted.diary.domain.DiaryEntryRepository;
import com.tainted.diary.error.DiaryNotFoundException;
import com.tainted.diary.event.DiaryCreatedEvent;
import com.tainted.diary.id.IdGenerator;
import com.tainted.diary.web.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DiaryService 순수 단위 테스트 (Mockito).
 * repository/crypto/idGenerator/eventPublisher 를 mock 으로 대체하고,
 * Clock 은 고정값을 주입해 결정론적으로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DiaryServiceTest {

    @Mock DiaryEntryRepository repository;
    @Mock EnvelopeCryptoService crypto;
    @Mock IdGenerator idGenerator;
    @Mock ApplicationEventPublisher eventPublisher;

    // 고정 시각: 2024-01-02T03:04:05Z
    private final Clock fixedClock =
            Clock.fixed(Instant.parse("2024-01-02T03:04:05Z"), ZoneOffset.UTC);

    @InjectMocks DiaryService service;

    @BeforeEach
    void initService() {
        // @InjectMocks 는 생성자에 Clock 도 주입해야 하므로 직접 구성한다.
        service = new DiaryService(repository, crypto, idGenerator, fixedClock, eventPublisher);
    }

    private EnvelopeResult sampleEnvelope() {
        return new EnvelopeResult("tc", "tiv", "cc", "civ", "edek", "div");
    }

    @Test
    void create_persistsEncryptedEntry_publishesEvent_andReturnsMeta() {
        when(idGenerator.newId()).thenReturn("diary-1", "event-1");
        when(crypto.encryptFields("제목", "본문")).thenReturn(sampleEnvelope());

        CreateDiaryRequest req =
                new CreateDiaryRequest("user-1", "제목", "본문", "JOY", 8);

        CreateDiaryResponse resp = service.create(req);

        // 응답 검증 (고정 Clock → timestamp 결정론적)
        assertThat(resp.diaryId()).isEqualTo("diary-1");
        assertThat(resp.userId()).isEqualTo("user-1");
        assertThat(resp.primaryEmotion()).isEqualTo("JOY");
        assertThat(resp.energyScore()).isEqualTo(8);
        assertThat(resp.timestamp()).isEqualTo("2024-01-02T03:04:05Z");

        // 저장된 엔티티가 평문이 아닌 암호문을 담는지 검증
        ArgumentCaptor<DiaryEntry> entryCaptor = ArgumentCaptor.forClass(DiaryEntry.class);
        verify(repository).save(entryCaptor.capture());
        DiaryEntry saved = entryCaptor.getValue();
        assertThat(saved.getId()).isEqualTo("diary-1");
        assertThat(saved.getUserId()).isEqualTo("user-1");
        assertThat(saved.getEncryptedTitle()).isEqualTo("tc");
        assertThat(saved.getEncryptedContent()).isEqualTo("cc");
        assertThat(saved.getEncryptedDek()).isEqualTo("edek");
        assertThat(saved.getTimestamp()).isEqualTo(Instant.parse("2024-01-02T03:04:05Z"));

        // 발행된 도메인 이벤트 검증 (eventId 는 두 번째 newId)
        ArgumentCaptor<DiaryCreatedEvent> evCaptor = ArgumentCaptor.forClass(DiaryCreatedEvent.class);
        verify(eventPublisher).publishEvent(evCaptor.capture());
        DiaryCreatedEvent ev = evCaptor.getValue();
        assertThat(ev.eventId()).isEqualTo("event-1");
        assertThat(ev.diaryId()).isEqualTo("diary-1");
        assertThat(ev.userId()).isEqualTo("user-1");
        assertThat(ev.primaryEmotion()).isEqualTo("JOY");
        assertThat(ev.energyScore()).isEqualTo(8);
        assertThat(ev.occurredAt()).isEqualTo("2024-01-02T03:04:05Z");
    }

    @Test
    void getMeta_returnsMetadata_withoutDecrypting() {
        DiaryEntry entry = sampleStoredEntry();
        when(repository.findById("diary-1")).thenReturn(Optional.of(entry));

        DiaryMetaResponse meta = service.getMeta("diary-1");

        assertThat(meta.diaryId()).isEqualTo("diary-1");
        assertThat(meta.userId()).isEqualTo("user-1");
        assertThat(meta.primaryEmotion()).isEqualTo("JOY");
        assertThat(meta.energyScore()).isEqualTo(8);
        // getMeta 는 복호화하지 않아야 한다
        verifyNoInteractions(crypto);
    }

    @Test
    void getMeta_throwsNotFound_whenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMeta("missing"))
                .isInstanceOf(DiaryNotFoundException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void getContent_decryptsTitleAndContent_withSharedDek() {
        DiaryEntry entry = sampleStoredEntry();
        when(repository.findById("diary-1")).thenReturn(Optional.of(entry));
        // 제목·본문 모두 동일 encryptedDek/dekIv 로 복호화돼야 한다
        when(crypto.decrypt("tc", "tiv", "edek", "div")).thenReturn("제목");
        when(crypto.decrypt("cc", "civ", "edek", "div")).thenReturn("본문");

        DiaryContentResponse content = service.getContent("diary-1");

        assertThat(content.diaryId()).isEqualTo("diary-1");
        assertThat(content.title()).isEqualTo("제목");
        assertThat(content.content()).isEqualTo("본문");
    }

    @Test
    void getContent_throwsNotFound_whenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getContent("missing"))
                .isInstanceOf(DiaryNotFoundException.class);
        verifyNoInteractions(crypto);
    }

    @Test
    void update_reEncryptsAndMutatesEntry_returningMeta() {
        DiaryEntry entry = sampleStoredEntry();
        when(repository.findById("diary-1")).thenReturn(Optional.of(entry));
        when(crypto.encryptFields("새 제목", "새 본문"))
                .thenReturn(new EnvelopeResult("tc2", "tiv2", "cc2", "civ2", "edek2", "div2"));

        UpdateDiaryRequest req = new UpdateDiaryRequest("새 제목", "새 본문", "CALM", 5);

        DiaryMetaResponse meta = service.update("diary-1", req);

        // 엔티티가 새 암호문/메타로 갱신됐는지 (DiaryEntry.update 경로 커버)
        assertThat(entry.getEncryptedTitle()).isEqualTo("tc2");
        assertThat(entry.getEncryptedContent()).isEqualTo("cc2");
        assertThat(entry.getEncryptedDek()).isEqualTo("edek2");
        assertThat(entry.getPrimaryEmotion()).isEqualTo("CALM");
        assertThat(entry.getEnergyScore()).isEqualTo(5);

        assertThat(meta.diaryId()).isEqualTo("diary-1");
        assertThat(meta.primaryEmotion()).isEqualTo("CALM");
        assertThat(meta.energyScore()).isEqualTo(5);
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());
        UpdateDiaryRequest req = new UpdateDiaryRequest("t", "c", "CALM", 5);

        assertThatThrownBy(() -> service.update("missing", req))
                .isInstanceOf(DiaryNotFoundException.class);
        verifyNoInteractions(crypto);
    }

    @Test
    void delete_removesExistingEntry() {
        DiaryEntry entry = sampleStoredEntry();
        when(repository.findById("diary-1")).thenReturn(Optional.of(entry));

        service.delete("diary-1");

        verify(repository).delete(entry);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(DiaryNotFoundException.class);
        verify(repository, never()).delete(any());
    }

    private DiaryEntry sampleStoredEntry() {
        return new DiaryEntry(
                "diary-1", "user-1",
                "tc", "tiv",
                "cc", "civ",
                "edek", "div",
                "JOY", 8,
                Instant.parse("2024-01-02T03:04:05Z"));
    }
}
