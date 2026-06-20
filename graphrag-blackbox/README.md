# diary — graph-rag Method 1 블랙박스 테스트 (out-of-process RestAssured)

`graph-rag-test-generator` (Method 1) 으로 `diary` 마이크로서비스에 대해 생성한
**out-of-process 블랙박스 RestAssured 테스트**의 자기완결적 아티팩트다. 실행 중인
diary 컨테이너(REST + PostgreSQL + Kafka producer)를 대상으로 graph-rag 의 `:e2e:test`
하니스에서 돌린다. SUT 코드는 전혀 수정하지 않는다.

- **생성 도구:** graph-rag-builder(Tool 1, 분석/그래프 빌드) + test-generator(Tool 2, 테스트 생성)
- **대상:** diary-service 0.1.0 (Spring Boot, PostgreSQL, Kafka producer, KEK/DEK 암호화)
- **결과:** 5 endpoint → 5 test class → **11 test, 8 green, 3 quarantine, 0 fail**
- **line coverage (Tool 1 탐색):** **131/208 (62%)**

## 디렉터리 구성

| 경로 | 내용 |
|------|------|
| `generated-tests/io/graphrag/generated/diary/*.java` | GREEN 으로 유지하는 5개 테스트 클래스 (총 8 test) |
| `generated-tests/generation-result.json` | Tool 2 의 마지막 생성 결과(파일 목록 + warning + 병렬 안전성) |
| `graph/graph.json` | Tool 1 이 빌드한 SUT 지식 그래프 (5 endpoint, 19 path, 10 sql, 0 http, 1 table) |
| `graph/exploration-report.json` | endpoint별 탐색/분기 커버리지 리포트 |
| `requests/req-*.json` | Tool 2 호출용 endpoint별 생성 요청 스펙 (5개, `packageName: io.graphrag.generated.diary`) |
| `junit-platform.properties` | 병렬 실행 설정(Tool 2 산출) |
| `quarantine/` | **GREEN 런에서 제외된** 3개 비결정 케이스. pristine 전체 클래스 사본 + 격리 패키지의 실행 가능 클래스 |
| `KNOWN-LIMITATIONS.md` | 격리 3건의 endpoint·captured-vs-replayed·근본 원인 문서 |

## endpoint → test class 매핑

| endpointId | METHOD PATH | test class | green | quarantine |
|-----------|-------------|-----------|-------|-----------|
| `post-internal-diaries` | POST `/internal/diaries` | `DiaryPostTest` | 1 (`s400_1`) | 1 (`s201_1`) |
| `get-internal-diaries-id` | GET `/internal/diaries/{id}` | `DiaryGetMetaTest` | 3 | 0 |
| `get-internal-diaries-id-content` | GET `/internal/diaries/{id}/content` | `DiaryGetContentTest` | 1 (`s404_1`) | 2 (`s500_1`,`s404_2`) |
| `put-internal-diaries-id` | PUT `/internal/diaries/{id}` | `DiaryPutTest` | 2 | 0 |
| `delete-internal-diaries-id` | DELETE `/internal/diaries/{id}` | `DiaryDeleteTest` | 1 | 0 |
| | | **합계** | **8** | **3** |

각 테스트는 `TestScope`(graph-rag testlib) 로 REST 호출 + JDBC seed/cleanup 을 수행한다.
seed 한 행은 `deferDelete` 로 테스트 종료 시 정리된다.

## 재현 (reproduce)

전제: JDK 23(jdk-23.0.2+7), Docker, graph-rag 레포가 아래 경로에 클론·빌드되어 있어야 한다.

```bash
export JAVA_HOME=/Users/changjoonbaek/Library/Java/JavaVirtualMachines/jdk-23.0.2+7/Contents/Home
GRAPHRAG=/Users/changjoonbaek/github_graph-rag-test-generator/graph-rag
PLATFORM=/Users/changjoonbaek/github_tainted-spring/tainted-spring-platform
DIARY=/Users/changjoonbaek/github_tainted-spring/tainted-spring-diary
W=$GRAPHRAG/.work/diary-final
ART=$DIARY-blackbox/graphrag-blackbox   # 이 아티팩트 디렉터리

# 0) diary 운영 jar 빌드 (메인 레포에서, 테스트 워크트리 아님)
( cd $DIARY && ./gradlew bootJar )   # -> build/libs/diary-service-0.1.0.jar

# 1) Tool 1 — 그래프 빌드 (diary 는 PostgreSQL + Kafka producer; KEK env 필수)
( cd $GRAPHRAG && ./gradlew :graph-rag-builder:run --args="build \
  --sut-src       $DIARY/src/main/java \
  --sut-resources $DIARY/src/main/resources \
  --sut-jar       $DIARY/build/libs/diary-service-0.1.0.jar \
  --sut-compose   $PLATFORM/docker-compose.yml \
  --sut-id diary --with-kafka --db-service postgres \
  --sut-java-home $JAVA_HOME \
  --sut-env DIARY_KEK_BASE64=YWJjZGVmZ2hpamtsbW5vcHFyc3R1dnd4eXoxMjM0NTY= \
  --out $W/graph-out" )
#   --db-service postgres 가 핵심: platform compose 는 멀티-DB. 생략 시 엉뚱한 DB 선택.
#   DIARY_KEK_BASE64 없으면 diary 부팅 실패(KEK 미설정).

# 2) Tool 2 — endpoint별 테스트 생성 (요청 스펙은 requests/ 에 동봉)
( cd $GRAPHRAG && for r in \
      req-post-diaries req-get-diaries-id req-get-diaries-id-content req-put-diaries-id req-delete-diaries-id; do
    ./gradlew -q :test-generator:run \
      --args="generate --request $ART/requests/$r.json --graph $W/graph-out --out $W/generated"
  done )

# 3) diary 컨테이너 기동
( cd $PLATFORM && docker compose -p grdiary -f docker-compose.yml up -d --build diary )
#    diary:8082, postgres:5432 (DB diary, user postgres, pw postgrespw), kafka:9092.
#    health 까지 대기: curl -s -o /dev/null -w '%{http_code}' http://localhost:8082/internal/diaries/missing/content  # -> 404

# 4) GREEN 테스트만 e2e 하니스로 복사 후 실행 (quarantine/ 은 복사하지 않는다)
cp -R $ART/generated-tests/io $GRAPHRAG/e2e/build/generated-tests/
cp $ART/junit-platform.properties $GRAPHRAG/e2e/src/test/resources/
( cd $GRAPHRAG && APP_BASE_URI=http://localhost:8082 \
  JDBC_URL=jdbc:postgresql://localhost:5432/diary JDBC_USER=postgres JDBC_PASS=postgrespw \
  KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
  ./gradlew :e2e:test --rerun )
#   기대: BUILD SUCCESSFUL, 8 test / 0 fail / 0 skip
```

## 정리 (cleanup)

```bash
# 컨테이너 + 볼륨 제거
( cd $PLATFORM && docker compose -p grdiary down -v )
# e2e 하니스에 복사한 생성 테스트 + junit props 제거 (레포 오염 방지)
rm -rf $GRAPHRAG/e2e/build/generated-tests/io
git -C $GRAPHRAG checkout -- e2e/src/test/resources/junit-platform.properties 2>/dev/null \
  || rm -f $GRAPHRAG/e2e/src/test/resources/junit-platform.properties
```

## 격리 (quarantine) — 3건, 모두 생성기/탐색기 한계

| 격리 테스트 | endpoint | captured → replayed | 근본 원인 |
|------------|----------|---------------------|-----------|
| `DiaryPostTest.s201_1` | POST `/internal/diaries` | eventId/occurredAt 고정 → 매 요청 재생성 | Kafka JSONAssert 가 비결정 UUID/timestamp 하드코딩 |
| `DiaryGetContentTest.s500_1` | GET `/internal/diaries/{id}/content` | 500 → 404 | flaky 캡처 상태; 신선 DB 는 결정적 404 |
| `DiaryGetContentTest.s404_2` | GET `/internal/diaries//content` | 404 → 400 | double-slash 빈 path-var 의 인코딩 불일치 |

자세한 내용·실행 가능 격리 소스는 `KNOWN-LIMITATIONS.md` 와 `quarantine/` 참고. **테스트를
약화시키지 않았다** — 격리 + 문서화가 원칙이다.

## 결과 요약

- **5 endpoint / 5 class / 11 test → 8 green, 3 quarantine, 0 fail, 0 skip.**
- **SQL capture:** OK. 10 SQL, 1 table(`diary_entry`). seed INSERT/deferDelete 정상.
- **Kafka capture:** OK. `diary.created` 캡처됨(격리 #1 의 JSONAssert 근거). 비결정 필드
  단언이 한계라 해당 1건만 격리.
- **outbound HTTP:** 없음(`http: 0`). diary 는 leaf 서비스 → WireMock 스텁 불필요.

## 환경 메모 (recipe 대비)

- diary 호스트 포트 **8082**, PostgreSQL **5432**(DB `diary`, user `postgres`, pw `postgrespw`).
- jar 이름: `diary-service-0.1.0.jar` (gradle bootJar). **빌드는 JDK 23** 필요.
- diary 는 KEK 기반 암복호화 서비스: 부팅에 `DIARY_KEK_BASE64` env 필수
  (recipe 의 `--sut-env` 로 Tool 1 탐색 시에도 주입; compose 는 `application-docker` 기본값 사용).
- `authMode: DISABLED` (internal endpoint, 인증 없음).
- 생성 패키지: `io.graphrag.generated.diary`.
