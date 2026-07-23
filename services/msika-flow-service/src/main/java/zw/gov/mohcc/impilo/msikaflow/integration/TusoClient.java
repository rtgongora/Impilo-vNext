package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;

/**
 * TUSO premises-registry client — eligibility precondition 3 (§4.3) and
 * facility-legitimacy checks. Uses the VashandiClient {@code copyTrustHeaders}
 * idiom: the inbound request's trust headers are forwarded on every call so
 * TUSO's TrustContext filter never sees a null tenant (live-caught BUG-1:
 * a bare RestTemplate sent no headers → tuso tenant-isolation threw → the
 * precondition fail-closed UNVERIFIED for every vendor).
 *
 * <p>Graceful degradation is preserved: any failure returns {@code null} so
 * callers map the outage to UNVERIFIED — never a throw.</p>
 */
@Service
public class TusoClient {

    private static final Logger log = LoggerFactory.getLogger(TusoClient.class);

    /**
     * Reference tenant the TUSO facility-master rows live under. The estate
     * currently runs TWO "default tenant" UUIDs (default-tenant split, see
     * experience-bff PublicGatewayAnonymousDefaultsFilter and commit
     * b38bbca56): API callers use the golden …-4000-8000-…001 tenant while the
     * absorbed MFL facility master (1,778 rows) sits under this one. Like the
     * public find-care lane (which reads the master under this tenant) and the
     * daidzai SOS reference-fallback, the status-summary read falls back to
     * this reference tenant when the caller-tenant read fails isolation. The
     * summary is non-PII operational status (same disclosure class as public
     * find-care), so the fallback does not widen disclosure.
     */
    static final String TUSO_REFERENCE_TENANT = "00000000-0000-0000-0000-000000000001";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public TusoClient(ObjectMapper objectMapper,
                      @Value("${msika-flow.integration.tuso-url:http://localhost:8084}") String tusoBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(tusoBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /** Resolves the current request thread's inbound request for trust-header propagation. */
    public JsonNode getFacilityStatusSummary(long facilityId) {
        return getFacilityStatusSummary(facilityId, currentRequest());
    }

    /**
     * GET /v1/internal/facilities/{id}/status-summary with the inbound trust
     * headers forwarded; on failure retries once under the facility-master
     * reference tenant (default-tenant split). Returns {@code null} on outage
     * so callers degrade to UNVERIFIED (fail closed, never a throw).
     */
    public JsonNode getFacilityStatusSummary(long facilityId, HttpServletRequest inbound) {
        JsonNode summary = fetchStatusSummary(facilityId, inbound, null);
        if (summary != null) {
            return summary;
        }
        return fetchStatusSummary(facilityId, inbound, TUSO_REFERENCE_TENANT);
    }

    private JsonNode fetchStatusSummary(long facilityId, HttpServletRequest inbound, String tenantOverride) {
        try {
            ResponseEntity<String> response = restClient.get()
                    .uri("/v1/internal/facilities/" + facilityId + "/status-summary")
                    .headers(h -> {
                        copyTrustHeaders(inbound, h);
                        if (tenantOverride != null) {
                            h.set(TrustHeaderExtractor.H_TENANT_ID, tenantOverride);
                        }
                    })
                    .retrieve()
                    .toEntity(String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("TUSO status-summary failed for facility {}{}: HTTP {}", facilityId,
                        tenantOverride != null ? " (reference tenant)" : "", response.getStatusCode());
                return null;
            }
            return objectMapper.readTree(response.getBody()).path("data");
        } catch (Exception e) {
            log.warn("TUSO status-summary failed for facility {}{}: {}", facilityId,
                    tenantOverride != null ? " (reference tenant)" : "", e.getMessage());
            return null;
        }
    }

    static HttpServletRequest currentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    static void copyTrustHeaders(HttpServletRequest inbound, HttpHeaders target) {
        if (inbound != null) {
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_TENANT_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_ACTOR_TYPE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_PURPOSE_OF_USE);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_CORRELATION_ID);
            copyIfPresent(inbound, target, TrustHeaderExtractor.H_FACILITY_ID);
            copyIfPresent(inbound, target, "Authorization");
        }
        String pod = inbound != null ? inbound.getHeader("X-Pod-ID") : null;
        target.add("X-Pod-ID", pod != null && !pod.isBlank() ? pod : "national-spine");
        target.add("X-Request-ID", java.util.UUID.randomUUID().toString());
        target.add("x-envoy-internal", "true");
    }

    private static void copyIfPresent(HttpServletRequest inbound, HttpHeaders target, String name) {
        String v = inbound.getHeader(name);
        if (v != null && !v.isBlank()) {
            target.add(name, v);
        }
    }
}
