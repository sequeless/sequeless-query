-- TEST ONLY. The sq_object part of prototype commit 48a0cc6, sequeless-adapter-persistence-postgres,
-- src/main/resources/db/migration/sequeless/postgres/V1__objects_ontology_outbox.sql, copied unchanged for the
-- table and its two indexes. The rest of that file (ontology documents and the outbox) is not needed here.
-- The Command side is not part of this module (spec 14, section 2.3, Q5). The ITs apply this file, then V6,
-- then the Query migrations. It must not be shipped as a migration of this module.

CREATE TABLE sq_object (
    id          uuid PRIMARY KEY,
    tenant_id   text NOT NULL,
    type_iri    text NOT NULL,
    version     bigint NOT NULL,
    state       text,
    props       jsonb NOT NULL,
    created_at  timestamptz NOT NULL,
    created_by  text NOT NULL,
    updated_at  timestamptz NOT NULL,
    updated_by  text NOT NULL,
    deleted_at  timestamptz
);

CREATE INDEX sq_object_props_gin_idx ON sq_object USING GIN (props jsonb_path_ops);

CREATE INDEX sq_object_tenant_type_created_idx
    ON sq_object (tenant_id, type_iri, created_at)
    WHERE deleted_at IS NULL;
