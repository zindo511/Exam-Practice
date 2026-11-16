# ADR-0014: Chỉ dùng PostgreSQL, test bằng Testcontainers

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-16

## Bối cảnh

Dự án được khởi tạo với H2 (chế độ tương thích PostgreSQL) làm database mặc định khi phát triển, và PostgreSQL qua profile `postgres`. Chạy hai database khác nhau gây ra các vấn đề:
- Thiết kế bị giới hạn ở phần chung của cả hai: không dùng được `JSONB`, không dùng được unique index có điều kiện (xem [database.md](../database.md)).
- "Chế độ tương thích" của H2 không giống hệt PostgreSQL; migration hoặc truy vấn chạy được trên H2 vẫn có thể lỗi trên PostgreSQL, và ngược lại.
- Test chạy trên H2 không chứng minh được ứng dụng chạy đúng trên database thật.

Máy phát triển đã có sẵn PostgreSQL.

## Quyết định

- **PostgreSQL là database duy nhất**, cho cả phát triển lẫn triển khai. Bỏ H2 và profile `postgres`; kết nối cấu hình bằng biến môi trường `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (có giá trị mặc định cho máy local).
- **Test cần database dùng Testcontainers**: mỗi lần chạy test tự bật một PostgreSQL sạch trong Docker (`postgres:18-alpine`, cùng phiên bản chính với máy phát triển) rồi xóa đi. `@ServiceConnection` tự trỏ datasource của Spring vào container (`TestcontainersConfiguration`).
- Test không cần database (ArchUnit, unit test cho entity) không bật container.

## Phương án đã cân nhắc

- **Giữ H2 cho phát triển và test:** nhanh và không cần cài gì, nhưng mất các lợi ích ở trên.
- **Test chạy trên PostgreSQL cài trên máy** (database riêng cho test): không cần Docker, nhưng test phụ thuộc trạng thái database có sẵn, mỗi người phải tự tạo database, và khó chạy giống nhau trên CI.

## Hệ quả

- (+) Phát triển, test và triển khai dùng cùng một loại database; lỗi khác biệt giữa các database không còn.
- (+) Được dùng tính năng riêng của PostgreSQL khi cần.
- (+) Test độc lập: mỗi lần chạy bắt đầu từ database trống, migration chạy lại từ đầu.
- (−) **Chạy `./mvnw test` cần Docker đang chạy.** Không có Docker thì test khởi động ứng dụng bị lỗi.
- (−) Lần đầu chạy test phải tải image PostgreSQL; mỗi lần chạy mất thêm vài giây để bật container.
- (−) Chạy ứng dụng cần PostgreSQL đang chạy và đã có database `exam_practice`.
