# Architecture Decision Records (ADR)

Mỗi file trong thư mục này ghi lại **một quyết định kỹ thuật quan trọng**: bối cảnh lúc quyết định, lựa chọn cuối cùng, các phương án đã cân nhắc và hệ quả.

README của dự án mô tả hệ thống **là gì** và **dùng thế nào**. Các ADR giải thích **vì sao** lại làm như vậy.

## Quy tắc

- Mỗi quyết định một file, đặt tên `NNNN-ten-ngan.md`, đánh số tăng dần.
- **Điều chỉnh nhỏ** một quyết định đã có (thu hẹp phạm vi, bỏ hoặc thêm một chi tiết): sửa thẳng ADR đó, cập nhật các mục Quyết định, Phương án đã cân nhắc, Hệ quả, và ghi `(cập nhật YYYY-MM-DD: ...)` cạnh dòng Ngày. Lịch sử thay đổi xem bằng `git log`.
- **Đổi hẳn hướng** một quyết định (chọn phương án khác hoàn toàn): viết ADR mới và đổi trạng thái ADR cũ thành *Bị thay thế bởi ADR-NNNN*, để giữ lại lý do của cả hai lần quyết định.
- Trạng thái: **Đề xuất** → **Đã chấp nhận** → (có thể) **Bị thay thế** hoặc **Bị hủy**.
- Viết ADR mới khi một quyết định ảnh hưởng nhiều module, khó đảo ngược, hoặc có thể bị hỏi lại "sao không làm cách khác?".
- Bắt đầu từ [mẫu](0000-template.md).

## Danh sách

| ADR | Quyết định | Trạng thái |
|---|---|---|
| [0001](0001-modular-monolith.md) | Modular monolith, không dùng Spring Modulith | Đã chấp nhận |
| [0002](0002-chia-bon-module.md) | Chia 4 module: identity, examdesign, examination, proctoring | Đã chấp nhận |
| [0003](0003-snapshot-de-khi-mo-ky-thi.md) | Chụp snapshot đề khi mở kỳ thi | Đã chấp nhận |
| [0004](0004-public-api-va-chieu-phu-thuoc.md) | Giao tiếp qua Public Api, phụ thuộc một chiều | Đã chấp nhận |
| [0005](0005-ep-ranh-gioi-module.md) | Ép ranh giới bằng package-private và ArchUnit | Đã chấp nhận |
| [0006](0006-flyway-va-du-lieu-theo-module.md) | Flyway, dữ liệu thuộc về từng module | Đã chấp nhận |
| [0007](0007-flat-id-linking.md) | Flat ID Linking, điều kiện dùng `@OneToMany` | Đã chấp nhận |
| [0008](0008-khong-lombok-mapstruct.md) | Không Lombok/MapStruct, entity không setter | Đã chấp nhận |
| [0009](0009-dinh-dang-response-api.md) | Response API: DTO trực tiếp, `PageResponse`, ProblemDetail + `ErrorCode` | Đã chấp nhận |
| [0010](0010-tham-gia-bang-ma-ky-thi.md) | Tham gia kỳ thi bằng mã | Đã chấp nhận |
| [0011](0011-giam-sat-hoi-qua-examination-api.md) | Giám sát hỏi trạng thái qua `ExaminationApi`, polling trước, chưa dùng event | Đã chấp nhận |
| [0012](0012-mat-ket-noi-khong-tu-dong-xu-ly.md) | Mất kết nối không tự động xử lý | Đã chấp nhận |
| [0013](0013-xu-ly-het-gio-lam-bai.md) | Xử lý hết giờ: entity chặn, job tự chốt bài, optimistic locking | Đã chấp nhận |
| [0014](0014-chi-dung-postgresql-test-bang-testcontainers.md) | Chỉ dùng PostgreSQL, test bằng Testcontainers | Đã chấp nhận |
| [0015](0015-lay-nguoi-dang-nhap-qua-authentication-principal.md) | Lấy người đang đăng nhập qua `@AuthenticationPrincipal`, service nhận ID qua tham số | Đã chấp nhận |
| [0016](0016-moi-cau-mot-diem-quy-ve-thang-10.md) | Mỗi câu 1 điểm, điểm bài quy về thang 10 | Đã chấp nhận |
