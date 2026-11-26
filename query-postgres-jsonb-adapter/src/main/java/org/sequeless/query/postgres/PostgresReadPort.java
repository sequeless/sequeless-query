package org.sequeless.query.postgres;

import java.util.Objects;
import java.util.Optional;
import org.sequeless.kernel.identity.Scope;
import org.sequeless.query.api.ObjectView;
import org.sequeless.query.api.ReadRequest;
import org.sequeless.query.spi.ReadPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The PostgreSQL {@link ReadPort} over {@code sq_object} (spec 14, sections 6.1 and 6.4).
 *
 * <p>A read is one {@code SELECT} of the row whose {@code id} is the request's, under two further conditions: the tenant
 * is the scope's ({@code tenant_id = ?}), and the object is not soft-deleted ({@code deleted_at IS NULL}). The port takes
 * no includeDeleted flag, so a soft-deleted object is never returned. The id and the tenant are bound parameters, never
 * text taken from the request. An unknown id, a soft-deleted object and an object of another tenant all give {@link
 * Optional#empty()}. A live row is mapped by {@link ObjectViewRowMapper}, the mapping the browse port uses, so both ports
 * return the same {@link ObjectView} for one object.
 *
 * <p>The read runs in its own read-only transaction, {@code PROPAGATION_REQUIRES_NEW}, as the browse does: a caller's open
 * transaction is suspended, and the read sees committed state only. The isolation level is the database default (READ
 * COMMITTED). One statement reads one row, so the REPEATABLE READ snapshot the browse needs for its total adds nothing
 * here. The read never writes.
 */
public final class PostgresReadPort implements ReadPort {

    private static final String READ_SQL = "SELECT " + ObjectViewRowMapper.COLUMNS
            + " FROM sq_object WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

    private final JdbcClient jdbcClient;
    private final TransactionTemplate transactionTemplate;

    /**
     * @param jdbcClient the client that runs the statement; must not be {@code null}
     * @param transactionManager the manager of the read-only transaction the statement runs in; must not be {@code null},
     *     and must manage the same DataSource as {@code jdbcClient}
     */
    public PostgresReadPort(JdbcClient jdbcClient, PlatformTransactionManager transactionManager) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient must not be null");
        Objects.requireNonNull(transactionManager, "transactionManager must not be null");
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplate.setReadOnly(true);
    }

    @Override
    public Optional<ObjectView> read(Scope scope, ReadRequest request) {
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(request, "request must not be null");
        return transactionTemplate.execute(status -> jdbcClient
                .sql(READ_SQL)
                .params(request.id().value(), scope.tenantId().value())
                .query(ObjectViewRowMapper::toView)
                .optional());
    }
}
