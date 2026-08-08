package zw.gov.mohcc.impilo.fhirgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRepository;
import zw.gov.mohcc.impilo.companion.idempotency.JdbcIdempotencyRepository;

/**
 * FHIR-gateway Tech Companion configuration — the schema-prefixed idempotency repository.
 *
 * <p>{@code TechCompanionAutoConfiguration} already registers {@code IdempotencyFilter}, which
 * enforces {@code Idempotency-Key} on POST/PUT/PATCH to {@code /internal/v1/**} — that includes
 * {@code /internal/v1/gateway/forward} — caches the response and replays it on a retry. The
 * mechanism was never missing. Its <b>table name</b> was wrong here.</p>
 *
 * <p>The autoconfig defaults to the unqualified {@code "idempotency_keys"}. This service creates
 * the table in schema {@code fhir_gateway} ({@code V001__init.sql}) and sets
 * {@code default_schema: fhir_gateway} — but that setting is Hibernate's, and
 * {@link JdbcIdempotencyRepository} uses raw {@link JdbcTemplate}. Measured against the live
 * database on 2026-08-08:</p>
 *
 * <pre>
 *   information_schema.tables → fhir_gateway | idempotency_keys   (only there)
 *   SHOW search_path          → "$user", public
 *   SELECT … FROM idempotency_keys
 *       → ERROR: relation "idempotency_keys" does not exist
 * </pre>
 *
 * <p>So every idempotency read and write this gateway attempted resolved against a table that does
 * not exist on its search path. A retried forward could not be recognised as a retry, and
 * telemonitoring — which has always sent an {@code Idempotency-Key} — was getting no protection
 * from it. With P5 about to route four more services through this endpoint, a duplicate clinical
 * resource per retry is the failure that would follow.</p>
 *
 * <p>tshepo, tuso, varapi and vito each already carry this same class for the same reason. The
 * autoconfig's own javadoc names the extension point: "Services can override any bean (e.g. supply
 * their own schema-prefixed IdempotencyRepository)." This is that override, and nothing more —
 * filters, the exception handler and service wiring stay with the autoconfiguration.</p>
 */
@Configuration
public class CompanionV11Config {

    @Bean
    public IdempotencyRepository companionIdempotencyRepository(JdbcTemplate jdbc) {
        return new JdbcIdempotencyRepository(jdbc, "fhir_gateway.idempotency_keys", 24);
    }
}
