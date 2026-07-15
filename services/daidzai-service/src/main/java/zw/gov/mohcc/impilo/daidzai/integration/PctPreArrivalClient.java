package zw.gov.mohcc.impilo.daidzai.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Pushes an EMS ePCR pre-arrival snapshot to PCT so the destination ED sees the incoming patient's
 * prehospital obs BEFORE the ambulance arrives (architecture decision #2 — the ePCR reaches ED).
 * DAIDZAI owns the ePCR system of record; PCT owns the ED pre-arrival projection on {@code ed_visit}
 * and materialises it from this push. Best-effort — a degraded ED never blocks or rolls back the
 * responder's ePCR capture; the obs remain safely in {@code dai_ems_epcr_event}.
 */
@Component
public class PctPreArrivalClient {

    private static final Logger log = LoggerFactory.getLogger(PctPreArrivalClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public PctPreArrivalClient(
            @Value("${daidzai.pct.base-url:http://pct-service:8088}") String baseUrl,
            @Value("${daidzai.pct.timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * On EMS handover, transition the PRE_ARRIVAL ed_visit to an active ED visit (PCT reuses the
     * same row — no duplicate). Never throws. Body carries traumaEpisodeId + emsMissionRef +
     * pctEncounterRef + facilityId.
     */
    public void pushArrival(UUID tenantId, Map<String, Object> body) {
        post("/v1/ed/arrival", tenantId, body);
    }

    /**
     * Upsert the ED pre-arrival projection for a trauma episode. Idempotent on the PCT side
     * (keyed by trauma_episode_id). Never throws.
     */
    public void pushPreArrival(UUID tenantId, Map<String, Object> body) {
        post("/v1/ed/pre-arrival", tenantId, body);
    }

    private void post(String path, UUID tenantId, Map<String, Object> body) {
        try {
            restClient.post()
                    .uri(baseUrl + path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set("X-Tenant-ID", tenantId.toString());
                        h.set("X-Pod-ID", "national-spine");
                        h.set("X-Request-ID", UUID.randomUUID().toString());
                        h.set("X-Correlation-ID", UUID.randomUUID().toString());
                        h.set("X-Actor-ID", "daidzai-ems");
                        h.set("X-Actor-Type", "SERVICE");
                        h.set("X-Purpose-Of-Use", "TREATMENT");
                        h.set("Idempotency-Key", "daidzai-prearrival-" + UUID.randomUUID());
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException e) {
            log.warn("PCT pre-arrival push failed (episode {}): {} — ePCR obs stay in daidzai, projection is eventual",
                    body.get("traumaEpisodeId"), e.getMessage());
        }
    }
}
