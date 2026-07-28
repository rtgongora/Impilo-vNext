package zw.gov.mohcc.impilo.mentalhealth.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Writes this service's referral decision back onto pct's {@code emergency_handover} — the other
 * half of the acceptance handshake. Mirrors {@code pct-service}'s {@code DaidzaiEpisodeClient}
 * shape exactly (same trust-header synthesis, same "log and swallow" failure discipline for a call
 * a clinical write must not roll back on) for consistency across the pack's service-to-service
 * clients.
 */
@Service
public class PctHandoverClient {

    private static final Logger log = LoggerFactory.getLogger(PctHandoverClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PctHandoverClient(RestTemplate restTemplate,
                             @Value("${impilo.services.pct-base-url:http://localhost:8088}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Accept pct's handover, naming this referral as the accepting record. Returns {@code true} on
     * success; {@code false} if pct is unreachable or refuses (e.g. the handover already resolved) —
     * the caller decides whether that blocks the local acceptance or proceeds and flags a divergence
     * for reconciliation, matching this pack's own "peer outage never blocks a local clinical write"
     * doctrine.
     */
    public boolean acceptHandover(UUID tenantId, UUID handoverId, UUID referralId, String acceptedBy) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("acceptedBy", acceptedBy);
        body.put("acceptingRef", referralId.toString());
        body.put("acceptingService", "mental-health-service");
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/v1/emergency/handovers/" + handoverId + "/accept",
                    HttpMethod.POST, new HttpEntity<>(body, headers(tenantId)), Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("PCT accept-handover for handover {} (referral {}) failed: {} — the local "
                    + "referral is still recorded as ACCEPTED; the handover's own status will diverge "
                    + "until reconciled", handoverId, referralId, e.getMessage());
            return false;
        }
    }

    /** Decline pct's handover. Same failure discipline as {@link #acceptHandover}. */
    public boolean declineHandover(UUID tenantId, UUID handoverId, String declinedBy, String reason) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("declinedBy", declinedBy);
        body.put("reason", reason);
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    baseUrl + "/v1/emergency/handovers/" + handoverId + "/decline",
                    HttpMethod.POST, new HttpEntity<>(body, headers(tenantId)), Map.class);
            return resp.getStatusCode().is2xxSuccessful();
        } catch (RestClientException e) {
            log.warn("PCT decline-handover for handover {} failed: {} — the local referral is still "
                    + "recorded as DECLINED; the handover's own status will diverge until reconciled",
                    handoverId, e.getMessage());
            return false;
        }
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "mental-health-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        h.set("Idempotency-Key", "mh-handover-" + UUID.randomUUID());
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }
}
