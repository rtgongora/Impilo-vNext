package zw.gov.mohcc.impilo.governance.mirror;

import java.time.Duration;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.micrometer.core.instrument.MeterRegistry;
import zw.gov.mohcc.impilo.shared.auth.TrustHeaders;

/**
 * One-way HTTP producer that pushes a {@link OrgMirrorPayload} to the
 * organization-registry mirror receiver.
 *
 * <p>Resilient by construction: bounded connect/read timeouts, and callers
 * treat every failure as best-effort — a mirror error is logged + metered and
 * NEVER propagated in a way that could fail or roll back the primary
 * {@code wgv_organisation} write. The receiver is idempotent on the wgv id, so
 * re-pushing the same organisation is safe and re-runnable.
 */
@Component
public class OrgRegistryMirrorClient {

    private static final Logger log = LoggerFactory.getLogger(OrgRegistryMirrorClient.class);

    private final OrgMirrorProperties properties;
    private final MeterRegistry meterRegistry;
    private final RestClient restClient;

    public OrgRegistryMirrorClient(OrgMirrorProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofMillis(properties.getConnectTimeoutMs()).toMillis());
        factory.setReadTimeout((int) Duration.ofMillis(properties.getReadTimeoutMs()).toMillis());
        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    /**
     * Push one organisation to the mirror receiver.
     *
     * @return {@code true} on a 2xx response, {@code false} on any failure.
     *         Never throws — failures are swallowed, logged and metered so the
     *         caller (relay or backfill) is fully decoupled from mirror health.
     */
    public boolean push(OrgMirrorPayload payload, UUID tenantId, UUID correlationId) {
        try {
            restClient.post()
                    .uri(properties.getMirrorPath())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TrustHeaders.X_TENANT_ID, tenantId != null ? tenantId.toString() : "")
                    .header(TrustHeaders.X_CORRELATION_ID,
                            (correlationId != null ? correlationId : UUID.randomUUID()).toString())
                    // Mark internal so the receiver's TrustContextFilter resolves INTERNAL mode.
                    .header("X-Access-Mode", "INTERNAL")
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            meterRegistry.counter("impilo.org_mirror.push", "result", "success").increment();
            return true;
        } catch (Exception e) {
            meterRegistry.counter("impilo.org_mirror.push", "result", "failure").increment();
            log.warn("Org mirror push failed for organisation id={} (best-effort, primary write unaffected): {}",
                    payload != null ? payload.id() : null, e.toString());
            return false;
        }
    }
}
