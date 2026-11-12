package com.example.sample.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 응답용 페이지 DTO. Spring Data {@link Page}를 그대로 노출하지 않기 위해 사용합니다.
 * page는 1부터 시작합니다 (Spring Data의 0 기반 번호 + 1).
 */
@Schema(description = "페이지 응답")
public record PageResponse<T>(
        @Schema(description = "현재 페이지 데이터") List<T> content,
        @Schema(description = "현재 페이지 (1부터)", example = "1") int page,
        @Schema(description = "페이지 크기", example = "10") int size,
        @Schema(description = "전체 건수", example = "21") long totalElements,
        @Schema(description = "전체 페이지 수", example = "3") int totalPages,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber() + 1, page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
