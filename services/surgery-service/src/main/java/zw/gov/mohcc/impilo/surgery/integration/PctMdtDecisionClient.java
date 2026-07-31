package zw.gov.mohcc.impilo.surgery.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * Resolves a multidisciplinary-team decision that lives in PCT (surgical demonstration 3, V012).
 * READ ONLY. Surgery references the board record; it never owns one.
 *
 * <p>PCT has two MDT systems of record and their consolidation is a cross-lane decision this
 * programme is not authorised to make, so both are addressed here and the caller says which:</p>
 * <ul>
 *   <li>{@code PCT_MDT_CASE_ITEM} — the telemedicine board lane (pct V051), read through
 *       {@code GET /v1/mdt/sessions/{id}}'s case items.</li>
 *   <li>{@code PCT_MDT_DECISION} — the Adult Medicine lane (pct V114), read through
 *       {@code GET /v1/consultations/mdt/{decisionId}}.</li>
 * </ul>
 *
 * <p><b>Three outcomes, deliberately distinct.</b> Found, definitively absent, and unknown are
 * three different answers and collapsing the third into either of the others is how a service
 * starts lying. A 404 from PCT means the reference is wrong and the caller may refuse it. An
 * outage means nobody knows, so the caller records the decision unverified rather than either
 * blocking a surgical decision on a PCT outage or claiming a confirmation it never received.</p>
 */
@Service
public class PctMdtDecisionClient {

    private static final Logger log = LoggerFactory.getLogger(PctMdtDecisionClient.class);

    public static final String SOURCE_MDT_DECISION = "PCT_MDT_DECISION";
    public static final String SOURCE_MDT_CASE_ITEM = "PCT_MDT_CASE_ITEM";

    /**
     * @param known   false when PCT could not be reached at all — not an assertion of absence
     * @param present true when PCT resolved the reference
     */
    public record MdtLookup(boolean known, boolean present, Map<String, Object> decision) {
        public static MdtLookup unknown() { return new MdtLookup(false, false, null); }
        public static MdtLookup absent() { return new MdtLookup(true, false, null); }
        public static MdtLookup found(Map<String, Object> d) { return new MdtLookup(true, true, d); }
    }

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PctMdtDecisionClient(RestTemplate surgeryRestTemplate,
                                @Value("${surgery.integration.pct.base-url:http://localhost:8088}") String baseUrl) {
        this.restTemplate = surgeryRestTemplate;
        this.baseUrl = baseUrl;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public MdtLookup lookup(String source, UUID reference) {
        if (reference == null || source == null) {
            return MdtLookup.absent();
        }
        String path = switch (source) {
            case SOURCE_MDT_DECISION -> "/v1/consultations/mdt/" + reference;
            case SOURCE_MDT_CASE_ITEM -> "/v1/mdt/sessions/" + reference;
            default -> null;
        };
        if (path == null) {
            return MdtLookup.absent();
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(baseUrl + path,
                    org.springframework.http.HttpMethod.GET, new HttpEntity<>(trustHeaders()), Map.class);
            Map<String, Object> body = resp.getBody();
            if (body == null) {
                return MdtLookup.absent();
            }
            Object data = body.get("data");
            Map<String, Object> decision = data instanceof Map<?, ?> m ? (Map<String, Object>) m : body;
            return decision.isEmpty() ? MdtLookup.absent() : MdtLookup.found(decision);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            if (status.value() == 404) {
                // PCT answered, and the answer is that no such board record exists.
                return MdtLookup.absent();
            }
            log.warn("SURGERY: MDT lookup {} {} refused by PCT ({}) — recorded as unverified",
                    source, reference, status);
            return MdtLookup.unknown();
        } catch (RestClientException e) {
            log.warn("SURGERY: MDT lookup {} {} unavailable: {} — recorded as unverified, not as absent",
                    source, reference, e.getMessage());
            return MdtLookup.unknown();
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
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId() != null
                    ? ctx.correlationId().toString() : UUID.randomUUID().toString());
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
