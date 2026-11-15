# ADR-0001: Modular monolith, không dùng Spring Modulith

- **Trạng thái:** Đã chấp nhận
- **Ngày:** 2026-11-15

## Bối cảnh

Hệ thống có nhiều mảng nghiệp vụ tách biệt (tài khoản, soạn đề, tổ chức thi, giám sát) nhưng đang ở giai đoạn MVP, đội ngũ nhỏ. Cần ranh giới rõ ràng giữa các mảng để code không dính vào nhau, đồng thời giữ việc phát triển và deploy đơn giản.

## Quyết định

- Một ứng dụng Spring Boot duy nhất, một database, deploy một lần.
- Chia thành các **module** theo nghiệp vụ; mỗi module là một package con trực tiếp của `vn.exampractice`.
- **Không dùng thư viện Spring Modulith.** Modular monolith ở đây là cách tổ chức kiến trúc; ranh giới được giữ bằng phạm vi truy cập của Java và test ArchUnit ([ADR-0005](0005-ep-ranh-gioi-module.md)).

## Phương án đã cân nhắc

- **Monolith chia theo tầng** (`controller/`, `service/`, `repository/` dùng chung): quen thuộc, nhưng không có ranh giới nghiệp vụ; mọi service gọi được mọi repository, càng về sau càng rối.
- **Microservices:** ranh giới cứng nhất, nhưng phải trả giá bằng gọi qua mạng, giao dịch phân tán, nhiều thứ phải vận hành. Quá nặng cho MVP.
- **Spring Modulith:** có sẵn kiểm tra ranh giới và event bền vững, nhưng thêm phụ thuộc và quy ước riêng. Nhóm muốn giữ stack đơn giản; phần kiểm tra ranh giới cần thiết làm được bằng ArchUnit.

## Hệ quả

- (+) Deploy, debug, chạy local đơn giản; giao dịch database cục bộ.
- (+) Refactor giữa các module dễ hơn nhiều so với giữa các service.
- (+) Ranh giới rõ nên có thể tách một module thành service riêng khi thật sự cần.
- (−) Ranh giới không có sẵn từ framework; phải tự giữ bằng quy ước, ArchUnit và review.
- (−) Không có sẵn event bền vững giữa module. MVP chưa dùng event ([ADR-0011](0011-giam-sat-hoi-qua-examination-api.md)); khi cần, event của Spring chỉ nằm trong bộ nhớ và có thể mất nếu ứng dụng sập đúng lúc.
- Xem lại khi: một module cần mở rộng hoặc deploy độc lập, hoặc cần event không được phép mất.
