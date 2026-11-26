package org.sequeless.query.autoconfig;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.equivalentTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.sequeless.ontology.schema.spi.SchemaSource;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * Mechanically enforced boundaries for the Spring Boot autoconfiguration (spec 14, section 5). Spring is allowed
 * here, because this is the adapter-side wiring module. The module wires the query side through its published
 * packages only: it never imports {@code org.sequeless.query.internal}, except that the composition seam
 * {@code QueryServiceFactory} lives in {@code api}. Of the ontology schema contract it names only the
 * {@link SchemaSource} port, the bean type the host supplies.
 */
@AnalyzeClasses(
        packages = "org.sequeless.query.autoconfig",
        importOptions = {ImportOption.DoNotIncludeTests.class})
class BoundaryRulesTest {

    @ArchTest
    static final ArchRule must_not_depend_on_query_core_internal = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.sequeless.query.internal..")
            .as(
                    "the autoconfiguration builds the query service through QueryServiceFactory, never the internal package");

    @ArchTest
    static final ArchRule may_name_only_the_schema_source_port_of_the_ontology_contract = noClasses()
            .should()
            .dependOnClassesThat(resideInAPackage("org.sequeless.ontology..")
                    .and(DescribedPredicate.not(equivalentTo(SchemaSource.class))))
            .as("the Query side sees the schema only through the Field contract (query ADR 0001); "
                    + "the host's SchemaSource bean type is the single exception");

    /**
     * Only the wiring and condition classes, the {@code @AutoConfiguration} and {@code @Configuration} types, may name
     * the schema contract. The module has no api package, so no public type exposes it through a signature.
     */
    @ArchTest
    static final ArchRule schema_contract_is_named_only_by_wiring_and_condition_classes = noClasses()
            .that()
            .areNotAnnotatedWith(AutoConfiguration.class)
            .and()
            .areNotAnnotatedWith(Configuration.class)
            .should()
            .dependOnClassesThat()
            .resideInAPackage("org.sequeless.ontology..")
            .as("the schema contract is named only by the composition root: the @AutoConfiguration and @Configuration "
                    + "classes (spec 14, section 5)");

    @ArchTest
    static final ArchRule depends_only_on_java_and_its_declared_dependencies = noClasses()
            .should()
            .dependOnClassesThat()
            .resideOutsideOfPackages(
                    "java..",
                    "javax.sql..",
                    "org.sequeless.query..",
                    "org.sequeless.ontology.schema..",
                    "org.springframework..")
            .as("the autoconfiguration depends on the JDK, the Query packages, the schema contract's SchemaSource port "
                    + "and Spring, as its declared dependencies allow");
}
