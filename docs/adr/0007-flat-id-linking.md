# ADR-0007: Flat ID Linking, điều kiện dùng `@OneToMany`

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Quan hệ JPA (`@ManyToOne`, `@OneToMany`) chạy query ngầm mỗi khi truy cập. Duyệt một danh sách rồi chạm vào quan hệ của từng phần tử sinh ra **N+1 query**: 1 query lấy danh sách và N query lấy dữ liệu liên quan. Lỗi này khó thấy vì code trông hoàn toàn bình thường.

Ngoài ra, quan hệ JPA giữa hai module sẽ buộc chúng dùng chung entity, phá ranh giới ở [ADR-0004](0004-public-api-va-chieu-phu-thuoc.md). `@OneToMany` còn có thêm các vấn đề riêng: tải cả danh sách con, không phân trang được, quan hệ hai chiều khó giữ đồng bộ, `cascade` gây xóa dây chuyền.

## Quyết định

**Mặc định liên kết bằng ID ("Flat ID"):**
- Giữa các module và giữa các thực thể chính trong một module, entity chỉ lưu ID (`Attempt.sessionId`, `Attempt.studentId`).
- Khi cần dữ liệu liên quan: **gom ID, lấy một lần bằng `IN (...)`, ghép trong bộ nhớ**. Không gọi `findById` trong vòng lặp.
- Màn hình chỉ đọc trong cùng module có thể dùng một câu JPQL `join ... on` theo ID, trả thẳng ra DTO.

**Chỉ dùng `@OneToMany` khi thỏa đủ bốn điều kiện:**
1. Danh sách con nhỏ và có giới hạn.
2. Phần tử con không có ý nghĩa khi đứng riêng.
3. Luôn tải và lưu cùng với cha.
4. Một chiều, có `@JoinColumn`, `cascade = ALL`, `orphanRemoval = true`.

Hiện chỉ `Attempt` → `Answer` thỏa điều kiện (tối đa bằng số câu của đề). Thực thể con không có repository riêng; mọi thay đổi đi qua hàm của cha (`attempt.saveAnswer(...)`), để luật "không sửa đáp án sau khi nộp" không thể bị bỏ qua.

## Phương án đã cân nhắc

- **Dùng quan hệ JPA đầy đủ và xử lý N+1 bằng `JOIN FETCH` / `@EntityGraph`:** được, nhưng mỗi chỗ phải nhớ thêm; quên là N+1 âm thầm quay lại. Không giải quyết được vấn đề ranh giới module.
- **Bỏ hẳn `@OneToMany`, kể cả `Attempt` → `Answer`:** `Answer` sẽ có repository riêng và có thể bị lưu trực tiếp mà không qua kiểm tra của `Attempt`.

## Hệ quả

- (+) Số query cố định, không phụ thuộc số dòng; N+1 lộ rõ trong code (`findById` trong vòng lặp).
- (+) Thực thể chính được tải và lưu độc lập; không có tải lan ngoài ý muốn.
- (+) Không vướng quan hệ JPA khi tách module.
- (−) Phải tự viết bước ghép dữ liệu; Api các module cần hàm lấy theo danh sách ID.
- (−) Database không tự đảm bảo ID trỏ tới dữ liệu tồn tại giữa các module ([ADR-0006](0006-flyway-va-du-lieu-theo-module.md)).
