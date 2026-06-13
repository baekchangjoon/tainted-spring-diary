package com.tainted.diary.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DiaryEntryRepository extends JpaRepository<DiaryEntry, String> {
    List<DiaryEntry> findByUserIdOrderByTimestampDesc(String userId);
}
