package org.sequeless.query.field.testfixtures;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced boundaries for the query-field test kit: the template's four rules,
 * scanning {@code org.sequeless.query.field.testfixtures}. The kit has no api or spi
 * packages of its own, so the two rules that name them hold vacuously here (allowed to be empty).
 * The dependency rule is widened to query-field, kernel-identity, JUnit and AssertJ, which are the
 * declared dependencies of this module.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query.field.testfixtures",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule module_is_free_of_spring = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("the test kit must stay transport-agnostic: no Spring dependency");

    @ArchTest
    static final ArchRule api_and_spi_types_are_public = classes()
            .that()
            .resideInAnyPackage("org.sequeless.query.field.api..", "org.sequeless.query.field.spi..")
            .and()
            .areTopLevelClasses()
            .and()
            .haveSimpleNameNotEndingWith("package-info")
            .should()
            .bePublic()
            .as("the published contract (api + spi) must be exported as public types")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule api_and_spi_never_depend_on_internal = noClasses()
            .that()
            .resideInAnyPackage("org.sequeless.query.field.api..", "org.sequeless.query.field.spi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.query.field.internal..")
            .as("api and spi must not depend on internal, which is implementation detail")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule module_depends_only_on_java_junit_assertj_and_declared_dependencies = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.query.field..",
                    "org.sequeless.kernel.identity..",
                    "org.junit..",
                    "org.assertj..")
            .as("the test kit depends on the JDK, query-field, kernel-identity, JUnit and AssertJ only; "
                    + "widen per declared dependency");
}
