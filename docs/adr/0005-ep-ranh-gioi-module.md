# ADR-0005: Ép ranh giới bằng package-private và ArchUnit

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Không dùng Spring Modulith ([ADR-0001](0001-modular-monolith.md)), nên cần cách khác để các quy tắc ở [ADR-0004](0004-public-api-va-chieu-phu-thuoc.md) được kiểm tra bằng máy thay vì chỉ dựa vào trí nhớ của người viết code.

Java không có khái niệm "package riêng tư". Phạm vi package-private (không ghi `public`) chỉ có tác dụng trong **đúng một package**; `internal` và `internal.persistence` bị Java coi là hai package khác nhau.

## Quyết định

Ba lớp bảo vệ:

1. **Compiler:** `internal` của mỗi module là **một package phẳng**, mọi class mặc định package-private (entity, repository, service, controller, class cài đặt Api). Spring và Hibernate đều làm việc được với class không `public`.
2. **ArchUnit** (`ModuleBoundaryTest`, chạy cùng `./mvnw test`):
   - Không có phụ thuộc vòng giữa các module.
   - Không module nào dùng `internal` của module khác.
   - Đúng chiều phụ thuộc (3 rule).
   - Entity có `@Table` mang tiền tố của module ([ADR-0006](0006-flyway-va-du-lieu-theo-module.md)).
3. **Review:** các quy tắc máy chưa kiểm tra được (không có foreign key xuyên module, một transaction một module…), liệt kê ở README mục 4.5.

## Phương án đã cân nhắc

- **Chia `internal` thành nhiều package con** (`domain/`, `persistence/`, `web/`): dễ nhìn hơn nhưng buộc mọi class phải `public`, mất lớp bảo vệ của compiler.
- **Maven multi-module** (`*-api`, `*-impl`): compiler chặn tuyệt đối, nhưng tốn cấu hình build; chưa cần ở MVP.
- **JPMS** (`module-info.java`): ẩn được hẳn package, nhưng phải tách jar và khó dùng cùng Spring.
- **Chỉ dựa vào quy ước và review:** sớm muộn sẽ có vi phạm lọt qua.

## Hệ quả

- (+) Phần lớn vi phạm bị phát hiện khi build hoặc khi chạy test, không đợi đến review.
- (+) Không thêm phụ thuộc vào code chạy thật (ArchUnit chỉ ở scope test).
- (−) Mỗi module có thể lên khoảng 20 class trong một package.
- Xem lại khi: `internal` quá lớn (dấu hiệu nên chia module, không phải chia package con), hoặc vi phạm lọt qua thường xuyên (chuyển sang Maven multi-module).
