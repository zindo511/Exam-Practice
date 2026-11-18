package vn.exampractice.examination.internal;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Mã tham gia: 6 ký tự A–Z, 0–9 (ADR-0010), ngẫu nhiên. */
class AccessCodeGeneratorTest {

    private final AccessCodeGenerator generator = new AccessCodeGenerator();

    @Test
    void codes_have_6_uppercase_letters_or_digits() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.next()).matches("^[A-Z0-9]{6}$");
        }
    }

    @Test
    void codes_are_random() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            codes.add(generator.next());
        }
        assertThat(codes).hasSizeGreaterThan(990);
    }
}
