/**
 * Spring Boot autoconfiguration of the Query side (spec 14, section 5): the Field source over the host's
 * {@code SchemaSource}, the Postgres ports over the host's {@code DataSource}, and the query service. Every bean
 * backs off when the host defines a bean of its type.
 */
package org.sequeless.query.autoconfig;
