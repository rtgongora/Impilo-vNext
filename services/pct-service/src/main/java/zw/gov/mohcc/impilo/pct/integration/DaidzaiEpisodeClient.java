package zw.gov.mohcc.impilo.pct.integration;

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
 * Thin client to the DAIDZAI canonical trauma-episode spine. PCT keeps its own ED system-of-record
 * rows and carries the shared {@code trauma_episode_id}; DAIDZAI owns the episode + timeline.
 *
 * <ul>
 *   <li>{@link #mintEdWalkIn} — PCT mints on ED-first walk-in trauma (origin_kind=ED_WALK_IN,
 *       idempotent on origin_key = ed_visit id). Returns the episode id, or null if DAIDZAI is
 *       unreachable (the ED write must never be blocked by a degraded spine).</li>
 *   <li>{@link #registerPhase} — best-effort read-model timeline update; failure is swallowed so a
 *       clinical transaction is never rolled back by a correlation side-channel.</li>
 * </ul>
 *
 * <p>Service-originated call — synthesises the mandatory trust envelope (the
 * MISSING_REQUIRED_HEADER defect family).</p>
 */
@Service
public class DaidzaiEpisodeClient {

    private static final Logger log = LoggerFactory.getLogger(DaidzaiEpisodeClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public DaidzaiEpisodeClient(RestTemplate restTemplate,
                                @Value("${pct.integration.daidzai.base-url:http://localhost:8392}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    /**
     * Idempotently mint an ED-first walk-in trauma episode against PCT.
     *
     * @return the minted (or pre-existing) trauma episode id, or {@code null} if DAIDZAI is
     *         unreachable / returned no id.
     */
    public UUID mintEdWalkIn(UUID tenantId, UUID edVisitId, String subjectCpid,
                             String subjectIdentityMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("originService", "pct-service");
        body.put("originKind", "ED_WALK_IN");
        body.put("originKey", edVisitId.toString());
        body.put("ownerRef", edVisitId.toString());
        body.put("firstPhase", "ED");
        body.put("subjectIdentityMode", subjectIdentityMode != null ? subjectIdentityMode : "UNKNOWN");
        if (subjectCpid != null) { body.put("subjectCpid", subjectCpid); body.put("subjectHealthId", subjectCpid); } // actor-plane HID (legacy daidzai wire key; value is a CPID)
        try {
            ResponseEntity<Map> resp = restTemplate.exchange(baseUrl + "/internal/v1/daidzai/trauma-episodes",
                    HttpMethod.POST, new HttpEntity<>(body, headers(tenantId)), Map.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                Object id = resp.getBody().get("traumaEpisodeId");
                if (id != null) return UUID.fromString(id.toString());
            }
            log.warn("DAIDZAI trauma-episode mint for ED visit {} returned no id (status {})",
                    edVisitId, resp.getStatusCode());
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("DAIDZAI unreachable minting ED walk-in episode for visit {}: {} — ED write proceeds",
                    edVisitId, e.getMessage());
        }
        return null;
    }

    /** Best-effort timeline registration; never throws (a clinical write must not roll back on this). */
    public void registerPhase(UUID tenantId, UUID episodeId, String phase, String ownerRef,
                              String status, String eventType) {
        if (episodeId == null) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("phase", phase);
        body.put("ownerService", "pct-service");
        body.put("ownerRef", ownerRef);
        body.put("status", status);
        body.put("eventType", eventType);
        try {
            restTemplate.exchange(baseUrl + "/internal/v1/daidzai/trauma-episodes/" + episodeId + "/phases",
                    HttpMethod.POST, new HttpEntity<>(body, headers(tenantId)), Void.class);
        } catch (RestClientException e) {
            log.warn("DAIDZAI phase registration ({}) for episode {} failed: {} — projection stays honest",
                    phase, episodeId, e.getMessage());
        }
    }

    /**
     * Close the trauma episode on disposition (best-effort). ED disposition ends the trauma journey;
     * surgery does NOT close (theatre proved that). Idempotent on the DAIDZAI side.
     */
    public void close(UUID tenantId, UUID episodeId, String reason) {
        if (episodeId == null) return;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("reason", reason);
        try {
            restTemplate.exchange(baseUrl + "/internal/v1/daidzai/trauma-episodes/" + episodeId + "/close",
                    HttpMethod.POST, new HttpEntity<>(body, headers(tenantId)), Void.class);
        } catch (RestClientException e) {
            log.warn("DAIDZAI episode close for {} failed: {} — disposition still recorded",
                    episodeId, e.getMessage());
        }
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        if (tenantId != null) h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "pct-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        h.set("Idempotency-Key", "pct-ep-" + UUID.randomUUID());
        h.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return h;
    }
}
