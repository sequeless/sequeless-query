/**
 * Spring Boot autoconfiguration of the Query side: the Field source over the host's
 * {@code SchemaSource}, the Postgres ports over the host's {@code DataSource}, and the query service. Every bean
 * backs off when the host defines a bean of its type.
 *
 * <p>{@link QueryAutoConfiguration} registers nothing unless the host defines both a
 * {@code SchemaSource} bean and a {@code DataSource} bean. {@link QueryServiceAutoConfiguration}
 * registers the query service only when the {@code SchemaSource}, the Field source and the three
 * ports all exist.
 */
package org.sequeless.query.autoconfig;
