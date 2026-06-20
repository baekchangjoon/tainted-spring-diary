package com.tainted.diary.web;

import com.tainted.diary.error.DiaryNotFoundException;
import com.tainted.diary.error.GlobalExceptionHandler;
import com.tainted.diary.service.DiaryService;
import com.tainted.diary.web.dto.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * DiaryController 웹 슬라이스 테스트 (@WebMvcTest).
 * DiaryService 를 mock 으로 두고 HTTP 매핑·상태코드·검증오류·예외→ProblemDetail 변환을 검증한다.
 * GlobalExceptionHandler 를 명시 Import 해 슬라이스 안에서 동작시킨다.
 */
@WebMvcTest(DiaryController.class)
@Import(GlobalExceptionHandler.class)
class DiaryControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean DiaryService diaryService;

    @Test
    void create_returns201_andDelegatesToService() throws Exception {
        when(diaryService.create(any())).thenReturn(
                new CreateDiaryResponse("d-1", "user-1", "JOY", 8, "2024-01-02T03:04:05Z"));

        mockMvc.perform(post("/internal/diaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","title":"제목","content":"본문",
                                 "primaryEmotion":"JOY","energyScore":8}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.diaryId").value("d-1"))
                .andExpect(jsonPath("$.userId").value("user-1"))
                .andExpect(jsonPath("$.energyScore").value(8));
    }

    @Test
    void create_returns400_whenTitleBlank() throws Exception {
        // 참고: spring.mvc.problemdetails.enabled=true 일 때 프레임워크의
        // ResponseEntityExceptionHandler 가 MethodArgumentNotValidException 을 먼저 처리하므로
        // 응답은 RFC7807 problem+json (400) 이다. 커스텀 GlobalExceptionHandler.handleValidation
        // 의 로직 자체는 GlobalExceptionHandlerTest 가 단위로 직접 커버한다.
        mockMvc.perform(post("/internal/diaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","title":"   ","content":"본문",
                                 "primaryEmotion":"JOY","energyScore":8}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
        verifyNoInteractions(diaryService);
    }

    @Test
    void create_returns400_whenEnergyScoreOutOfRange() throws Exception {
        // energyScore=11 → @Max(10) 위반
        mockMvc.perform(post("/internal/diaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","title":"제목","content":"본문",
                                 "primaryEmotion":"JOY","energyScore":11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void create_returns400_whenEnergyScoreNull() throws Exception {
        mockMvc.perform(post("/internal/diaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"user-1","title":"제목","content":"본문",
                                 "primaryEmotion":"JOY"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getMeta_returns200WithMetadata() throws Exception {
        when(diaryService.getMeta("d-1")).thenReturn(
                new DiaryMetaResponse("d-1", "user-1", "JOY", 8, "2024-01-02T03:04:05Z"));

        mockMvc.perform(get("/internal/diaries/d-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaryId").value("d-1"))
                .andExpect(jsonPath("$.primaryEmotion").value("JOY"))
                // 메타에는 복호화 본문이 없어야 한다
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    @Test
    void getMeta_returns404ProblemJson_whenServiceThrows() throws Exception {
        when(diaryService.getMeta("nope"))
                .thenThrow(new DiaryNotFoundException("diary not found: nope"));

        mockMvc.perform(get("/internal/diaries/nope"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.title").value("Diary not found"))
                .andExpect(jsonPath("$.detail").value("diary not found: nope"));
    }

    @Test
    void getContent_returns200WithDecryptedBody() throws Exception {
        when(diaryService.getContent("d-1")).thenReturn(
                new DiaryContentResponse("d-1", "제목", "본문"));

        mockMvc.perform(get("/internal/diaries/d-1/content"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diaryId").value("d-1"))
                .andExpect(jsonPath("$.title").value("제목"))
                .andExpect(jsonPath("$.content").value("본문"));
    }

    @Test
    void update_returns200_andDelegates() throws Exception {
        when(diaryService.update(eq("d-1"), any())).thenReturn(
                new DiaryMetaResponse("d-1", "user-1", "CALM", 5, "2024-01-02T03:04:05Z"));

        mockMvc.perform(put("/internal/diaries/d-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 제목","content":"새 본문",
                                 "primaryEmotion":"CALM","energyScore":5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryEmotion").value("CALM"))
                .andExpect(jsonPath("$.energyScore").value(5));
    }

    @Test
    void update_returns400_whenContentBlank() throws Exception {
        mockMvc.perform(put("/internal/diaries/d-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"새 제목","content":"",
                                 "primaryEmotion":"CALM","energyScore":5}
                                """))
                .andExpect(status().isBadRequest());
        verify(diaryService, never()).update(any(), any());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/internal/diaries/d-1"))
                .andExpect(status().isNoContent());
        verify(diaryService).delete("d-1");
    }

    @Test
    void delete_returns404_whenServiceThrows() throws Exception {
        doThrow(new DiaryNotFoundException("diary not found: nope"))
                .when(diaryService).delete("nope");

        mockMvc.perform(delete("/internal/diaries/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Diary not found"));
    }
}
