package vn.exampractice.examination.internal;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Sinh mã tham gia kỳ thi: {@value ExamSession#ACCESS_CODE_LENGTH} ký tự ngẫu nhiên từ A–Z và 0–9
 * (ADR-0010), khoảng 2,2 tỷ tổ hợp.
 *
 * <p>Dùng {@link SecureRandom} để mã không đoán được từ các mã trước. Việc mã có trùng kỳ thi khác
 * hay không do service kiểm tra.
 */
@Component
class AccessCodeGenerator {

    static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final SecureRandom random = new SecureRandom();

    String next() {
        StringBuilder code = new StringBuilder(ExamSession.ACCESS_CODE_LENGTH);
        for (int i = 0; i < ExamSession.ACCESS_CODE_LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }
}
