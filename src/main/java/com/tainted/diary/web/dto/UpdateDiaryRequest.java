package com.tainted.diary.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDiaryRequest(
        @NotBlank String title,
        @NotBlank String content,
        @NotBlank String primaryEmotion,
        @NotNull @Min(1) @Max(10) Integer energyScore
) {}
