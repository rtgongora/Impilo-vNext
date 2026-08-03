package zw.gov.mohcc.impilo.vashandi.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
public class TusoIntegrationClient {

    private static final Logger log = LoggerFactory.getLogger(TusoIntegrationClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public TusoIntegrationClient(RestTemplate vashandiRestTemplate,
                                 @Value("${impilo.vashandi.integration.tuso-base-url}") String baseUrl) {
        this.restTemplate = vashandiRestTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Registry-plane tenant. The estate runs a deliberate two-plane tenancy model
     * (see experience-bff PublicTenants): the facility/provider/reference master lives on
     * the registry plane, while episodes/orders/assignments live on the care plane.
     * A reader must declare the plane holding the rows it reads.
     */
    private static final String REGISTRY_PLANE_TENANT = "00000000-0000-0000-0000-000000000001";

    public IntegrationCheckResult validateFacility(UUID facilityId) {
        if (facilityId == null) {
            return IntegrationCheckResult.degraded("tuso", "facility_id required");
        }
        try {
            // An assignment carries the canonical facility UUID, but /v1/internal/facilities/{id}
            // takes the NUMERIC surrogate — so passing a UUID there returned 400 Bad Request on
            // every activation. TUSO serves UUID lookups on /by-uid/{uuid}.
            String url = baseUrl + "/v1/internal/facilities/by-uid/" + facilityId;
            // The facility master is REGISTRY-plane data (PublicTenants); this call is made while
            // acting in a CARE-plane trust context. Without declaring the plane, tuso's
            // tenant filter matches nothing and a real facility reads as invalid.
            HttpHeaders headers = buildTrustHeaders();
            headers.set(TrustContext.H_TENANT_ID, REGISTRY_PLANE_TENANT);
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            return IntegrationCheckResult.live("tuso", response.getBody());
        } catch (RestClientException ex) {
            log.warn("TUSO unavailable for facility {}: {}", facilityId, ex.getMessage());
            return IntegrationCheckResult.degraded("tuso", ex.getMessage());
        }
    }

    public Optional<Map<String, Object>> getFacility(UUID facilityId) {
        IntegrationCheckResult result = validateFacility(facilityId);
        return result.payload();
    }

    /**
     * Soft virtual-pool check: does any TUSO virtual service expose a routing
     * seam whose target_ref equals {@code poolId}? Returns {@code empty} when
     * TUSO is unreachable or the response is unusable — callers must treat
     * empty as "cannot verify" and NEVER hard-fail on it (shift creation must
     * not depend on TUSO availability).
     */
    @SuppressWarnings("unchecked")
    public Optional<Boolean> virtualPoolKnown(String poolId) {
        if (poolId == null || poolId.isBlank()) {
            return Optional.empty();
        }
        try {
            String url = baseUrl + "/v1/internal/virtual-services";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            Object data = response.getBody() == null ? null : response.getBody().get("data");
            if (!(data instanceof java.util.List<?> entities)) {
                return Optional.empty();
            }
            for (Object entity : entities) {
                if (!(entity instanceof Map<?, ?> vs)) continue;
                Object seams = ((Map<String, Object>) vs).get("routingSeams");
                if (!(seams instanceof java.util.List<?> seamList)) continue;
                for (Object seam : seamList) {
                    if (seam instanceof Map<?, ?> s && poolId.equals(s.get("targetRef"))) {
                        return Optional.of(Boolean.TRUE);
                    }
                }
            }
            return Optional.of(Boolean.FALSE);
        } catch (RestClientException ex) {
            log.warn("TUSO unavailable for virtual-pool check {}: {}", poolId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Soft check (A5): does the canonical tuso.facility_department table
     * (V051) know this department at this facility? Returns {@code empty}
     * when TUSO is unreachable — callers must treat empty as "cannot verify"
     * and never hard-fail on it, and never treat "false" as licence to
     * invent/guess a department; the caller's job is to record the outcome
     * (e.g. a DEPARTMENT_UNVALIDATED restriction), not to silently upgrade
     * or downgrade the reference.
     *
     * <p>Vashandi's vsh_workforce_assignment.facilityId is the cross-service
     * {@code facility_uuid}, while TUSO's department/unit/service-point
     * endpoints are keyed on the numeric bigint id — resolves via
     * {@code /by-uid/} first (the same bridge PCT/session-contract already
     * use) rather than guessing an id mapping.</p>
     */
    @SuppressWarnings("unchecked")
    public Optional<Boolean> departmentKnown(UUID facilityUuid, String departmentCode) {
        if (facilityUuid == null || departmentCode == null || departmentCode.isBlank()) {
            return Optional.empty();
        }
        Long facilityId;
        try {
            String byUidUrl = baseUrl + "/v1/internal/facilities/by-uid/" + facilityUuid;
            ResponseEntity<Map<String, Object>> byUidResponse = restTemplate.exchange(
                    byUidUrl, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            Object data = byUidResponse.getBody() == null ? null : byUidResponse.getBody().get("data");
            Object idAttr = data instanceof Map<?, ?> m ? ((Map<String, Object>) m).get("id") : null;
            if (idAttr == null) {
                return Optional.empty();
            }
            facilityId = Long.valueOf(idAttr.toString());
        } catch (RestClientException | NumberFormatException ex) {
            log.warn("TUSO unavailable resolving facility_uuid={} for department check: {}", facilityUuid, ex.getMessage());
            return Optional.empty();
        }
        try {
            String url = baseUrl + "/v1/internal/facilities/" + facilityId + "/departments";
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url, HttpMethod.GET, new HttpEntity<>(buildTrustHeaders()),
                    new ParameterizedTypeReference<>() {});
            Object data = response.getBody() == null ? null : response.getBody().get("data");
            if (!(data instanceof java.util.List<?> departments)) {
                return Optional.empty();
            }
            for (Object d : departments) {
                if (d instanceof Map<?, ?> dept && departmentCode.equals(dept.get("departmentCode"))) {
                    return Optional.of(Boolean.TRUE);
                }
            }
            return Optional.of(Boolean.FALSE);
        } catch (RestClientException ex) {
            log.warn("TUSO unavailable for department check facility={} department={}: {}",
                    facilityId, departmentCode, ex.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders buildTrustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        try {
            TrustContext ctx = TrustContextHolder.require();
            headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            if (ctx.facilityId() != null) {
                headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            }
        } catch (IllegalStateException e) {
            log.debug("No trust context for TUSO call");
        }
        return headers;
    }
}
