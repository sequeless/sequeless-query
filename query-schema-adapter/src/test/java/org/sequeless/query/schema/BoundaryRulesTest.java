package org.sequeless.query.schema;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced boundaries for query-schema-adapter, spec 14 section 5 and query ADR 0001.
 * The template's four rules hold over {@code org.sequeless.query.schema}. The module has no api or spi
 * package of its own, so the two rules that name them hold vacuously (allowed to be empty). The
 * dependency rule is widened to query-field, schema-contract and kernel-identity. No class may depend
 * on query-core's packages, which share the {@code org.sequeless.query} prefix.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query.schema",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule module_is_free_of_spring = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("the adapter must stay transport-agnostic: no Spring dependency");

    @ArchTest
    static final ArchRule api_and_spi_types_are_public = classes()
            .that()
            .resideInAnyPackage("org.sequeless.query.schema.api..", "org.sequeless.query.schema.spi..")
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
            .resideInAnyPackage("org.sequeless.query.schema.api..", "org.sequeless.query.schema.spi..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.query.schema.internal..")
            .as("api and spi must not depend on internal, which is implementation detail")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule module_depends_only_on_java_and_its_declared_dependencies = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.query..",
                    "org.sequeless.kernel.identity..",
                    "org.sequeless.ontology.schema..")
            .as("query-schema-adapter depends on the JDK, query-field, kernel-identity and schema-contract only");

    @ArchTest
    static final ArchRule module_does_not_depend_on_query_core = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.sequeless.query.api..",
                    "org.sequeless.query.internal..",
                    "org.sequeless.query.spi..",
                    "org.sequeless.query.testkit..")
            .as("query-schema-adapter must not depend on query-core or query-testkit; it implements query-field only");
}
