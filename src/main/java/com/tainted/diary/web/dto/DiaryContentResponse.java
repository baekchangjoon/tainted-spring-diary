package com.tainted.diary.web.dto;

/**
 * 서버측 복호화 후 반환되는 일기 본문 응답.
 */
public record DiaryContentResponse(
        String diaryId,
        String title,
        String content
) {}
