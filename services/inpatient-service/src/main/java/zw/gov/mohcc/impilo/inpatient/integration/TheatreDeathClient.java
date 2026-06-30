package zw.gov.mohcc.impilo.inpatient.integration;

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
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Routes a death-in-theatre to the canonical PCT Death &amp; Post-Death Pathway (WS#8). Inpatient/theatre
 * NEVER owns the death case — it confirms the death with PCT ({@code POST /v1/death/confirm},
 * placeOfDeathContext=THEATRE) and stores the returned death-case ref on the episode. Best-effort:
 * a PCT outage does not lose the theatre-side safety record (the episode keeps the safety event with
 * routed_status=OWNER_UNAVAILABLE for reconciliation).
 */
@Service
public class TheatreDeathClient {

    private static final Logger log = LoggerFactory.getLogger(TheatreDeathClient.class);

    private final RestTemplate restTemplate;
    private final String pctBaseUrl;

    public TheatreDeathClient(RestTemplate inpatientRestTemplate,
                              @Value("${inpatient.integration.pct.base-url:http://localhost:8088}") String pctBaseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.pctBaseUrl = pctBaseUrl;
    }

    /**
     * Confirm a death that occurred in theatre and open/route the PCT death case.
     *
     * @return the PCT death-case id, or null if PCT was unavailable (caller records OWNER_UNAVAILABLE).
     */
    @SuppressWarnings("unchecked")
    public String confirmDeathInTheatre(String deceasedCpid, String encounterRef, OffsetDateTime deathDatetime,
                                        boolean resuscitationAttempted, String suspectedManner) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("deathDatetime", (deathDatetime != null ? deathDatetime : OffsetDateTime.now()).toString());
            payload.put("placeOfDeathContext", "THEATRE");
            payload.put("placeOfDeathLocation", "Operating theatre");
            payload.put("resuscitationAttempted", resuscitationAttempted);
            payload.put("deceasedCpid", deceasedCpid);
            payload.put("deceasedIdentityStatus", deceasedCpid != null ? "KNOWN" : "UNKNOWN");
            payload.put("suspectedManner", suspectedManner != null ? suspectedManner : "PENDING");
            payload.put("sourceContext", "FACILITY");
            payload.put("sourceRef", encounterRef);
            ResponseEntity<Map> resp = restTemplate.postForEntity(pctBaseUrl + "/v1/death/confirm",
                    new HttpEntity<>(payload, trustHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            Object data = body != null ? body.getOrDefault("data", body) : null;
            if (data instanceof Map<?, ?> m) {
                Object id = ((Map<String, Object>) m).get("caseId");
                if (id == null) id = ((Map<String, Object>) m).get("case_id");
                log.info("INPATIENT-THEATRE: routed death-in-theatre for {} → PCT death case {}", deceasedCpid, id);
                return id != null ? id.toString() : null;
            }
            return null;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: PCT death pathway unavailable for {} (best-effort): {}",
                    deceasedCpid, e.getMessage());
            return null;
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            if (ctx.purposeOfUse() != null) headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
        } catch (IllegalStateException ignored) {
            // best-effort
        }
        return headers;
    }
}
