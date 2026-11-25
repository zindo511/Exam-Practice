# ADR-0004: Giao tiếp qua Public Api, phụ thuộc một chiều

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Các module cần dùng chức năng và dữ liệu của nhau: `examination` cần nội dung đề, `proctoring` cần trạng thái bài làm và cần ra lệnh cộng giờ, khóa bài. Nếu cho gọi thẳng service hoặc repository của nhau, ranh giới module chỉ còn trên giấy.

## Quyết định

**Mỗi module mở một cửa công khai duy nhất:**

```
<module>/
├── <Module>Api.java   ← interface: các thao tác module khác được gọi
├── dto/               ← record bất biến dùng trong Api
└── internal/          ← mọi thứ còn lại, module khác không được dùng
```

- Api trả **DTO**, không bao giờ trả entity.
- Trong cùng module các service gọi nhau tự do; **giữa hai module chỉ qua Api**.
- Api nên có hàm lấy theo danh sách ID (ví dụ `getUsersByIds(Set<Long>)`) để tránh gọi lặp ([ADR-0007](0007-flat-id-linking.md)).

**Phụ thuộc một chiều, không có vòng:**

```
identity ◀ examdesign ◀ examination ◀ proctoring
```

- `examdesign` không biết `examination`; `examination` không biết `proctoring`.
- Nếu A gọi Api của B thì B không được gọi Api của A.
- `proctoring` lấy trạng thái bài làm và gửi lệnh của giáo viên (cộng giờ, khóa bài) đều qua `ExaminationApi` ([ADR-0011](0011-giam-sat-hoi-qua-examination-api.md)).

**Event: chưa dùng ở MVP.** Mọi giao tiếp giữa module đều là gọi Api đồng bộ. Khi sau này một module ở bên trái cần **báo** cho module ở bên phải (ví dụ `examination` báo "bài vừa được nộp" để đẩy thời gian thực tới màn hình giám sát), dùng event thay vì gọi ngược Api:
- Class event đặt trong `<module>/event/`, là phần công khai của module phát.
- Bên nghe xử lý bằng `@TransactionalEventListener`, chạy sau khi transaction của bên phát đã commit.
- Thêm event là một quyết định mới, ghi bằng ADR riêng.

## Phương án đã cân nhắc

- **Gọi thẳng service của module khác:** service là chi tiết nội bộ, thay đổi thường xuyên và hay trả về entity. Module bên ngoài sẽ vỡ theo mỗi lần sửa nội bộ, và có thể sửa entity vượt qua luật nghiệp vụ.
- **Dùng event ngay từ đầu cho giám sát:** thêm nhiều phần chuyển động và nguy cơ dữ liệu lệch mà MVP chưa cần (xem [ADR-0011](0011-giam-sat-hoi-qua-examination-api.md)).
- **Cho phép phụ thuộc hai chiều:** sinh vòng, không hiểu và không test được từng module riêng.

## Hệ quả

**Public Api:**
- (+) Sửa bên trong module thoải mái miễn giữ nguyên Api.
- (+) Toàn bộ cam kết của module nằm trong một file, dễ review.
- (+) Không rò rỉ entity: không sửa dữ liệu vượt luật, không `LazyInitializationException`, không phụ thuộc cấu trúc bảng.
- (+) Test module khác chỉ cần mock một interface; có thể thay cài đặt bằng HTTP client nếu tách service.
- (−) Thêm interface, class cài đặt và DTO cho mỗi module.

**Chiều phụ thuộc:**
- (+) Biết trước phạm vi ảnh hưởng: sửa module nào thì chỉ các module bên phải có thể bị ảnh hưởng.
- (+) Phần cốt lõi (`examination`) không bị ảnh hưởng bởi phần hay thay đổi (`proctoring`), vì `examination` không gọi tới `proctoring`.
- (+) Khi cần thêm tính năng phản ứng theo việc nộp bài (thông báo, thống kê), có thể cho `examination` phát event mà không phải phụ thuộc vào module mới.
- (+) Không có vòng phụ thuộc giữa các bean Spring.
- (−) Module ở gốc (`identity`) phải rất ổn định vì mọi module phụ thuộc vào nó.
