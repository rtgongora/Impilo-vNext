package zw.gov.mohcc.impilo.surgery.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reads the specimen projection for a linked {@code inpatient.procedure_episode} — the input to
 * the §16 histology closure gate. inpatient-service's {@code procedure_specimen} status
 * vocabulary (V022): PARSED | ORDERED | COLLECTED | DISPATCHED | RECEIVED | RESULTED | CRITICAL |
 * ACKNOWLEDGED | REJECTED. Anything not ACKNOWLEDGED or REJECTED is histology still in flight or
 * a result nobody has reviewed — either way, unreviewed.
 *
 * <p><b>FAIL-SAFE, deliberately opposite to this service's other clients.</b>
 * {@code PctProblemContributionClient} fails OPEN (an outage must not block recording a fact).
 * This client fails CLOSED: it answers a GATE question ("may this episode close?"), and the
 * pipeline's own NFR1 says a service on a gate path blocks when it cannot answer — an
 * unreachable specimen list is not an empty specimen list. {@link SpecimenGateView#UNKNOWN}
 * makes the third state explicit so the caller cannot collapse unknown into clear.</p>
 */
@Service
public class InpatientSpecimenClient {

    private static final Logger log = LoggerFactory.getLogger(InpatientSpecimenClient.class);

    /** Terminal, reviewed states — everything else blocks closure. */
    private static final List<String> REVIEWED_STATES = List.of("ACKNOWLEDGED", "REJECTED");

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InpatientSpecimenClient(RestTemplate surgeryRestTemplate,
                                   @Value("${surgery.integration.inpatient.base-url:http://localhost:8121}") String baseUrl) {
        this.restTemplate = surgeryRestTemplate;
        this.baseUrl = baseUrl;
    }

    public record SpecimenGateView(boolean known, int total, int unreviewed) {
        public static final SpecimenGateView UNKNOWN = new SpecimenGateView(false, 0, 0);
        public boolean allReviewed() { return known && unreviewed == 0; }
    }

    /** Counts unreviewed specimens for the linked procedure episode. UNKNOWN on any failure. */
    @SuppressWarnings("unchecked")
    public SpecimenGateView specimenGate(UUID procedureEpisodeRef) {
        try {
            ResponseEntity<List> resp = restTemplate.exchange(
                    baseUrl + "/internal/v1/theatre/cases/" + procedureEpisodeRef + "/specimens",
                    HttpMethod.GET, new HttpEntity<>(trustHeaders()), List.class);
            List<Map<String, Object>> rows = resp.getBody();
            if (rows == null) {
                return SpecimenGateView.UNKNOWN;
            }
            int unreviewed = 0;
            for (Map<String, Object> row : rows) {
                Object status = row.get("status");
                if (status == null || !REVIEWED_STATES.contains(status.toString())) {
                    unreviewed++;
                }
            }
            return new SpecimenGateView(true, rows.size(), unreviewed);
        } catch (RestClientException | IllegalStateException e) {
            log.warn("SURGERY: specimen gate query unavailable — closure will be BLOCKED (fail-safe): {}",
                    e.getMessage());
            return SpecimenGateView.UNKNOWN;
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "surgery");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_ACTOR_TYPE, ctx.actorType() != null ? ctx.actorType() : "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID,
                    ctx.correlationId() != null ? ctx.correlationId().toString() : UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse() != null ? ctx.purposeOfUse() : "TREATMENT");
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_ACTOR_TYPE, "PROVIDER");
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            headers.set(TrustContext.H_PURPOSE_OF_USE, "TREATMENT");
        }
        return headers;
    }
}
