package org.sequeless.query.testkit;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.kernel.values.Value;
import org.sequeless.query.api.Audit;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.PropertyRef;
import org.sequeless.query.api.TypeRef;

/**
 * Builds the stored form of a fixture object for {@link ObjectSeeder#seed}. Every object is created
 * and last changed by the same fixture author at the same instant, and has no lifecycle state.
 */
final class ObjectViews {

    private static final String AUTHOR = "query-testkit";

    private ObjectViews() {}

    /**
     * @param id the object's identity
     * @param type the class IRI the object is an instance of
     * @param tenant the tenant the object belongs to
     * @param version the object's version, at least 1
     * @param properties the property values keyed by property IRI
     * @param createdAt the creation and last-change instant
     * @param deleted whether the object is soft-deleted
     * @return the view a seeding call stores
     */
    static ObjectView of(
            ObjectId id,
            String type,
            TenantId tenant,
            long version,
            Map<PropertyRef, Value> properties,
            Instant createdAt,
            boolean deleted) {
        Audit audit = new Audit(createdAt, AUTHOR, createdAt, AUTHOR);
        return new ObjectView(id, new TypeRef(type), tenant, version, Optional.empty(), properties, audit, deleted);
    }
}
