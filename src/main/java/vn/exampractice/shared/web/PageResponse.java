package vn.exampractice.shared.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Response của danh sách phân trang, tạo từ {@code Page} của Spring Data; không trả thẳng
 * {@code Page} ra API (ADR-0009).
 *
 * <p>{@code page} bắt đầu từ 0. Kích thước mặc định 20, tối đa 100, cấu hình ở
 * {@code spring.data.web.pageable} trong application.yaml.
 *
 * <p>Đổi entity sang DTO trước khi tạo: {@code PageResponse.from(questions.map(QuestionMapper::toResponse))}.
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
