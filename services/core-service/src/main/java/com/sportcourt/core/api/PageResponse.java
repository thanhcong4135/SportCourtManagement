package com.sportcourt.core.api;

import org.springframework.data.domain.Page;

import java.util.List;

public record PageResponse<T>(
    List<T> items,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean hasNext
) {
    public static <T> PageResponse<T> from(Page<T> pageData) {
        return new PageResponse<>(
            pageData.getContent(),
            pageData.getNumber(),
            pageData.getSize(),
            pageData.getTotalElements(),
            pageData.getTotalPages(),
            pageData.hasNext()
        );
    }
}
