# diary-service

일기 CRUD, 서버측 KMS envelope 암호화(AES/GCM), 감정·활력 메타, 커밋 후 이벤트 발행을 담당하는 Spring Boot 마이크로서비스.

> 전체 시스템: [tainted-spring-msa](https://github.com/baekchangjoon/tainted-spring-msa)

---

## 역할

- 일기 항목(제목·본문·감정·활력 점수) CRUD 제공
- 서버측 시뮬레이션 KMS envelope 암호화 — 제목·본문을 단일 DEK(AES/GCM)로 암호화하고, DEK는 마스터 키(KEK)로 래핑하여 저장
- 조회 시 메타데이터(암호화 상태)와 복호화 본문을 별도 엔드포인트로 분리 제공
- `diary.created` 이벤트를 `@TransactionalEventListener(AFTER_COMMIT)` 패턴으로 Kafka에 발행 — 트랜잭션 커밋 이후에만 이벤트가 전송됨
- mindgraph 서비스가 호출하는 서버측 복호화 엔드포인트(`GET /internal/diaries/{id}/content`) 제공

---

## 기술 스택

| 항목 | 버전 |
|---|---|
| Java | 23 |
| Gradle | 8.12 |
| Spring Boot | 3.4.1 |
| 데이터베이스 | PostgreSQL (`diary`) |
| 메시지 브로커 | Apache Kafka |
| 포트 | 8082 |
| 테스트 | Testcontainers (PostgreSQL + Kafka) + RestAssured (통합 테스트 9건) |

---

## 빌드 & 테스트

> Gradle 8.12 이상 및 Docker가 필요합니다 (Testcontainers 실행에 사용).

```bash
# Java 23 설정 (macOS)
export JAVA_HOME=$(/usr/libexec/java_home -v 23)

# 빌드 및 전체 테스트 실행
./gradlew build
```

통합 테스트는 Testcontainers로 PostgreSQL과 Kafka 컨테이너를 자동 기동한 뒤 RestAssured로 HTTP 요청을 검증합니다.

---

## 주요 API

모든 엔드포인트는 내부 전용(`/internal`)이며, 서비스 직접 호출(`:8082`) 또는 BFF를 경유하여 접근합니다.

### 엔드포인트 목록

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/internal/diaries` | 일기 생성 (201) |
| `GET` | `/internal/diaries/{id}` | 메타데이터 조회 (복호화 없음) |
| `GET` | `/internal/diaries/{id}/content` | 서버측 복호화 본문 조회 |
| `PUT` | `/internal/diaries/{id}` | 일기 수정 |
| `DELETE` | `/internal/diaries/{id}` | 일기 삭제 (204) |

### curl 예시

```bash
# 일기 생성
curl -s -X POST http://localhost:8082/internal/diaries \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "user-001",
    "title": "오늘의 일기",
    "content": "오늘은 좋은 하루였다.",
    "primaryEmotion": "JOY",
    "energyScore": 8
  }' | jq .

# 메타데이터 조회
curl -s http://localhost:8082/internal/diaries/{id} | jq .

# 복호화 본문 조회 (mindgraph 콜백용)
curl -s http://localhost:8082/internal/diaries/{id}/content | jq .

# 일기 수정
curl -s -X PUT http://localhost:8082/internal/diaries/{id} \
  -H "Content-Type: application/json" \
  -d '{
    "title": "수정된 제목",
    "content": "수정된 본문",
    "primaryEmotion": "CALM",
    "energyScore": 6
  }' | jq .

# 일기 삭제
curl -s -X DELETE http://localhost:8082/internal/diaries/{id}
```

생성 요청 본문 필드:

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | String | 작성자 ID |
| `title` | String | 일기 제목 (암호화 저장) |
| `content` | String | 일기 본문 (암호화 저장) |
| `primaryEmotion` | String | 주요 감정 (예: `JOY`, `CALM`, `SAD`) |
| `energyScore` | int | 활력 점수 (1 ~ 10) |

---

## 이벤트

### 발행: `diary.created`

일기가 성공적으로 저장되고 트랜잭션이 커밋된 직후 Kafka 토픽 `diary.created`로 발행됩니다.

```json
{
  "eventId": "uuid",
  "userId": "user-001",
  "diaryId": "uuid",
  "primaryEmotion": "JOY",
  "energyScore": 8,
  "occurredAt": "2026-06-13T12:00:00Z"
}
```

`@TransactionalEventListener(phase = AFTER_COMMIT)` 패턴을 사용하므로 트랜잭션 롤백 시 이벤트는 발행되지 않습니다.

---

## Docker

```bash
# 이미지 빌드
docker build -t tainted-spring/diary:0.1.0 .

# 컨테이너 실행 (로컬 DB/Kafka 연결 예시)
docker run -p 8082:8082 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/diary \
  -e SPRING_KAFKA_BOOTSTRAP_SERVERS=host.docker.internal:9092 \
  tainted-spring/diary:0.1.0
```

멀티스테이지 Dockerfile이 포함되어 있어 빌드 산출물만 런타임 이미지에 복사됩니다.

---

## 라이선스

[MIT](LICENSE) © 2026 baekchangjoon
