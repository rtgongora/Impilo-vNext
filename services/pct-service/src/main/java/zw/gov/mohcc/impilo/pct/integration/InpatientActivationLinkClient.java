package zw.gov.mohcc.impilo.pct.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The write-back half of the correlation column inpatient's V200 added:
 * {@code emergency_activation.emergency_episode_id}. Nothing populated it until this client existed
 * (W6b) — {@link InFacilityDeteriorationConsumer} calls it right after minting the episode that an
 * {@code EMERGENCY_ACTIVATION_RAISED} event triggered.
 *
 * <p>Best-effort, matching every other cross-service client in this pack: an inpatient outage never
 * blocks the episode mint (which already happened locally and is the important write); the write-back
 * just does not land this round, and inpatient's own activation row shows an absent
 * {@code emergency_episode_id} rather than a fabricated one.
 */
@Service
public class InpatientActivationLinkClient {

    private static final Logger log = LoggerFactory.getLogger(InpatientActivationLinkClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public InpatientActivationLinkClient(RestTemplate restTemplate,
                                         @Value("${pct.integration.inpatient.base-url:http://localhost:8121}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void linkEpisode(UUID tenantId, UUID activationId, UUID emergencyEpisodeId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("emergencyEpisodeId", emergencyEpisodeId.toString());
        try {
            restTemplate.exchange(
                    baseUrl + "/internal/v1/emergency/" + activationId + "/link-episode",
                    HttpMethod.POST, new HttpEntity<>(body, headers(tenantId)), Void.class);
        } catch (RestClientException e) {
            log.warn("PCT-EMERGENCY: failed to write emergency_episode_id {} back onto inpatient "
                    + "activation {}: {} — the episode still exists, only the back-link is missing "
                    + "this round", emergencyEpisodeId, activationId, e.getMessage());
        }
    }

    private HttpHeaders headers(UUID tenantId) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (tenantId != null) h.set("X-Tenant-ID", tenantId.toString());
        h.set("X-Pod-ID", "national-spine");
        h.set("X-Request-ID", UUID.randomUUID().toString());
        h.set("X-Correlation-ID", UUID.randomUUID().toString());
        h.set("X-Actor-ID", "pct-service");
        h.set("X-Actor-Type", "SERVICE");
        h.set("X-Purpose-Of-Use", "TREATMENT");
        return h;
    }
}
