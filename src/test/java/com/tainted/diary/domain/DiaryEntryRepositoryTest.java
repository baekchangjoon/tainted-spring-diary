package com.tainted.diary.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DiaryEntryRepository 영속성 슬라이스 테스트 (@DataJpaTest + Testcontainers Postgres).
 * 커스텀 파생 쿼리 findByUserIdOrderByTimestampDesc 의 정렬·필터 동작을 검증한다.
 *
 * 실제 Postgres 를 사용하는 이유: 엔티티가 columnDefinition = "TEXT" 등 Postgres 방언에
 * 의존하므로, 내장 H2 가 아닌 컨테이너 DB 로 매핑을 검증한다.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class DiaryEntryRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("diary")
                    .withUsername("postgres")
                    .withPassword("postgrespw");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired DiaryEntryRepository repository;

    private DiaryEntry entry(String id, String userId, Instant ts) {
        return new DiaryEntry(
                id, userId,
                "tc", "tiv", "cc", "civ", "edek", "div",
                "JOY", 6, ts);
    }

    @Test
    void findByUserId_returnsOnlyThatUser_sortedByTimestampDesc() {
        Instant base = Instant.parse("2024-01-01T00:00:00Z");
        repository.save(entry("a", "user-1", base));                       // 가장 오래됨
        repository.save(entry("b", "user-1", base.plusSeconds(3600)));     // 가장 최신
        repository.save(entry("c", "user-1", base.plusSeconds(60)));       // 중간
        repository.save(entry("z", "user-2", base.plusSeconds(99999)));    // 다른 유저
        repository.flush();

        List<DiaryEntry> result = repository.findByUserIdOrderByTimestampDesc("user-1");

        // user-2 는 제외되고, timestamp 내림차순(b → c → a)으로 정렬돼야 한다
        assertThat(result).extracting(DiaryEntry::getId)
                .containsExactly("b", "c", "a");
    }

    @Test
    void findByUserId_returnsEmpty_whenNoEntries() {
        assertThat(repository.findByUserIdOrderByTimestampDesc("nobody")).isEmpty();
    }
}
