package com.jjinmak.back.boundedContext.payment.app.dto;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

/** 결제 컨텍스트 조회 API 공통 페이지 응답. 최신순(id 내림차순), 한 페이지 최대 50건 */
public record PageDto<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        boolean hasNext
) {
    public static final int MAX_SIZE = 50;

    public static <T> PageDto<T> of(Page<T> page) {
        return new PageDto<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.hasNext());
    }

    /** page 음수 → 0, size 는 1~50 으로 보정, id 내림차순 */
    public static PageRequest latest(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_SIZE), Sort.by(Sort.Direction.DESC, "id"));
    }
}
