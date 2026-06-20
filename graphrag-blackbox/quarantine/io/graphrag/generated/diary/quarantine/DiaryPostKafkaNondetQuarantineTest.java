package io.graphrag.generated.diary.quarantine;

import io.graphrag.testlib.api.TestScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * QUARANTINED — DO NOT add to the green run. See KNOWN-LIMITATIONS.md #1.
 *
 * Original: io.graphrag.generated.diary.DiaryPostTest#s201_1 (POST /internal/diaries).
 * Root cause: the generated Kafka JSONAssert hardcodes a capture-time eventId (UUID) and
 * occurredAt (timestamp) which the SUT regenerates per request, so the replayed record
 * never matches. This is a generator determinism limitation, NOT a service bug. The
 * assertion body is preserved verbatim below (the values are the capture-time ones).
 */
class DiaryPostKafkaNondetQuarantineTest {

    private TestScope scope;

    @BeforeEach
    void setUp() {
        scope = TestScope.create();
    }

    @AfterEach
    void cleanup() {
        scope.cleanup();
    }

    @Test
    void s201_1() throws Exception {
        scope.kafka().subscribe("diary.created");
        scope.rest().given()
            .contentType("application/json")
            .body("{\"userId\":\"sample-userId\",\"title\":\"sample-title\",\"content\":\"sample-content\",\"primaryEmotion\":\"sample-primaryEmotion\",\"energyScore\":1}")
        .when()
            .post("/internal/diaries")
        .then()
            .statusCode(201)
            .body("diaryId", notNullValue())
            .body("userId", equalTo("sample-userId"))
            .body("primaryEmotion", equalTo("sample-primaryEmotion"))
            .body("energyScore", equalTo(1))
            .body("timestamp", notNullValue());
        {
            org.apache.kafka.clients.consumer.ConsumerRecord<String, String> record =
                scope.kafka().consumeNextRecord("diary.created", null, java.time.Duration.ofSeconds(5));
            org.junit.jupiter.api.Assertions.assertNotNull(record);
            org.skyscreamer.jsonassert.JSONAssert.assertEquals(
                "{\"eventId\":\"565a865c-c9df-4fa6-a9ef-2f02b435b477\",\"userId\":\"sample-userId\",\"primaryEmotion\":\"sample-primaryEmotion\",\"energyScore\":1,\"occurredAt\":\"2026-06-20T05:59:26.398574Z\"}", record.value(), false);
        }
    }
}
