package org.sequeless.query.postgres;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced boundaries for the postgres adapter, spec 14 sections 5 and 6.5. The adapter reads
 * query-core only through its published packages, so it never imports {@code org.sequeless.query.internal}
 * (design-preferences.md). It keeps out the ontology schema contract, as query-core does. Spring is allowed
 * here, because this is the adapter module.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query.postgres",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule must_not_depend_on_query_core_internal = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.sequeless.query.internal..")
            .as("the adapter uses query-core through its published api and spi, never its internal package");

    @ArchTest
    static final ArchRule must_not_depend_on_the_ontology_schema_contract = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.sequeless.ontology..")
            .as("the Query side sees the schema only through the Field contract (query ADR 0001)");

    @ArchTest
    static final ArchRule depends_only_on_java_and_its_declared_dependencies = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "javax.sql..",
                    "org.sequeless.query..",
                    "org.sequeless.filter..",
                    "org.sequeless.kernel.identity..",
                    "org.sequeless.kernel.values..",
                    "org.springframework..",
                    "org.flywaydb..")
            .as("the adapter depends on the JDK, the Query and filter packages it declares, Spring JDBC and Flyway");
}
