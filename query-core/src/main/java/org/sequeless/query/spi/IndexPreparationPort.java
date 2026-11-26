package org.sequeless.query.spi;

import java.util.List;
import java.util.Map;
import org.sequeless.kernel.identity.TenantId;
import org.sequeless.query.api.IndexPreparationResult;
import org.sequeless.query.field.api.ClassRef;
import org.sequeless.query.field.api.Field;
import org.sequeless.query.field.api.OntologyVersionRef;

/**
 * The outbound port through which the host prepares whatever index structures an implementation
 * needs in order to answer browse and read requests efficiently. It is a driven port: the host
 * calls it after every ontology activation (import, reload and startup), and adapters implement it.
 *
 * <p>This javadoc is the full behavioural contract every implementation must satisfy. The
 * query-testkit's {@code IndexPreparationContract} asserts the clauses that the port exposes: null
 * handling, the result (the newly registered searchable Fields, one per distinct search key, sorted
 * by that key), idempotence, immutability, the version not being checked against the Fields, and
 * per-tenant registration. It does not assert index structures or derived state. Each adapter's own
 * tests assert its index DDL and its completion.
 *
 * <ul>
 *   <li><b>Null handling.</b> Every argument must be non-{@code null}. A conforming implementation
 *       throws {@link NullPointerException} when any argument, including {@code version}, or any
 *       key or value of {@code rootFields}, is {@code null}.
 *   <li><b>Version is not assumed to match the Fields.</b> The {@code version} argument is supplied
 *       by the caller and is not checked against {@code rootFields}. An implementation must not
 *       assume that {@code version} is the version the Fields were resolved under; it may use the
 *       Fields alone to decide what to prepare.
 *   <li><b>Only capabilities decide what is prepared.</b> The {@code Field} lists are the resolved
 *       Fields of each root class. Of each Field's capabilities, only {@code facetable}, {@code
 *       sortable} and {@code searchable} matter here: a Field with {@code facetable} or {@code
 *       sortable} gets an expression index, whether it is a direct property or one reached through
 *       a relationship; a Field with {@code searchable} is registered for full-text search; a Field
 *       that is {@code filterable} alone gets no index.
 *   <li><b>{@link #prepare} is idempotent.</b> The host calls it after every ontology activation,
 *       so it is called repeatedly with the same version. A repeat call with the same version must
 *       not fail, error, or accumulate duplicate index structures, and returns an empty {@code
 *       newlyRegistered}.
 *   <li><b>The result lists the newly added search registrations, in key order.</b> {@code
 *       newlyRegistered} holds one {@code FieldPath} per distinct search key that this call newly
 *       registered: a searchable Field only, whatever its type. Expression-index registrations are not
 *       listed. The key is the segment property IRIs joined by {@code |}. The
 *       entries are sorted by that key in {@link String#compareTo} order. Two root classes that reach
 *       the same key give one entry, whatever the order of {@code rootFields}.
 *   <li><b>The result is immutable.</b> The caller cannot change the list in {@code newlyRegistered}.
 *   <li><b>{@link #prepare} is a no-op for Fields with no indexing needs.</b> A {@code rootFields}
 *       map whose Fields are none of {@code facetable}, {@code sortable} or {@code searchable}, or
 *       an empty map, must not cause {@link #prepare} to fail; it simply has nothing to prepare.
 * </ul>
 */
public interface IndexPreparationPort {

    /**
     * Prepares whatever index structures this implementation needs for the Fields of every root
     * class, under the tenant and version given. See the interface-level javadoc for the full
     * contract this method must satisfy.
     *
     * <p>Preparing a searchable Field registers it for the tenant, but does not change the objects
     * already stored. The caller learns from the returned {@link IndexPreparationResult} which
     * searchable Fields were newly registered, and recomputes the full-text data of existing
     * objects when that list is not empty. Returns once the preparation is done or durably
     * accepted.
     *
     * @param tenant the tenant whose indexes are prepared; must not be {@code null}
     * @param version the ontology version the caller is activating; must not be {@code null}; not
     *     checked against {@code rootFields}
     * @param rootFields the resolved Fields of every root class, one entry per class, in the order
     *     {@code FieldSource.rootClasses} returned them; must not be {@code null}
     * @return the searchable Fields newly registered for {@code tenant} by this call, in
     *     deterministic order; empty on every repeat call with the same version; never {@code null}
     * @throws NullPointerException if any argument, or any key or value of {@code rootFields}, is
     *     {@code null}
     */
    IndexPreparationResult prepare(TenantId tenant, OntologyVersionRef version, Map<ClassRef, List<Field>> rootFields);
}
