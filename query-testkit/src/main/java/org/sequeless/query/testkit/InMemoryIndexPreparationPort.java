package org.sequeless.query.testkit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.FieldPath;
import org.sequeless.query.field.api.OntologyVersionRef;
import org.sequeless.query.field.api.PathSegment;
import org.sequeless.query.spi.IndexPreparationPort;

/**
 * An in-memory {@link IndexPreparationPort}, the reference {@link IndexPreparationContract} is proven
 * against. It records the searchable Fields registered for each tenant, synchronously, and reports a
 * Field as newly registered only the first time the tenant is prepared with it.
 *
 * <p>Registrations are keyed by the Field's registration key. The result has
 * one entry per distinct key, sorted by that key. When two Fields share a key but not a path, the
 * one first by slug path, then by id path, is reported, so the result does not depend on the order
 * of {@code rootFields}.
 *
 * <p>It builds no index structures, so only the registrations are modelled: a Field that is
 * {@code facetable} or {@code sortable} gets nothing here. The version is accepted and not used,
 * as the port documents.
 */
public final class InMemoryIndexPreparationPort implements IndexPreparationPort {

    private final Map<TenantId, Set<String>> registered = new HashMap<>();

    /** Creates a port with nothing registered for any tenant. */
    public InMemoryIndexPreparationPort() {}

    @Override
    public IndexPreparationResult prepare(
            TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields) {
        Objects.requireNonNull(tenant, "tenant must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(rootFields, "rootFields must not be null");

        // Keyed by the registry key and sorted by it, so the result order is fixed by the Fields alone.
        Map<String, FieldPath> searchable = new TreeMap<>();
        for (Map.Entry<ClassRef, List<Field>> root : rootFields.entrySet()) {
            Objects.requireNonNull(root.getKey(), "root class must not be null");
            Objects.requireNonNull(root.getValue(), "root fields must not be null");
            for (Field field : root.getValue()) {
                Objects.requireNonNull(field, "field must not be null");
                if (field.capabilities().searchable()) {
                    searchable.merge(keyOf(field.path()), field.path(), InMemoryIndexPreparationPort::firstByPath);
                }
            }
        }

        Set<String> alreadyRegistered = registered.computeIfAbsent(tenant, ignored -> new HashSet<>());
        List<FieldPath> newlyRegistered = new ArrayList<>();
        for (Map.Entry<String, FieldPath> entry : searchable.entrySet()) {
            if (alreadyRegistered.add(entry.getKey())) {
                newlyRegistered.add(entry.getValue());
            }
        }
        return new IndexPreparationResult(newlyRegistered);
    }

    /**
     * Returns at once: this port registers synchronously, so there is nothing to wait for. It is the
     * {@code awaitPrepared} hook of the in-memory environment.
     */
    public void awaitPrepared() {}

    /** The first of two paths that share a key, by slug path and then by id path. */
    private static FieldPath firstByPath(FieldPath left, FieldPath right) {
        Comparator<FieldPath> order = Comparator.comparing(FieldPath::asSlugPath)
                .thenComparing(path -> path.asIdPath().toString());
        return order.compare(left, right) <= 0 ? left : right;
    }

    /** The segment IRIs joined by {@code |}, the FieldRegistry key. */
    private static String keyOf(FieldPath path) {
        return path.segments().stream().map(PathSegment::iri).collect(Collectors.joining("|"));
    }
}
