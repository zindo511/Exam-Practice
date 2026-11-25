package vn.exampractice.examination;

import java.util.Optional;

import vn.exampractice.examination.dto.AttemptRef;
import vn.exampractice.examination.dto.AttemptSummary;
import vn.exampractice.examination.dto.SessionAttempts;

/**
 * Cổng gọi đồng bộ cho module proctoring (ADR-0004, ADR-0011): đọc trạng thái bài làm cho màn hình giám
 * sát, và thực hiện lệnh của giáo viên lên bài làm (cộng giờ, khóa bài). Đây là interface Java gọi trong
 * cùng ứng dụng, không phải REST API.
 *
 * <p><b>Quyền:</b> mọi hàm nhận ID của người đang đăng nhập (lấy từ token, ADR-0015) và examination tự
 * kiểm tra, vì chỉ examination biết kỳ thi của giáo viên nào, bài làm của học sinh nào.
 *
 * <p><b>Lỗi:</b> hàm đọc trả {@link Optional#empty()} khi không tìm thấy hoặc không có quyền, không phân
 * biệt lý do; proctoring tự chọn mã lỗi. Hàm lệnh ném {@code BusinessException} với mã của examination
 * ({@code ATTEMPT_NOT_FOUND}, {@code ATTEMPT_ALREADY_SUBMITTED}, {@code ATTEMPT_EXPIRED},
 * {@code ATTEMPT_LOCKED}), vì giáo viên cần biết đúng lý do.
 *
 * <p><b>Transaction:</b> hàm lệnh tự thử lại khi xung đột ghi đồng thời, mỗi lần một transaction mới
 * (ADR-0017). Phải gọi chúng <b>ngoài</b> transaction: nếu bên gọi đang mở transaction thì lần thử lại
 * dùng chung transaction đã lỗi và không thử lại được. Proctoring gọi lệnh trước, ghi nhật ký của mình
 * trong transaction riêng sau đó.
 */
public interface ExaminationApi {

    /**
     * Thông tin kỳ thi và trạng thái bài làm của mọi học sinh đã tham gia, kể cả người chưa bắt đầu
     * ({@code NOT_STARTED}). Màn hình giám sát gọi khoảng 5 giây một lần (ADR-0011).
     *
     * @return rỗng nếu kỳ thi không tồn tại hoặc không phải của {@code teacherId}
     */
    Optional<SessionAttempts> getSessionAttempts(Long sessionId, Long teacherId);

    /**
     * Bài làm {@code attemptId} có phải của học sinh {@code studentId} không, thuộc kỳ thi nào. Proctoring
     * gọi mỗi khi nhận sự kiện từ trình duyệt học sinh, để học sinh không gửi tín hiệu thay cho bài của người khác
     * (ADR-0011).
     *
     * @return rỗng nếu bài làm không tồn tại hoặc của học sinh khác
     */
    Optional<AttemptRef> findAttemptOfStudent(Long attemptId, Long studentId);

    /**
     * Cộng thêm giờ cho một bài đang làm (state-machines.md mục 2). Chỉ được khi chưa quá hạn chót. Hạn
     * chót mới có thể vượt giờ đóng kỳ thi; kỳ thi chờ bài này hết giờ rồi mới đóng (ADR-0018).
     *
     * @param minutes số phút cộng thêm, lớn hơn 0
     * @return bài làm sau khi cộng
     */
    AttemptSummary extendTime(Long teacherId, Long attemptId, int minutes);

    /**
     * Khóa bài vì vi phạm: bài kết thúc ngay ở {@code LOCKED}, chấm từ các đáp án đã lưu
     * (state-machines.md mục 2, ADR-0012). Chỉ khóa được bài đang làm.
     *
     * @return bài làm sau khi khóa
     */
    AttemptSummary lock(Long teacherId, Long attemptId);
}
