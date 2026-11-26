package org.sequeless.query.api;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;

/**
 * One stored object as the Query side returns it: its identity, type, tenant, version, state,
 * property values, audit record and soft-delete flag.
 *
 * @param id the object's identity; never {@code null}
 * @param type the class the object is an instance of; never {@code null}
 * @param tenant the tenant the object belongs to; never {@code null}
 * @param version the object's version, at least 1
 * @param state the object's lifecycle state, when it has one; never {@code null} as a wrapper, use
 *     {@link Optional#empty()} when there is none
 * @param properties the property values keyed by property IRI; never {@code null}, copied
 * @param audit who created and last changed the object; never {@code null}
 * @param deleted whether the object is soft-deleted
 */
public record ObjectView(
        ObjectId id,
        TypeRef type,
        TenantId tenant,
        long version,
        Optional<String> state,
        Map<PropertyRef, Value> properties,
        Audit audit,
        boolean deleted) {

    /**
     * Validates every reference component is present, that {@code version} is at least 1, and
     * copies {@code properties}.
     *
     * @throws IllegalArgumentException if {@code version} is less than 1
     */
    public ObjectView {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(tenant, "tenant must not be null");
        if (version < 1) {
            throw new IllegalArgumentException("ObjectView version must be at least 1");
        }
        Objects.requireNonNull(state, "state must not be null (use Optional.empty())");
        properties = Map.copyOf(properties); // null-checks the map and every key and value
        Objects.requireNonNull(audit, "audit must not be null");
    }
}
