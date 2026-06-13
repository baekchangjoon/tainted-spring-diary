package com.tainted.diary.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 일기 엔티티. 제목·본문은 envelope 암호화 형태로만 저장된다.
 * 복호화 값은 DB에 절대 기록되지 않는다.
 */
@Entity
@Table(name = "diary_entry")
public class DiaryEntry {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    // 암호화된 제목
    @Column(name = "encrypted_title", nullable = false, columnDefinition = "TEXT")
    private String encryptedTitle;

    @Column(name = "title_iv", nullable = false, length = 32)
    private String titleIv;

    // 암호화된 본문
    @Column(name = "encrypted_content", nullable = false, columnDefinition = "TEXT")
    private String encryptedContent;

    @Column(name = "content_iv", nullable = false, length = 32)
    private String contentIv;

    // per-entry DEK (KEK로 암호화된 상태)
    @Column(name = "encrypted_dek", nullable = false, columnDefinition = "TEXT")
    private String encryptedDek;

    @Column(name = "dek_iv", nullable = false, length = 32)
    private String dekIv;

    @Column(name = "primary_emotion", nullable = false, length = 64)
    private String primaryEmotion;

    @Column(name = "energy_score", nullable = false)
    private int energyScore;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    protected DiaryEntry() {}

    public DiaryEntry(String id, String userId,
                      String encryptedTitle, String titleIv,
                      String encryptedContent, String contentIv,
                      String encryptedDek, String dekIv,
                      String primaryEmotion, int energyScore,
                      Instant timestamp) {
        this.id = id;
        this.userId = userId;
        this.encryptedTitle = encryptedTitle;
        this.titleIv = titleIv;
        this.encryptedContent = encryptedContent;
        this.contentIv = contentIv;
        this.encryptedDek = encryptedDek;
        this.dekIv = dekIv;
        this.primaryEmotion = primaryEmotion;
        this.energyScore = energyScore;
        this.timestamp = timestamp;
    }

    // ── 갱신 메서드 ──────────────────────────────────────────────────────────

    public void update(String encryptedTitle, String titleIv,
                       String encryptedContent, String contentIv,
                       String encryptedDek, String dekIv,
                       String primaryEmotion, int energyScore) {
        this.encryptedTitle = encryptedTitle;
        this.titleIv = titleIv;
        this.encryptedContent = encryptedContent;
        this.contentIv = contentIv;
        this.encryptedDek = encryptedDek;
        this.dekIv = dekIv;
        this.primaryEmotion = primaryEmotion;
        this.energyScore = energyScore;
    }

    // ── Getters ─────────────────────────────────────────────────────────────

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getEncryptedTitle() { return encryptedTitle; }
    public String getTitleIv() { return titleIv; }
    public String getEncryptedContent() { return encryptedContent; }
    public String getContentIv() { return contentIv; }
    public String getEncryptedDek() { return encryptedDek; }
    public String getDekIv() { return dekIv; }
    public String getPrimaryEmotion() { return primaryEmotion; }
    public int getEnergyScore() { return energyScore; }
    public Instant getTimestamp() { return timestamp; }
}
