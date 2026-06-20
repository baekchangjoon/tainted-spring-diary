# KNOWN-LIMITATIONS (diary · graph-rag Method 1 블랙박스)

graph-rag(도구 1 = graph-rag-builder, 도구 2 = test-generator)로 생성한 out-of-process
블랙박스 RestAssured 테스트 중, **순전히 생성기/탐색기의 알려진 한계 때문에** 실제 구동
대상(docker compose)에서 실패하는 케이스를 격리(quarantine)한 기록이다. **테스트를 약화시키지
않았다** — 격리 + 사유 문서화가 원칙이다. 격리 소스는 `quarantine/`에 원본 단언 그대로 보존한다
(pristine 전체 클래스 사본 + 격리 패키지의 실행 가능 클래스).

## 요약

| 항목 | 값 |
|---|---|
| 탐색된 endpoint 수 | 5 (`POST /internal/diaries`, `GET /internal/diaries/{id}`, `GET /internal/diaries/{id}/content`, `PUT /internal/diaries/{id}`, `DELETE /internal/diaries/{id}`) |
| 생성된 시나리오(테스트) 수 | 11 |
| GREEN(통과) | 8 |
| 격리(quarantined) | 3 |
| FAIL(미해결) | 0 |
| line coverage (Tool 1 탐색) | 131/208 (62%) |

## 격리된 테스트 (3건)

### 1. `DiaryPostTest.s201_1` — Kafka JSONAssert 비결정성 (eventId / occurredAt)

- **endpoint:** `POST /internal/diaries`
- **요청:** 정상 본문 → `201 Created` + `diary.created` Kafka 이벤트 발행
- **captured(탐색 시점) 기대:** `eventId=565a865c-…`, `occurredAt=2026-06-20T05:59:26.398574Z`
- **replayed(compose 재생) 실제:** 매 요청마다 새 `eventId`(UUID) + 새 `occurredAt`(타임스탬프)
- **격리 소스:** `quarantine/io/graphrag/generated/diary/quarantine/DiaryPostKafkaNondetQuarantineTest.java`

**근본 원인.** 생성기가 `diary.created` 이벤트의 본문을 `JSONAssert.assertEquals(...)` 로
검증하는데, 캡처 시점의 `eventId`(랜덤 UUID)와 `occurredAt`(`Instant.now()` 류 타임스탬프)을
**하드코딩**한다. SUT 는 요청마다 이 두 필드를 새로 생성하므로, 재생 시 캡처 값과 결코
일치하지 않는다(REST 응답·`userId`·`primaryEmotion`·`energyScore` 는 결정적이라 통과).
서비스 버그가 아니라 생성기가 비결정 필드를 ignore 처리하지 못하는 한계다. 단언을
`notNullValue()` 로 바꾸면 테스트를 약화시키는 것이므로 격리한다.

> 같은 endpoint 의 나머지 시나리오 `s400_1`(energyScore 검증 위반 → 400)은 결정적이므로
> GREEN 으로 유지한다.

### 2. `DiaryGetContentTest.s500_1` — flaky 캡처 상태(500) vs 결정적 404

- **endpoint:** `GET /internal/diaries/{id}/content`
- **요청:** `GET /internal/diaries/probe-id-98829/content` (탐색기 probe id)
- **captured(탐색 시점) 기대:** `500 Internal Server Error`
- **replayed(compose 재생) 실제:** `404 Not Found`
- **격리 소스:** `quarantine/io/graphrag/generated/diary/quarantine/DiaryGetContentQuarantineTest.java`

**근본 원인.** 탐색기가 probe id 로 content 를 조회하던 순간 일시적 500 이 캡처됐다(탐색용
throwaway Testcontainers DB 의 과도기 상태). 신선한 compose DB 에서 존재하지 않는 id 의
content 조회는 **결정적으로 404** 를 반환한다(실측 `curl` → 404). flaky 하게 잡힌 상태이며
서비스 버그가 아니다. 404 로 단언을 바꾸면 약화이므로 격리한다.

> 같은 endpoint 의 `s404_1`(존재하지 않는 id → 404)은 결정적이므로 GREEN 으로 유지한다.

### 3. `DiaryGetContentTest.s404_2` — double-slash 빈 path-var 의 인코딩 불일치

- **endpoint:** `GET /internal/diaries/{id}/content`
- **요청:** `GET /internal/diaries//content` (빈 path-var → 이중 슬래시)
- **captured(탐색 시점) 기대:** `404 Not Found`
- **replayed(compose 재생) 실제:** `400 Bad Request`
- **격리 소스:** `quarantine/io/graphrag/generated/diary/quarantine/DiaryGetContentQuarantineTest.java`

**근본 원인.** 탐색기는 빈 path-var 를 그대로 `//` 로 보내 404 를 캡처했지만, RestAssured 는
재생 시 빈 세그먼트를 정규화/인코딩하는 방식이 달라 SUT 가 **400** 을 돌려준다. 캡처 경로와
재생 경로의 path-encoding 차이에서 오는 불일치이며 서비스 버그가 아니다. 격리한다.

## 캡처 상태(정직한 기록)

- **SQL capture:** OK. 10 SQL, 1 table(`diary_entry`, 11 컬럼, PK `id`). seed INSERT /
  `deferDelete` 정상 동작(예: `DiaryDeleteTest.s204_1`, `DiaryGetMetaTest`/`DiaryPutTest`
  의 seed 경로).
- **Kafka capture:** OK. `diary.created` 토픽이 매칭/캡처됐고 이벤트 본문까지 잡혔다 —
  그 결과가 위 격리 #1 의 JSONAssert 다. 즉 Kafka 캡처 자체는 성공했으나 비결정 필드의
  단언 처리가 한계였다.
- **outbound HTTP:** 없음(`http: 0`). diary 는 leaf 서비스로 자기 postgres + Kafka producer
  만 사용한다. 따라서 `--external-stubs`(WireMock) 불필요, 사용 안 함.

## 커버리지 한계(정직한 기록 — 격리 아님)

- **line 131/208 (62%), branch 0/4 (0%).** REST 탐색 + SQL seed 로 도달 가능한 경로만
  결정적으로 커버한다. 암복호화(KEK/DEK) 경로의 일부 분기와, 발행한 Kafka 이벤트를
  다운스트림(mindgraph/analytics)이 소비한 뒤의 상태는 REST 탐색만으로 결정적으로 시드할 수
  없어 미커버로 남는다. 가짜로 채우지 않았다.
