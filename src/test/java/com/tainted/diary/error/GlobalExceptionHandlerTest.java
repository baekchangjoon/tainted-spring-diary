package com.tainted.diary.error;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * GlobalExceptionHandler 순수 단위 테스트.
 * 특히 검증 핸들러의 "에러 목록이 비어있을 때" 분기(삼항 연산자)를 직접 커버한다.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleDiaryNotFound_mapsTo404ProblemDetail() {
        ProblemDetail pd = handler.handleDiaryNotFound(
                new DiaryNotFoundException("diary not found: x"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Diary not found");
        assertThat(pd.getDetail()).isEqualTo("diary not found: x");
    }

    @Test
    void handleValidation_usesFirstErrorMessage() {
        // 실제 필드 에러를 가진 BindingResult 구성
        BindingResult br = new BeanPropertyBindingResult(new Object(), "createDiaryRequest");
        br.reject("NotBlank", "title must not be blank");
        MethodArgumentNotValidException ex =
                new MethodArgumentNotValidException(null, br);

        ProblemDetail pd = handler.handleValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Validation failed");
        assertThat(pd.getDetail()).isEqualTo("title must not be blank");
    }

    @Test
    void handleValidation_fallsBackToInvalidRequest_whenNoErrors() {
        // 에러가 비어 있는 경우 → "invalid request" 분기 커버
        BindingResult emptyResult = mock(BindingResult.class);
        when(emptyResult.getAllErrors()).thenReturn(java.util.List.of());
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(emptyResult);

        ProblemDetail pd = handler.handleValidation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getDetail()).isEqualTo("invalid request");
    }
}
