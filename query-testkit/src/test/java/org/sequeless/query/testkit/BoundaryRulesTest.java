package org.sequeless.query.testkit;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced boundaries for query-testkit.
 * The template's rules stay: no Spring, and no ontology schema contract. The dependency rule is
 * widened to the testkit's declared dependencies. The testkit must not reach into query-core's
 * {@code internal} package, so the Q7 key is decoded only in query-core and is not copied here.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query.testkit",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule module_is_free_of_spring = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("the testkit must stay transport-agnostic: no Spring dependency");

    @ArchTest
    static final ArchRule module_is_free_of_the_schema_contract = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.ontology..")
            .as("the testkit must not depend on the ontology schema contract");

    @ArchTest
    static final ArchRule module_does_not_reach_into_query_core_internal = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.query.internal..")
            .as("the testkit uses query-core only through its api and spi, never its internal package");

    @ArchTest
    static final ArchRule module_depends_only_on_java_and_its_declared_dependencies = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.query..",
                    "org.sequeless.kernel.identity..",
                    "org.sequeless.kernel.values..",
                    "org.sequeless.filter.api..",
                    "com.fasterxml.jackson..",
                    "org.junit..",
                    "org.assertj..")
            .as("the testkit depends on the JDK and its declared dependencies only");

    @ArchTest
    static final ArchRule contracts_and_references_are_public = classes()
            .that()
            .haveSimpleNameEndingWith("Contract")
            .or()
            .haveSimpleNameStartingWith("InMemory")
            .should()
            .bePublic()
            .as("contracts and in-memory references are public: adapters and the BOM extend or instantiate them");
}
