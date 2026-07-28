package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to RITO (quality &amp; safety, 8391) — RITO is the SoR for the safety case; this
 * pack only holds a {@code rito_case_ref} LINK. Mirrors inpatient-theatre's own
 * {@code RitoSafetyClient} exactly (same endpoint, same payload keys, same contract) rather than
 * inventing a second shape for the same target service.
 *
 * <p>Takes {@code tenantId} explicitly rather than reading {@link
 * zw.gov.mohcc.impilo.shared.auth.TrustContextHolder}, unlike inpatient's version — the two callers in
 * this service are {@link zw.gov.mohcc.impilo.pct.core.EmergencyAlertSweepJob} and {@link
 * zw.gov.mohcc.impilo.pct.core.HandoverExpirySweepJob}, both scheduled jobs that run with no trust
 * context at all (an explicit system actor is stamped instead — see those classes' own docs), so an
 * ambient-context read would simply fail for the primary caller. Matches this service's own
 * {@link DaidzaiEpisodeClient}, which took the same approach for the same reason.
 *
 * <p>NEVER fabricates a case id: an outage returns {@code null} and the caller records the ref as
 * absent rather than inventing one — the exact discipline {@code inpatient.RitoSafetyClient} already
 * documents ("an outage returns null and the caller records OWNER_UNAVAILABLE").
 */
@Service
public class RitoSafetyClient {

    private static final Logger log = LoggerFactory.getLogger(RitoSafetyClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RitoSafetyClient(RestTemplate restTemplate,
                            @Value("${pct.integration.rito.base-url:http://localhost:8391}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /** Open a RITO case. Returns the RITO case id, or {@code null} if RITO was unreachable. */
    @SuppressWarnings("unchecked")
    public String createCase(UUID tenantId, String caseType, String title, String description,
                             String severity, String category, String subjectCpid,
                             Map<String, Object> metadata, String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("caseType", caseType);
            payload.put("title", title);
            payload.put("description", description);
            payload.put("severity", severity);
            // A service-to-service case is INTEGRATION_API — CaseClassification.SOURCES rejects
            // anything not in its closed vocabulary (inpatient's client hit this with "THEATRE").
            payload.put("source", "INTEGRATION_API");
            payload.put("category", category);
            if (subjectCpid != null) payload.put("subjectCpid", subjectCpid);
            payload.put("metadata", metadata != null ? metadata : Map.of());
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/rito/cases",
                    new HttpEntity<>(payload, headers(tenantId, idempotencyKey)), Map.class);
            Map<String, Object> body = resp.getBody();
            Object id = body != null ? firstNonNull(body.get("id"), body.get("caseId")) : null;
            if (id != null) log.info("PCT-EMERGENCY: opened RITO case {} ({})", id, caseType);
            return id != null ? id.toString() : null;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("PCT-EMERGENCY: RITO unavailable opening case ({}, best-effort): {}", caseType, e.getMessage());
            return null;
        }
    }

    private static Object firstNonNull(Object a, Object b) { return a != null ? a : b; }

    private HttpHeaders headers(UUID tenantId, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) headers.set("X-Tenant-ID", tenantId.toString());
        headers.set("X-Pod-ID", "national-spine");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("X-Correlation-ID", UUID.randomUUID().toString());
        headers.set("X-Actor-ID", "pct-service");
        headers.set("X-Actor-Type", "SERVICE");
        headers.set("X-Purpose-Of-Use", "TREATMENT");
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }
}
