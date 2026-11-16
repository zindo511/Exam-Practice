package vn.exampractice;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.base.DescribedPredicate;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/**
 * Quy ước viết code kiểm tra được bằng máy (README mục 4.5 và 6.8).
 *
 * <p>Quy ước "không Lombok/MapStruct" nằm ở {@link NoCodeGenerationLibrariesTest}, vì annotation
 * của Lombok bị xóa khi biên dịch nên ArchUnit không nhìn thấy.
 */
@AnalyzeClasses(packages = "vn.exampractice", importOptions = ImportOption.DoNotIncludeTests.class)
class CodingConventionTest {

    private static final DescribedPredicate<CanBeAnnotated> PERSISTENT_CLASS =
            annotatedWith(Entity.class).or(annotatedWith(Embeddable.class))
                    .as("@Entity or @Embeddable classes");

    /** ADR-0008 / database.md: enum lưu dạng chuỗi, không lưu số thứ tự. */
    @ArchTest
    static final ArchRule entity_enums_are_stored_as_strings =
            fields().that().areDeclaredInClassesThat(PERSISTENT_CLASS)
                    .and(haveEnumType())
                    .should(beAnnotatedWithEnumeratedString())
                    .because("mặc định JPA lưu số thứ tự; thêm hằng vào giữa enum sẽ làm dữ liệu cũ bị đọc sai")
                    .allowEmptyShould(true);

    /** ADR-0008: thay đổi trạng thái đi qua hàm hành vi, không qua setter. */
    @ArchTest
    static final ArchRule entities_have_no_setters =
            noMethods().that().areDeclaredInClassesThat().areAnnotatedWith(Entity.class)
                    .should().haveNameMatching("set[A-Z].*")
                    .because("entity tự bảo vệ luật của mình; dùng hàm hành vi như submit(), extendTime()")
                    .allowEmptyShould(true);

    /** ADR-0007: liên kết giữa các thực thể chính chỉ bằng ID. */
    @ArchTest
    static final ArchRule no_many_to_one_relations =
            noFields().should().beAnnotatedWith(ManyToOne.class)
                    .because("Flat ID Linking: lưu ID thay vì quan hệ JPA để tránh N+1 và giữ ranh giới module")
                    .allowEmptyShould(true);

    /** README 4.3: Controller → Service → Entity; controller không truy cập dữ liệu trực tiếp. */
    @ArchTest
    static final ArchRule controllers_do_not_use_repositories =
            noClasses().that().haveSimpleNameEndingWith("Controller")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
                    .because("controller chỉ nhận HTTP và gọi service")
                    .allowEmptyShould(true);

    /** ADR-0004: entity không ra khỏi module; Api và controller chỉ trả DTO. */
    @ArchTest
    static final ArchRule apis_and_controllers_never_return_entities =
            methods().that().arePublic()
                    .and().areDeclaredInClassesThat(simpleNameEndingWith("Controller").or(simpleNameEndingWith("Api")))
                    .should(notReturnEntities())
                    .allowEmptyShould(true);

    private static DescribedPredicate<JavaField> haveEnumType() {
        return new DescribedPredicate<>("have an enum type") {
            @Override
            public boolean test(JavaField field) {
                return !field.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.STATIC)
                        && field.getRawType().isEnum();
            }
        };
    }

    private static ArchCondition<JavaField> beAnnotatedWithEnumeratedString() {
        return new ArchCondition<>("be annotated with @Enumerated(EnumType.STRING)") {
            @Override
            public void check(JavaField field, ConditionEvents events) {
                boolean ok = field.isAnnotatedWith(Enumerated.class)
                        && field.getAnnotationOfType(Enumerated.class).value() == EnumType.STRING;
                if (!ok) {
                    events.add(SimpleConditionEvent.violated(field,
                            field.getFullName() + " must be annotated with @Enumerated(EnumType.STRING)"));
                }
            }
        };
    }

    private static ArchCondition<JavaMethod> notReturnEntities() {
        return new ArchCondition<>("not return @Entity classes (also inside List, Optional, ResponseEntity...)") {
            @Override
            public void check(JavaMethod method, ConditionEvents events) {
                for (JavaClass type : method.getReturnType().getAllInvolvedRawTypes()) {
                    if (type.isAnnotatedWith(Entity.class)) {
                        events.add(SimpleConditionEvent.violated(method,
                                method.getFullName() + " returns entity " + type.getSimpleName() + "; return a DTO instead"));
                    }
                }
            }
        };
    }
}
