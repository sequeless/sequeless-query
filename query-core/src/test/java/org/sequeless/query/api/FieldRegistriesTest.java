package org.sequeless.query.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sequeless.query.testsupport.QueryFixtures.FILTER_ONLY;
import static org.sequeless.query.testsupport.QueryFixtures.FILTER_SORT;
import static org.sequeless.query.testsupport.QueryFixtures.classRef;
import static org.sequeless.query.testsupport.QueryFixtures.field;
import static org.sequeless.query.testsupport.QueryFixtures.path;
import static org.sequeless.query.testsupport.QueryFixtures.scope;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.sequeless.filter.api.FieldDefinition;
import org.sequeless.filter.api.FieldRegistry;
import org.sequeless.query.field.api.Capabilities;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldType;

class FieldRegistriesTest {

    private static final ClassRef INVOICE = classRef("Invoice");

    @Test
    void aFilterableFieldIsRegisteredUnderItsEncodedKey() {
        Field status = field(path("urn:ont:status"), FieldType.STRING, FILTER_ONLY);

        FieldRegistry registry = FieldRegistries.of(scope(INVOICE, List.of(status)));

        assertThat(registry.find("urn:ont:status")).isPresent();
    }

    @Test
    void aFilterableObjectRefFieldIsRegisteredWithTheUuidFormat() {
        Field author = field(path("urn:ont:author"), FieldType.OBJECT_REF, FILTER_ONLY);

        FieldRegistry registry = FieldRegistries.of(scope(INVOICE, List.of(author)));

        assertThat(registry.find("urn:ont:author").orElseThrow().getJsonSchemaFormat())
                .isEqualTo("uuid");
    }

    @Test
    void aTraversalFieldIsRegisteredUnderItsPipeJoinedKey() {
        Field city = field(path("urn:ont:customer", "urn:ont:city"), FieldType.STRING, FILTER_ONLY);

        FieldRegistry registry = FieldRegistries.of(scope(INVOICE, List.of(city)));

        assertThat(registry.find("urn:ont:customer|urn:ont:city")).isPresent();
        assertThat(registry.find("urn:ont:customer")).isEmpty();
    }

    @Test
    void aNonFilterableFieldIsAbsent() {
        Field internal = field(path("urn:ont:notes"), FieldType.STRING, new Capabilities(false, false, false, true));

        FieldRegistry registry = FieldRegistries.of(scope(INVOICE, List.of(internal)));

        assertThat(registry.find("urn:ont:notes")).isEmpty();
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void everyFieldTypeConstantMapsToADefinitionWithPermittedOperators() {
        List<Field> fields = new ArrayList<>();
        for (FieldType type : FieldType.values()) {
            fields.add(field(path("urn:ont:" + type.name()), type, FILTER_SORT));
        }

        FieldRegistry registry = FieldRegistries.of(scope(INVOICE, fields));

        assertThat(registry.all()).hasSize(FieldType.values().length);
        for (FieldType type : FieldType.values()) {
            FieldDefinition definition = registry.find("urn:ont:" + type.name()).orElseThrow();
            assertThat(definition.getPermittedOperators())
                    .as("operators for %s", type)
                    .isNotEmpty();
        }
    }

    @ParameterizedTest
    @CsvSource({
        "STRING,string,",
        "LANG_STRING,string,",
        "IRI_REF,string,",
        "CONCEPT_REF,string,",
        "OBJECT_REF,string,uuid",
        "BINARY,string,",
        "GEO,string,",
        "UNKNOWN,string,",
        "INTEGER,integer,",
        "DECIMAL,number,",
        "BOOLEAN,boolean,",
        "DATE,string,date",
        "DATETIME,string,date-time",
        "TIME,string,time",
        "DURATION,string,duration"
    })
    void aFieldTypeMapsToItsJsonSchemaTypeAndFormat(FieldType type, String jsonSchemaType, String jsonSchemaFormat) {
        Field field = field(path("urn:ont:value"), type, FILTER_ONLY);

        FieldDefinition definition = FieldRegistries.of(scope(INVOICE, List.of(field)))
                .find("urn:ont:value")
                .orElseThrow();

        assertThat(definition.getJsonSchemaType()).isEqualTo(jsonSchemaType);
        assertThat(definition.getJsonSchemaFormat()).isEqualTo(jsonSchemaFormat);
    }

    @Test
    void permittedOperatorsFollowTheJsonSchemaType() {
        Field status = field(path("urn:ont:status"), FieldType.STRING, FILTER_ONLY);

        FieldDefinition definition = FieldRegistries.of(scope(INVOICE, List.of(status)))
                .find("urn:ont:status")
                .orElseThrow();

        assertThat(definition.getPermittedOperators()).contains("is", "exists", "contains");
        assertThat(definition.getPermittedOperators()).doesNotContain("is greater than");
    }
}
