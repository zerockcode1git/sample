package com.example.sample.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 목록 응답용 페이지 DTO. Spring Data {@link Page}를 그대로 노출하지 않기 위해 사용합니다.
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              boolean hasNext) {

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(), page.hasNext());
    }
}
