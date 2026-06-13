package com.tainted.diary;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class DiaryIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("diary")
                    .withUsername("postgres")
                    .withPassword("postgrespw");

    @Container
    static KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @LocalServerPort
    int port;

    private KafkaConsumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;

        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(List.of("diary.created"));
    }

    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
    }

    @Test
    void createDiary_returns201WithDiaryId() {
        given()
            .contentType(ContentType.JSON)
            .body("""
                    {
                      "userId": "user-abc",
                      "title": "오늘의 일기",
                      "content": "오늘은 참 좋은 날이었다.",
                      "primaryEmotion": "JOY",
                      "energyScore": 8
                    }
                    """)
        .when()
            .post("/internal/diaries")
        .then()
            .statusCode(201)
            .body("diaryId", not(emptyOrNullString()))
            .body("userId", equalTo("user-abc"))
            .body("primaryEmotion", equalTo("JOY"))
            .body("energyScore", equalTo(8));
    }

    @Test
    void createThenGetContent_roundTripKorean() {
        // (a) 일기 생성
        String diaryId = given()
            .contentType(ContentType.JSON)
            .body("""
                    {
                      "userId": "user-kor",
                      "title": "한국어 제목 — 오늘도 수고했어",
                      "content": "마음이 복잡하지만 그래도 내일은 더 나을 거야. 🌱",
                      "primaryEmotion": "MELANCHOLY",
                      "energyScore": 4
                    }
                    """)
        .when()
            .post("/internal/diaries")
        .then()
            .statusCode(201)
            .extract().path("diaryId");

        // (b) 복호화 본문 조회 — DB 저장 후 AES/GCM 라운드트립 검증
        given()
            .when()
            .get("/internal/diaries/" + diaryId + "/content")
        .then()
            .statusCode(200)
            .body("diaryId", equalTo(diaryId))
            .body("title", equalTo("한국어 제목 — 오늘도 수고했어"))
            .body("content", equalTo("마음이 복잡하지만 그래도 내일은 더 나을 거야. 🌱"));
    }

    @Test
    void createDiary_publishesDiaryCreatedKafkaEvent() throws InterruptedException {
        // 일기 생성
        Map<String, Object> created = given()
            .contentType(ContentType.JSON)
            .body("""
                    {
                      "userId": "user-kafka",
                      "title": "Kafka 이벤트 테스트",
                      "content": "이 일기는 Kafka 이벤트 발행을 확인하기 위해 작성되었다.",
                      "primaryEmotion": "CURIOUS",
                      "energyScore": 7
                    }
                    """)
        .when()
            .post("/internal/diaries")
        .then()
            .statusCode(201)
            .extract().as(java.util.HashMap.class);

        String expectedDiaryId = (String) created.get("diaryId");
        String expectedUserId = (String) created.get("userId");

        // diary.created 토픽에서 메시지 수신 대기 (최대 10초)
        List<ConsumerRecord<String, String>> records = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 10_000;
        while (records.isEmpty() && System.currentTimeMillis() < deadline) {
            consumer.poll(Duration.ofMillis(300)).forEach(records::add);
        }

        assertFalse(records.isEmpty(), "diary.created 토픽에 메시지가 없다");

        // 발행된 JSON 에 diaryId·userId·primaryEmotion·energyScore 포함 여부 검증
        String json = records.get(0).value();
        assertAll(
            () -> assertTrue(json.contains("\"diaryId\":\"" + expectedDiaryId + "\""),
                    "diaryId 포함 여부: " + json),
            () -> assertTrue(json.contains("\"userId\":\"" + expectedUserId + "\""),
                    "userId 포함 여부: " + json),
            () -> assertTrue(json.contains("\"primaryEmotion\":\"CURIOUS\""),
                    "primaryEmotion 포함 여부: " + json),
            () -> assertTrue(json.contains("\"energyScore\":7"),
                    "energyScore 포함 여부: " + json)
        );
    }

    @Test
    void getMeta_doesNotExposeDecryptedContent() {
        String diaryId = given()
            .contentType(ContentType.JSON)
            .body("""
                    {
                      "userId": "user-meta",
                      "title": "비밀 제목",
                      "content": "비밀 본문",
                      "primaryEmotion": "FEAR",
                      "energyScore": 3
                    }
                    """)
        .when()
            .post("/internal/diaries")
        .then()
            .statusCode(201)
            .extract().path("diaryId");

        // 메타 응답에 title·content 필드가 없어야 한다
        given()
            .when()
            .get("/internal/diaries/" + diaryId)
        .then()
            .statusCode(200)
            .body("diaryId", equalTo(diaryId))
            .body("primaryEmotion", equalTo("FEAR"))
            .body("title", nullValue())
            .body("content", nullValue());
    }

    @Test
    void unknownDiaryReturns404ProblemJson() {
        given()
            .when()
            .get("/internal/diaries/no-such-id")
        .then()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("title", equalTo("Diary not found"));
    }
}
