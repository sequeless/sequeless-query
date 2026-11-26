package org.sequeless.query.testkit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sequeless.kernel.values.ObjectId;
import org.sequeless.query.api.ObjectView;

/**
 * The in-memory storage the in-memory references read: one stored view per object id, in the order
 * each id was first seeded. It is the shared store behind {@link InMemoryBrowsePort} and {@link
 * InMemoryReadPort}, so a contract seeds once and both ports answer over the same objects.
 */
public final class InMemoryObjectStore implements ObjectSeeder {

    private final Map<ObjectId, ObjectView> views = new LinkedHashMap<>();

    /** Creates an empty store. */
    public InMemoryObjectStore() {}

    @Override
    public void seed(ObjectView view) {
        Objects.requireNonNull(view, "view must not be null");
        views.put(view.id(), view);
    }

    /**
     * Every stored view, soft-deleted ones included, in seeding order.
     *
     * @return an unmodifiable snapshot; never {@code null}
     */
    public List<ObjectView> all() {
        return List.copyOf(views.values());
    }
}
