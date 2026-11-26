/**
 * The published Field contract: the vocabulary the schema side and {@code sequeless-filter} agree on
 * for "what can be filtered". It exists as its own package in its own module, whose only compile
 * dependency is kernel-identity, so that neither side appears on the other's dependency graph. The
 * schema side implements the ports, filter consumes the model, and nothing is inherited by either
 * side.
 *
 * <p>Everything here is an immutable record or a closed enum, with no behavior beyond derived
 * accessors, validation, and equality. All types here must be public.
 */
package org.sequeless.query.field.api;
