package org.sequeless.query.field.api;

/**
 * Whether a facet or relationship holds at most one value, or potentially several, on a given
 * record.
 *
 * <p>Exposed as its own enum rather than folded into {@link FieldType} or left implicit, because cardinality changes
 * matching semantics: a {@code MANY} facet matches on <em>any</em> of its values, and lets consumers render a
 * multi-select and offer explicit quantifier operators such as {@code has all of} / {@code has none of} where a {@code
 * SINGLE} facet would not.
 */
public enum Cardinality {

    /** At most one value per record. */
    SINGLE,

    /** Zero or more values per record. */
    MANY
}
