package org.sequeless.query.field;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced hexagonal boundaries, and the dependency rule for {@code query-field}: it
 * depends on the JDK, itself and {@code org.sequeless.kernel.identity} only.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query.field",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule field_is_free_of_spring = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("query-field must stay free of Spring");

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
            .as("the published contract (api + spi) must be exported as public types");

    @ArchTest
    static final ArchRule api_and_spi_never_depend_on_internal = noClasses()
            .that()
            .resideInAnyPackage("org.sequeless.query.field.api..", "org.sequeless.query.field.spi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.query.field.internal..")
            .as("api and spi must not depend on internal — internal is a pure-function implementation "
                    + "detail, not published surface");

    @ArchTest
    static final ArchRule module_depends_on_nothing_outside_java_and_itself = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages("java..", "org.sequeless.query.field..", "org.sequeless.kernel.identity..")
            .as(
                    "query-field depends only on the JDK, itself and org.sequeless.kernel.identity; this is the "
                            + "bytecode-level backstop for the Enforcer ban-all-runtime-dependencies rule (see query-field/pom.xml)");
}
