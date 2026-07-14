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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Best-effort client to RITO (quality &amp; safety, 8391). RITO is the SoR for the safety case and its
 * investigation — theatre creates a case, records the safety incident (e.g. RETAINED_ITEM sentinel), and
 * raises a quality-signal, then stores the returned rito_case_ref. Mirrors {@link OrosOrderClient}. It
 * NEVER fabricates a case id — an outage returns null and the caller records OWNER_UNAVAILABLE.
 */
@Service
public class RitoSafetyClient {

    private static final Logger log = LoggerFactory.getLogger(RitoSafetyClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public RitoSafetyClient(RestTemplate inpatientRestTemplate,
                            @Value("${inpatient.integration.rito.base-url:http://localhost:8391}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /** Open a RITO case for a theatre safety concern. Returns the RITO case id, or null if RITO was down. */
    @SuppressWarnings("unchecked")
    public String createCase(String caseType, String title, String description, String severity,
                             String category, String subjectHealthId, Map<String, Object> metadata,
                             String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("caseType", caseType);
            payload.put("title", title);
            payload.put("description", description);
            payload.put("severity", severity);
            // RITO validates source against its enum (NOMPILO|KHULUMA|...|INTEGRATION_API); a theatre
            // service-to-service case is INTEGRATION_API. "THEATRE" was rejected 400 so no case opened.
            payload.put("source", "INTEGRATION_API");
            payload.put("category", category);
            if (subjectHealthId != null) payload.put("subjectHealthId", subjectHealthId);
            payload.put("metadata", metadata != null ? metadata : Map.of());
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/rito/cases",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            Map<String, Object> body = resp.getBody();
            Object id = body != null ? firstNonNull(body.get("id"), body.get("caseId")) : null;
            if (id != null) log.info("INPATIENT-THEATRE: opened RITO case {} ({})", id, caseType);
            return id != null ? id.toString() : null;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: RITO unavailable opening case ({}, best-effort): {}", caseType, e.getMessage());
            return null;
        }
    }

    /** Record a safety incident (e.g. RETAINED_ITEM) on a RITO case. */
    public boolean recordSafetyIncident(String caseId, String incidentType, String harmLevel,
                                        String immediateActions, boolean sentinel, String idempotencyKey) {
        if (caseId == null) return false;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("incident_type", incidentType);
            payload.put("harm_level", harmLevel);
            payload.put("immediate_actions", immediateActions);
            payload.put("sentinel", sentinel);
            restTemplate.postForEntity(baseUrl + "/internal/v1/rito/cases/" + caseId + "/safety-incident",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: RITO safety-incident unavailable for case {} (best-effort): {}",
                    caseId, e.getMessage());
            return false;
        }
    }

    /** Raise a quality signal (e.g. CHECKLIST_NONCOMPLIANCE) into RITO's quality pipeline. */
    public boolean ingestQualitySignal(String signalType, String sourceRef, String severity,
                                       String facilityRef, String summary, Map<String, Object> detail,
                                       String idempotencyKey) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("signal_type", signalType);
            payload.put("source_system", "inpatient-theatre");
            payload.put("source_ref", sourceRef);
            payload.put("severity", severity);
            if (facilityRef != null) payload.put("facility_id", facilityRef);
            payload.put("summary", summary);
            if (detail != null) payload.put("detail", detail);
            restTemplate.postForEntity(baseUrl + "/internal/v1/rito/quality-signals",
                    new HttpEntity<>(payload, trustHeaders(idempotencyKey)), Map.class);
            return true;
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: RITO quality-signal unavailable ({}, best-effort): {}",
                    signalType, e.getMessage());
            return false;
        }
    }

    private static Object firstNonNull(Object a, Object b) { return a != null ? a : b; }

    private HttpHeaders trustHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "inpatient");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
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
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            headers.set(TrustContext.H_ACTOR_TYPE, "PROVIDER");
            headers.set(TrustContext.H_PURPOSE_OF_USE, "TREATMENT");
        }
        return headers;
    }
}
