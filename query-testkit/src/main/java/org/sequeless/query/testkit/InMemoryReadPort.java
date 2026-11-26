package org.sequeless.query.testkit;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.spi.ReadPort;

/**
 * An in-memory {@link ReadPort}, the reference {@link ReadContract} is proven against. It answers a
 * read by scanning the store, and applies the tenant and soft-delete rules the port documents.
 */
public final class InMemoryReadPort implements ReadPort {

    private final InMemoryObjectStore store;

    /**
     * @param store the store to read; must not be {@code null}
     */
    public InMemoryReadPort(InMemoryObjectStore store) {
        this.store = Objects.requireNonNull(store, "store must not be null");
    }

    @Override
    public Optional<ObjectView> read(Scope scope, ReadRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return store.all().stream()
                .filter(view -> view.id().equals(request.id()))
                .filter(view -> view.tenant().equals(scope.tenantId()))
                .filter(view -> !view.deleted())
                .findFirst();
    }
}
