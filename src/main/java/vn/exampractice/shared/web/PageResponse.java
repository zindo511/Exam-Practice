package vn.exampractice.shared.web;

/**
 * Response của danh sách phân trang, tạo từ {@code Page} của Spring Data; không trả thẳng
 * {@code Page} ra API (ADR-0009).
 */
public record PageResponse<T>() {
}
