package zw.gov.mohcc.impilo.inpatient.integration;

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

/**
 * Read-only financial-clearance check against COSTA (costing-engine-service), for the theatre
 * start gate (pipeline §23). COSTA remains the payment truth — this client only asks; it never
 * writes, and inpatient-service persists only a PROJECTION of the answer for its own audit
 * trail (see {@code ProcedureEpisodeEntity.financialClearanceStatus}).
 *
 * <p>Best-effort like {@link TheatreReadinessClient}: a downstream outage or an encounter with
 * no recorded decision both resolve to a named, honest outcome rather than either fabricating
 * clearance or hard-blocking every theatre start on a COSTA dependency that most facilities and
 * most patients will not have populated. Only an EXPLICIT blocked status from COSTA gates
 * anything.</p>
 */
@Service
public class CostaServiceAccessClient {

    private static final Logger log = LoggerFactory.getLogger(CostaServiceAccessClient.class);

    /** Mirrors costa.domain.enums.ServiceAccessStatus's two blocking values — the only two
     *  that gate a start. Every other COSTA status (including EMERGENCY_OVERRIDE, which COSTA
     *  itself may have already recorded) passes through this gate. */
    private static final java.util.Set<String> BLOCKING_STATUSES =
            java.util.Set.of("BLOCKED_PENDING_PAYMENT", "BLOCKED_PENDING_AUTHORISATION");

    private final RestTemplate restTemplate;
    private final String costaBaseUrl;

    public CostaServiceAccessClient(
            RestTemplate inpatientRestTemplate,
            @Value("${inpatient.integration.costa.base-url:http://localhost:8101}") String costaBaseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.costaBaseUrl = costaBaseUrl;
    }

    /** Outcome of a financial-clearance check. status is one of COSTA's own ServiceAccessStatus
     *  values, or "NOT_YET_EVALUATED" (asked COSTA, no decision existed) or "UNAVAILABLE"
     *  (COSTA could not be reached — never treated as a block). */
    public record ClearanceResult(String status, boolean blocking) {
        public static ClearanceResult of(String status) {
            return new ClearanceResult(status, BLOCKING_STATUSES.contains(status));
        }
        public static ClearanceResult notYetEvaluated() {
            return new ClearanceResult("NOT_YET_EVALUATED", false);
        }
        public static ClearanceResult unavailable() {
            return new ClearanceResult("UNAVAILABLE", false);
        }
    }

    @SuppressWarnings("unchecked")
    public ClearanceResult checkClearance(String encounterId) {
        if (encounterId == null || encounterId.isBlank()) {
            return ClearanceResult.notYetEvaluated();
        }
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(
                    costaBaseUrl + "/costa/v1/service-access-decisions?encounter_id=" + encounterId,
                    HttpMethod.GET, new HttpEntity<>(trustHeaders()), Map.class);
            Map<String, Object> envelope = resp.getBody();
            Object data = envelope != null ? envelope.get("data") : null;
            List<Map<String, Object>> decisions = data instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
            if (decisions.isEmpty()) {
                return ClearanceResult.notYetEvaluated();
            }
            // service.listRecent(encounterId) is already ordered most-recent-first — take the
            // first as the current decision for this encounter.
            Object accessStatus = decisions.get(0).get("access_status");
            if (accessStatus == null) {
                return ClearanceResult.notYetEvaluated();
            }
            return ClearanceResult.of(String.valueOf(accessStatus));
        } catch (RestClientException | IllegalStateException e) {
            log.warn("INPATIENT-THEATRE: COSTA service-access-decision unavailable for encounter={} "
                    + "(best-effort, never blocks): {}", encounterId, e.getMessage());
            return ClearanceResult.unavailable();
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
