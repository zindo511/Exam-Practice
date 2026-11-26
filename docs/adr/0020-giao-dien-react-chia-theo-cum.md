# ADR-0020: Giao diện React trong cùng repo, code chia theo cụm chức năng

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-26

## Bối cảnh

Backend MVP đã đủ API. Cần giao diện cho giáo viên (nhiều bảng và form) và học sinh (màn làm bài phải đáng tin: đồng hồ theo giờ server, tự lưu đáp án, báo rời tab). Người viết quen Java hơn JavaScript, nên cần ít khái niệm mới và dễ tìm code.

## Quyết định

- **Vị trí:** thư mục `frontend/` trong repo này, để thay đổi API và giao diện đi cùng một pull request.
- **Công nghệ:** Vite, React, TypeScript (`strict`), Ant Design, TanStack Query, React Router, Vitest.
- **Code chia theo cụm chức năng:** `src/features/<cụm>/` chứa đủ API, trang, hook của cụm đó. `api.ts` của cụm khai báo mọi API mà cụm gọi và kiểu dữ liệu đi kèm.
  - Một cụm là các màn cùng chức năng, không phải mỗi màn một thư mục.
  - Cụm khác cần thì import từ `api.ts` của cụm đó, không chép lại.
  - `src/shared/` chỉ chứa thứ từ 2 cụm trở lên cùng dùng.
- **Trang không tự gọi `fetch`.** Mọi request đi qua `shared/http.ts`: gắn token, đổi ProblemDetail thành `ApiError`. Xử lý lỗi theo `code` ([ADR-0009](0009-dinh-dang-response-api.md)).
- **Dữ liệu từ server do TanStack Query giữ;** không dùng Redux. Riêng đăng nhập dùng React Context.
- **Token lưu ở `sessionStorage`**, đóng tab là mất.
- **Chặn trang theo vai trò chỉ là điều hướng;** quyền thật do backend kiểm tra.
- **Lúc phát triển** Vite chuyển `/api` sang cổng 8080. Triển khai khác địa chỉ thì dùng `CORS_ALLOWED_ORIGINS`.
- **Test:** chỉ viết cho logic dễ sai (gọi API, đồng hồ, lưu đáp án), không test từng component.

## Phương án đã cân nhắc

- **Chia theo loại file** (`api/`, `pages/`, `hooks/`): sửa một màn phải mở nhiều thư mục.
- **Repo riêng cho giao diện:** đổi API phải mở hai pull request và giữ hai bên khớp nhau.
- **JavaScript:** ít cấu hình hơn, nhưng sai tên field của API chỉ lộ ra khi chạy.
- **Tự viết component thay cho Ant Design:** kiểm soát được giao diện hơn, nhưng phải tự làm bảng, form, hộp thoại.
- **Token ở `localStorage`:** không phải đăng nhập lại khi mở tab mới, nhưng trên máy dùng chung người sau vào được tài khoản người trước.

## Hệ quả

- (+) Sửa một màn chỉ cần mở một thư mục.
- (+) Kiểu dữ liệu bắt lỗi gọi sai field ngay khi build.
- (−) Kiểu dữ liệu ở `api.ts` viết tay, đổi DTO của backend thì phải sửa theo.
- (−) Ant Design làm file tải lần đầu lớn (khoảng 280 KB sau gzip).
- (−) Học sinh mở tab mới phải đăng nhập lại.
- (−) Token trong `sessionStorage` vẫn bị đọc được nếu trang dính XSS; giữ rủi ro thấp bằng cách không chèn HTML thô.
- Xem lại khi: kiểu dữ liệu viết tay hay bị lệch (khi đó sinh từ OpenAPI), hoặc file tải lần đầu quá chậm (khi đó tách theo trang).
