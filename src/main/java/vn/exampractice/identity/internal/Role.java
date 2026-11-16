package vn.exampractice.identity.internal;

/**
 * Vai trò của user (README 3.1). Lưu trong cột {@code identity_user.role} dạng tên hằng.
 *
 * <p>Thêm hằng mới thì sửa cả ràng buộc {@code CHECK} bằng một migration mới.
 */
enum Role {
    STUDENT,
    TEACHER
}
