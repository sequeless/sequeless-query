package org.sequeless.query;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Mechanically enforced hexagonal boundaries for query-core.
 * The template's rules stay. The dependency rule is widened to query-core's declared compile
 * dependencies (query-field, filter-core, kernel-identity, kernel-values), and the schema contract
 * stays out: no class may depend on {@code org.sequeless.ontology..}.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule module_is_free_of_spring = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..")
            .as("this module must stay transport-agnostic: no Spring dependency");

    @ArchTest
    static final ArchRule module_is_free_of_the_schema_contract = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.ontology..")
            .as("query-core must not depend on the ontology schema contract; "
                    + "the Field contract is the only view of the schema it may use");

    @ArchTest
    static final ArchRule api_and_spi_types_are_public = classes()
            .that()
            .resideInAnyPackage("org.sequeless.query.api..", "org.sequeless.query.spi..")
            .and()
            .areTopLevelClasses()
            .and()
            .haveSimpleNameNotEndingWith("package-info")
            .should()
            .bePublic()
            .as("the published contract (api + spi) must be exported as public types");

    /**
     * The one exception is {@code QueryServiceFactory}, the composition seam: it builds the internal service
     * so that no other module reaches into {@code internal}.
     */
    @ArchTest
    static final ArchRule api_and_spi_never_depend_on_internal = noClasses()
            .that()
            .resideInAnyPackage("org.sequeless.query.api..", "org.sequeless.query.spi..")
            .and()
            .doNotHaveFullyQualifiedName("org.sequeless.query.api.QueryServiceFactory")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.sequeless.query.internal..")
            .as("api and spi must not depend on internal, which is implementation detail");

    @ArchTest
    static final ArchRule module_depends_only_on_java_and_its_declared_dependencies = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "org.sequeless.query..",
                    "org.sequeless.kernel.identity..",
                    "org.sequeless.kernel.values..",
                    "org.sequeless.filter.api..")
            .as(
                    "query-core depends on the JDK, itself, and the Sequeless packages it imports only "
                            + "(query-field, filter-core api, kernel-identity, kernel-values); javax and jakarta are not allowed");
}
