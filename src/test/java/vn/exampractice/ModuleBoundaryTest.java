package vn.exampractice;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.util.Map;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Kiểm tra ranh giới giữa các module của modular monolith.
 *
 * <p>Module là package con trực tiếp của {@code vn.exampractice}. Mỗi module chỉ lộ ra
 * phần public ở gốc package (Api, dto); mọi thứ trong {@code internal} là riêng tư.
 *
 * <p>Hướng phụ thuộc cho phép: identity ◀ examdesign ◀ examination ◀ proctoring.
 */
@AnalyzeClasses(packages = "vn.exampractice", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundaryTest {

    private static final String ROOT = "vn.exampractice.";

    /** Tiền tố tên bảng bắt buộc của từng module. */
    private static final Map<String, String> TABLE_PREFIX = Map.of(
            "identity", "identity_",
            "examdesign", "design_",
            "examination", "exam_",
            "proctoring", "proctor_"
    );

    @ArchTest
    static final ArchRule modules_are_free_of_cycles =
            slices().matching("vn.exampractice.(*)..")
                    .should().beFreeOfCycles()
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule no_module_touches_internal_of_another_module =
            classes().that().resideInAPackage("vn.exampractice..")
                    .should(notDependOnInternalsOfOtherModules())
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule identity_depends_on_no_other_module =
            noClasses().that().resideInAPackage("vn.exampractice.identity..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "vn.exampractice.examdesign..",
                            "vn.exampractice.examination..",
                            "vn.exampractice.proctoring..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule examdesign_does_not_know_examination_or_proctoring =
            noClasses().that().resideInAPackage("vn.exampractice.examdesign..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "vn.exampractice.examination..",
                            "vn.exampractice.proctoring..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule examination_does_not_know_proctoring =
            noClasses().that().resideInAPackage("vn.exampractice.examination..")
                    .should().dependOnClassesThat().resideInAPackage("vn.exampractice.proctoring..")
                    .allowEmptyShould(true);

    @ArchTest
    static final ArchRule entity_tables_use_their_module_prefix =
            classes().that().areAnnotatedWith(Entity.class)
                    .should(haveTableNameWithModulePrefix())
                    .allowEmptyShould(true);

    private static ArchCondition<JavaClass> notDependOnInternalsOfOtherModules() {
        return new ArchCondition<>("not depend on the internal package of another module") {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                String originModule = moduleOf(origin.getPackageName());
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    String targetPackage = dependency.getTargetClass().getPackageName();
                    String targetModule = moduleOf(targetPackage);
                    if (targetModule == null || targetModule.equals(originModule)) {
                        continue;
                    }
                    String internal = ROOT + targetModule + ".internal";
                    if (targetPackage.equals(internal) || targetPackage.startsWith(internal + ".")) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> haveTableNameWithModulePrefix() {
        return new ArchCondition<>("declare @Table with the prefix of their module") {
            @Override
            public void check(JavaClass entity, ConditionEvents events) {
                String module = moduleOf(entity.getPackageName());
                String prefix = module == null ? null : TABLE_PREFIX.get(module);
                if (prefix == null) {
                    events.add(SimpleConditionEvent.violated(entity,
                            entity.getName() + " is an @Entity outside of a known module"));
                    return;
                }
                if (!entity.isAnnotatedWith(Table.class)) {
                    events.add(SimpleConditionEvent.violated(entity,
                            entity.getName() + " must declare @Table(name = \"" + prefix + "...\")"));
                    return;
                }
                String table = entity.getAnnotationOfType(Table.class).name();
                if (!table.startsWith(prefix)) {
                    events.add(SimpleConditionEvent.violated(entity,
                            entity.getName() + " maps to table '" + table + "' but module '" + module
                                    + "' requires prefix '" + prefix + "'"));
                }
            }
        };
    }

    /** Trả về tên module (package con trực tiếp của vn.exampractice), hoặc null nếu nằm ngoài. */
    private static String moduleOf(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return null;
        }
        String rest = packageName.substring(ROOT.length());
        int dot = rest.indexOf('.');
        return dot < 0 ? rest : rest.substring(0, dot);
    }
}
