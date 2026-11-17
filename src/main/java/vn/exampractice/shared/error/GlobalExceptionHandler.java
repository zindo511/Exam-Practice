package vn.exampractice.shared.error;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import vn.exampractice.shared.error.ProblemDetailFactory.FieldViolation;

/**
 * Nơi duy nhất chuyển exception thành {@code ProblemDetail} (README 6.8): lỗi nghiệp vụ,
 * validation, request sai định dạng, 404/405/415, optimistic locking và lỗi không lường trước.
 *
 * <p>Hai handler {@link AuthenticationException} và {@link AccessDeniedException} nhận lỗi từ
 * hai nguồn: từ filter của Security, do các class trong {@code shared.security} chuyển sang;
 * và từ bên trong controller/service (ví dụ {@code @PreAuthorize}), để chúng không rơi vào
 * lỗi 500.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ---------- Lỗi nghiệp vụ ----------

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
    }

    // ---------- 400: validation ----------

    /** {@code @Valid} trên {@code @RequestBody} không qua. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleBodyValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldViolation(error.getField(), error.getDefaultMessage()));
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.add(new FieldViolation(error.getObjectName(), error.getDefaultMessage()));
        }
        return ProblemDetailFactory.validation(errors, request.getRequestURI());
    }

    /** Ràng buộc đặt trực tiếp trên tham số ({@code @RequestParam @Min(1) int size}) không qua. */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ProblemDetail handleParameterValidation(HandlerMethodValidationException ex, HttpServletRequest request) {
        List<FieldViolation> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String parameter = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(error ->
                    errors.add(new FieldViolation(parameter, error.getDefaultMessage())));
        });
        return ProblemDetailFactory.validation(errors, request.getRequestURI());
    }

    // ---------- 400: request sai định dạng ----------

    /** Body không đọc được: JSON hỏng, sai kiểu dữ liệu của field. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(CommonErrorCode.MALFORMED_REQUEST, request.getRequestURI());
    }

    /** Tham số path/query sai kiểu, ví dụ {@code /questions/abc} khi id là số. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String detail = "Tham số '" + ex.getName() + "' không đúng kiểu dữ liệu";
        return ProblemDetailFactory.create(CommonErrorCode.MALFORMED_REQUEST, detail, request.getRequestURI());
    }

    /** Thiếu query param, header, cookie bắt buộc. */
    @ExceptionHandler(ServletRequestBindingException.class)
    ProblemDetail handleMissingRequestPart(ServletRequestBindingException ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(CommonErrorCode.MALFORMED_REQUEST, request.getRequestURI());
    }

    /**
     * {@code ?sort=} theo field không tồn tại, ví dụ {@code sort=khongCo,asc}. Spring Data chỉ phát
     * hiện khi chạy truy vấn, nên lỗi đi ra từ repository.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail handleUnknownSortProperty(PropertyReferenceException ex, HttpServletRequest request) {
        String detail = "Không sắp xếp được theo '" + ex.getPropertyName() + "'";
        return ProblemDetailFactory.create(CommonErrorCode.MALFORMED_REQUEST, detail, request.getRequestURI());
    }

    // ---------- 401, 403: từ filter của Security hoặc từ bên trong controller/service ----------

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(CommonErrorCode.UNAUTHENTICATED, request.getRequestURI());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(CommonErrorCode.FORBIDDEN, request.getRequestURI());
    }

    // ---------- 404, 405, 415 ----------

    /** Không có endpoint nào khớp URL. */
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    ProblemDetail handleNotFound(Exception ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(CommonErrorCode.NOT_FOUND, request.getRequestURI());
    }

    /** Sai HTTP method; trả kèm header {@code Allow} liệt kê các method được phép. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest request) {
        ProblemDetail problem = ProblemDetailFactory.create(CommonErrorCode.METHOD_NOT_ALLOWED, request.getRequestURI());
        HttpHeaders headers = new HttpHeaders();
        Set<HttpMethod> supported = ex.getSupportedHttpMethods();
        if (supported != null) {
            headers.setAllow(supported);
        }
        return ResponseEntity.status(problem.getStatus()).headers(headers).body(problem);
    }

    /** {@code Content-Type} của request không được hỗ trợ, ví dụ gửi text/plain cho endpoint JSON. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ProblemDetailFactory.create(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE, request.getRequestURI());
    }

    // ---------- 409: optimistic locking ----------

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail handleOptimisticLocking(ObjectOptimisticLockingFailureException ex, HttpServletRequest request) {
        log.info("Xung đột cập nhật đồng thời tại {}: {}", request.getRequestURI(), ex.getMessage());
        return ProblemDetailFactory.create(CommonErrorCode.CONCURRENT_MODIFICATION, request.getRequestURI());
    }

    // ---------- 500: lỗi không lường trước ----------

    /** Chi tiết chỉ ghi vào log server, không đưa ra response (docs/error-codes.md). */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Lỗi không lường trước tại {} {}", request.getMethod(), request.getRequestURI(), ex);
        return ProblemDetailFactory.create(CommonErrorCode.INTERNAL_ERROR, request.getRequestURI());
    }
}
