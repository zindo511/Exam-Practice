package vn.exampractice;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * ADR-0008: dự án không dùng Lombok và MapStruct.
 *
 * <p>Annotation của Lombok (@Getter, @Data...) bị xóa khi biên dịch nên ArchUnit không thấy được.
 * Thay vào đó, kiểm tra rằng hai thư viện này không có trên classpath: thêm lại dependency
 * vào pom.xml là test này đỏ.
 */
class NoCodeGenerationLibrariesTest {

    @ParameterizedTest
    @ValueSource(strings = {"lombok.Getter", "org.mapstruct.Mapper"})
    void libraryIsNotOnClasspath(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className),
                className + " có trên classpath; dự án không dùng Lombok/MapStruct (ADR-0008)");
    }
}
