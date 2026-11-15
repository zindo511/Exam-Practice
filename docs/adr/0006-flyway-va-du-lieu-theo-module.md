# ADR-0006: Flyway, dữ liệu thuộc về từng module

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Dự án được khởi tạo với `ddl-auto: update`: Hibernate tự sinh bảng từ entity. Cách này không đổi tên hay xóa được cột, có thể sinh schema khác nhau giữa H2 và PostgreSQL, và không để lại lịch sử thay đổi.

Các module dùng chung một database ([ADR-0001](0001-modular-monolith.md)) nhưng vẫn cần biết bảng nào thuộc module nào, để ranh giới module không bị phá ở tầng dữ liệu.

## Quyết định

**Quản lý schema:**
- **Flyway** chạy migration khi khởi động; Hibernate chỉ `validate`, không tự sửa schema.
- Một Flyway, một bảng lịch sử; migration chia thư mục theo module: `db/migration/<module>/`.
- Phiên bản dùng mốc thời gian để các module không trùng số: `V2026_11_15_0920__exam_create_session_attempt.sql`.

**Quyền sở hữu dữ liệu:**
- Tên bảng có tiền tố theo module: `identity_`, `design_`, `exam_`, `proctor_` (ArchUnit kiểm tra).
- Mỗi module chỉ đọc và ghi bảng của mình; cần dữ liệu module khác thì qua Api.
- **Không có foreign key giữa bảng của hai module**; chỉ lưu ID ([ADR-0007](0007-flat-id-linking.md)).
- **Một transaction chỉ ghi vào một module.** MVP không có thao tác nào cần ghi vào hai module cùng lúc; nếu sau này cần, phần việc ở module kia chạy sau commit qua event ([ADR-0004](0004-public-api-va-chieu-phu-thuoc.md)).

## Phương án đã cân nhắc

- **Giữ `ddl-auto: update`:** tiện lúc đầu nhưng schema dần lệch khỏi code và không kiểm soát được khi lên production.
- **Mỗi module một schema PostgreSQL và một Flyway riêng:** tách bạch hơn, nhưng phức tạp cấu hình; tiền tố bảng cộng ArchUnit là đủ cho MVP.
- **Đánh số migration tuần tự (`V1`, `V2`…):** dễ trùng số khi nhiều module viết migration độc lập.

## Hệ quả

- (+) Mọi thay đổi schema có lịch sử trong git, chạy giống nhau ở mọi môi trường.
- (+) Nhìn tên bảng hoặc tên file migration là biết module sở hữu.
- (+) Không có foreign key xuyên module nên sau này tách một module ra database riêng không vướng ràng buộc.
- (−) Mỗi entity mới phải đi kèm một file migration, nếu không ứng dụng không khởi động được.
- (−) Không có foreign key xuyên module nên database không tự đảm bảo ID trỏ tới dữ liệu tồn tại; module phải tự kiểm tra qua Api khi cần.
