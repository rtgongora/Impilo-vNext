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
 * Thin, best-effort client to the DAIDZAI canonical trauma-episode spine (8392). Inpatient keeps
 * its own resuscitation system-of-record rows and carries the shared {@code trauma_episode_id};
 * when a resuscitation is recorded against a trauma episode it registers a RESUS phase on the
 * episode read-model timeline. Failure is swallowed — a correlation side-channel must never roll
 * back or block a clinical write. Mirrors {@link MadiBloodClient}: ctor-injected
 * {@code inpatientRestTemplate} + {@code @Value} base-url + trust-context header copy.
 */
@Service
public class DaidzaiEpisodeClient {

    private static final Logger log = LoggerFactory.getLogger(DaidzaiEpisodeClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DaidzaiEpisodeClient(RestTemplate inpatientRestTemplate,
                                @Value("${inpatient.integration.daidzai.base-url:http://localhost:8392}") String baseUrl) {
        this.restTemplate = inpatientRestTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * DEFENSIVE mint of a trauma episode for a trauma-originated surgery whose {@code X-Trauma-Episode-ID}
     * header was absent (should not happen in the normal flow — daidzai/PCT mint upstream). Idempotent on
     * {@code (tenant, originKey)}: a retry returns the same id. Best-effort — returns {@code null} on
     * failure so life-saving surgery is never blocked. CONSUME-first: only call after the header path.
     */
    @SuppressWarnings("rawtypes")
    public UUID mintForProcedure(String originKey, String firstPhase) {
        if (originKey == null || originKey.isBlank()) return null;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originService", "inpatient");
        body.put("originKind", "PROCEDURE");
        body.put("originKey", originKey);
        if (firstPhase != null) body.put("firstPhase", firstPhase);
        try {
            ResponseEntity<Map> resp = restTemplate.postForEntity(baseUrl + "/internal/v1/daidzai/trauma-episodes",
                    new HttpEntity<>(body, trustHeaders()), Map.class);
            Object id = resp.getBody() != null ? resp.getBody().get("traumaEpisodeId") : null;
            return id != null ? UUID.fromString(id.toString()) : null;
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("DAIDZAI defensive mint for procedure {} failed: {} — surgery proceeds, link deferred",
                    originKey, e.getMessage());
            return null;
        }
    }

    public void registerPhase(UUID episodeId, String phase, String ownerRef, String status, String eventType) {
        if (episodeId == null) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", phase);
        body.put("ownerService", "inpatient-service");
        body.put("ownerRef", ownerRef);
        body.put("status", status);
        body.put("eventType", eventType);
        try {
            restTemplate.postForEntity(baseUrl + "/internal/v1/daidzai/trauma-episodes/" + episodeId + "/phases",
                    new HttpEntity<>(body, trustHeaders()), Void.class);
        } catch (RestClientException e) {
            log.warn("DAIDZAI phase registration ({}) for episode {} failed: {} — projection stays honest",
                    phase, episodeId, e.getMessage());
        }
    }

    private HttpHeaders trustHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Pod-ID", System.getenv("HOSTNAME") != null ? System.getenv("HOSTNAME") : "inpatient");
        headers.set("X-Request-ID", UUID.randomUUID().toString());
        headers.set("Idempotency-Key", "inp-ep-" + UUID.randomUUID());
        try {
            TrustContext ctx = TrustContextHolder.require();
            if (ctx.tenantId() != null) headers.set(TrustContext.H_TENANT_ID, ctx.tenantId().toString());
            if (ctx.actorId() != null) headers.set(TrustContext.H_ACTOR_ID, ctx.actorId());
            if (ctx.correlationId() != null) headers.set(TrustContext.H_CORRELATION_ID, ctx.correlationId().toString());
            else headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
            if (ctx.facilityId() != null) headers.set(TrustContext.H_FACILITY_ID, ctx.facilityId().toString());
            if (ctx.purposeOfUse() != null) headers.set(TrustContext.H_PURPOSE_OF_USE, ctx.purposeOfUse());
        } catch (IllegalStateException ignored) {
            headers.set(TrustContext.H_CORRELATION_ID, UUID.randomUUID().toString());
        }
        headers.set("X-Actor-Type", "SERVICE");
        return headers;
    }
}
