package org.sequeless.query.testkit;

import org.sequeless.query.api.ObjectView;

/**
 * The seeding hook a contract uses to put objects into the storage an adapter's query ports read.
 * The Query side has no write port, so the contracts seed the stored form of an object directly.
 */
public interface ObjectSeeder {

    /**
     * Makes {@code view} the stored state of its id. A later call with the same id replaces the
     * earlier view, as an update does, and a view with {@link ObjectView#deleted()} set is stored as
     * a soft-deleted object.
     *
     * @param view the object as it is stored; must not be {@code null}
     * @throws NullPointerException if {@code view} is {@code null}
     */
    void seed(ObjectView view);
}
