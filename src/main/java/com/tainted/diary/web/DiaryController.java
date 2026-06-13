package com.tainted.diary.web;

import com.tainted.diary.service.DiaryService;
import com.tainted.diary.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/diaries")
public class DiaryController {

    private final DiaryService diaryService;

    public DiaryController(DiaryService diaryService) {
        this.diaryService = diaryService;
    }

    /** 일기 생성 — 201 Created */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateDiaryResponse create(@Valid @RequestBody CreateDiaryRequest request) {
        return diaryService.create(request);
    }

    /** 메타데이터 조회 (복호화 없음) */
    @GetMapping("/{id}")
    public DiaryMetaResponse getMeta(@PathVariable String id) {
        return diaryService.getMeta(id);
    }

    /** 서버측 복호화 본문 조회 — mindgraph 콜백용 */
    @GetMapping("/{id}/content")
    public DiaryContentResponse getContent(@PathVariable String id) {
        return diaryService.getContent(id);
    }

    /** 일기 수정 */
    @PutMapping("/{id}")
    public DiaryMetaResponse update(@PathVariable String id,
                                    @Valid @RequestBody UpdateDiaryRequest request) {
        return diaryService.update(id, request);
    }

    /** 일기 삭제 — 204 No Content */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        diaryService.delete(id);
    }
}
