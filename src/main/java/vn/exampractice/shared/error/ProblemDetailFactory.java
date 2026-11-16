package vn.exampractice.shared.error;

import java.net.URI;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ProblemDetail;

/**
 * Tạo {@code ProblemDetail} theo định dạng chung (README 6.6): {@code code}, {@code timestamp},
 * {@code errors}. Dùng chung cho {@link GlobalExceptionHandler} và các handler lỗi của Security,
 * để mọi response lỗi có cùng một hình dạng.
 *
 * <p>{@code type} ({@code about:blank}) và {@code title} do Spring tự điền từ status.
 * {@code instance} luôn được set ở đây, vì lỗi 401/403 xảy ra trong filter của Security,
 * trước Spring MVC, nên không ai điền hộ.
 */
public final class ProblemDetailFactory {

    /** Spring 7 bỏ field {@code type} khỏi JSON khi để mặc định; set rõ để luôn có mặt (README 6.6). */
    private static final URI ABOUT_BLANK = URI.create("about:blank");

    private ProblemDetailFactory() {
    }

    /** Lỗi với thông điệp mặc định của mã lỗi. */
    public static ProblemDetail create(ErrorCode errorCode, String path) {
        return create(errorCode, errorCode.defaultMessage(), path);
    }

    /** Lỗi với {@code detail} cụ thể. */
    public static ProblemDetail create(ErrorCode errorCode, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(errorCode.status(), detail);
        problem.setType(ABOUT_BLANK);
        problem.setInstance(URI.create(path));
        problem.setProperty("code", errorCode.code());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }

    /** Lỗi validation ({@code VALIDATION_FAILED}) kèm danh sách lỗi theo từng field. */
    public static ProblemDetail validation(List<FieldViolation> errors, String path) {
        ProblemDetail problem = create(CommonErrorCode.VALIDATION_FAILED, path);
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Một phần tử của mảng {@code errors}. */
    public record FieldViolation(String field, String message) {
    }
}
