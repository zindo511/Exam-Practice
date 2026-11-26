/** Một trang kết quả của API danh sách (README 6.5.1). `page` bắt đầu từ 0. */
export type PageResponse<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};
