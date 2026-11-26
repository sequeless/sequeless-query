/**
 * The driven ports a schema-backed implementation provides: {@code FieldSource}, which answers
 * what may be filtered for a given tenant, root class, and version policy, and
 * {@code ConceptSource}, which serves concept scheme values. They are separate ports because
 * concept schemes publish on their own clock — bundling concept values into a field response
 * would mean caching a taxonomy snapshot and serving it stale. See
 * {@code docs/specs/field-contract.md} section 3.
 *
 * <p>All types here must be public.
 */
package org.sequeless.query.field.spi;
