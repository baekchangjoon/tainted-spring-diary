package io.graphrag.generated.diary.quarantine;

import io.graphrag.testlib.api.TestScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.notNullValue;

/**
 * QUARANTINED — DO NOT add to the green run. See KNOWN-LIMITATIONS.md #2 and #3.
 *
 * Original: io.graphrag.generated.diary.DiaryGetContentTest (GET /internal/diaries/{id}/content).
 *
 * s500_1 (#2): explorer captured a transient HTTP 500 for a probe id; the live SUT
 *   deterministically returns 404 for an unknown id. Flaky captured status, not a service bug.
 *
 * s404_2 (#3): GET /internal/diaries//content (empty path-var → double slash). The explorer
 *   captured 404; RestAssured normalizes/encodes the empty segment differently and the live
 *   SUT replays 400. Path-encoding mismatch between capture and replay, not a service bug.
 *
 * Both assertion bodies are preserved verbatim below.
 */
class DiaryGetContentQuarantineTest {

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
    void s500_1() throws Exception {
        scope.rest().given()
            .contentType("application/json")
        .when()
            .get("/internal/diaries/probe-id-98829/content")
        .then()
            .statusCode(500)
            .body("timestamp", notNullValue())
            .body("status", notNullValue())
            .body("error", notNullValue())
            .body("path", notNullValue());
    }

    @Test
    void s404_2() throws Exception {
        scope.rest().given()
            .contentType("application/json")
        .when()
            .get("/internal/diaries//content")
        .then()
            .statusCode(404)
            .body("type", notNullValue())
            .body("title", notNullValue())
            .body("status", notNullValue())
            .body("detail", notNullValue())
            .body("instance", notNullValue());
    }
}
