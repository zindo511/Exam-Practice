# ADR-0008: Không Lombok/MapStruct, entity không setter

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Lombok và MapStruct sinh code lúc biên dịch: code thật sự chạy không nằm trong file nguồn, khó lần theo luồng dữ liệu. Trên entity, `@Data` và `@Setter` còn gây hại trực tiếp:
- Sinh setter `public` cho mọi field, ai cũng đổi được trạng thái mà không qua kiểm tra luật.
- `equals/hashCode` sai khi entity chưa có ID hoặc nằm trong `Set`.
- `toString` có thể kích hoạt lazy loading hoặc đệ quy vô hạn.

Với MapStruct, các ánh xạ phức tạp viết bằng annotation (`@Mapping(expression = ...)`) thường khó đọc hơn viết bằng Java thường.

## Quyết định

**Không dùng Lombok và MapStruct.**

- **DTO là `record`**: Java tự sinh constructor, accessor, `equals`, `hashCode`, `toString`.
- **Mapper viết tay từng field:**
  - DTO dùng trong module (request/response REST): hàm static `from(...)` trong chính DTO.
  - DTO của Api công khai (`<module>/dto/`): chuyển đổi trong `internal`, vì phần công khai không được phụ thuộc vào entity.

**Entity bảo vệ luật của chính nó:**
- Field `private`, **không có setter**.
- Constructor rỗng `protected`, chỉ dành cho Hibernate (Hibernate ghi thẳng vào field vì `@Id` đặt trên field).
- Tạo mới qua constructor hoặc hàm factory có đủ tham số bắt buộc và có kiểm tra.
- Thay đổi trạng thái chỉ qua **hàm hành vi** đặt tên theo nghiệp vụ: `submit()`, `extendTime()`, `revise()`. Mỗi hàm kiểm tra luật trước, rồi đổi các field liên quan cùng lúc.
- Getter chỉ viết khi cần; collection trả về bản chỉ đọc.

## Phương án đã cân nhắc

- **Lombok có kiểm soát** (chỉ `@Getter`, cấm `@Data`/`@Setter`): giảm code, nhưng vẫn phụ thuộc cách Lombok can thiệp vào trình biên dịch (thường phải chờ bản cập nhật khi lên JDK mới) và cần plugin IDE.
- **MapStruct:** an toàn kiểu, báo khi quên map field; đáng dùng khi có rất nhiều DTO lớn lặp lại. Hệ thống này mỗi module chỉ có vài DTO nhỏ.

## Hệ quả

- (+) Code nhìn thấy là code chạy; đọc hàm `from(...)` là thấy field nào đi đâu.
- (+) Chỉ có một con đường thay đổi dữ liệu: qua hàm hành vi có kiểm tra luật.
- (+) Không phụ thuộc annotation processor; không cần plugin IDE.
- (−) Viết thêm constructor, getter và mapper (giảm bớt nhờ `record` và IDE).
- (−) Thêm field mới mà quên cập nhật mapper thì compiler không nhắc.
- Xem lại khi: số lượng DTO và ánh xạ lặp lại tăng đến mức viết tay trở thành gánh nặng.
