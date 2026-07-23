package zw.gov.mohcc.impilo.telemonitoring.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Rung-11+ seam (OF-B26, §14.7): Daidzai EMS clinical dispatch — the emergency service
 * boundary. Uses the estate idiom {@code POST /internal/v1/daidzai/incidents}
 * ({@code EmergencyController.escalate} — the provider/facility-triggered escalation
 * lane that creates an {@code EmergencyIncidentEntity} for dispatcher triage/dispatch).
 * The incident id lands in the Daidzai response's {@code id} field (distinct identifier
 * namespace from alert episodes, §5.3 collision rule).
 *
 * <p>§14.6 wording law: the description is CODED (trigger + metric + episode ref) —
 * never diagnosis text. Best-effort, never throws; the dispatched flag is recorded
 * honestly on the episode and the outbox event remains the durable seam.</p>
 */
@Component
public class DaidzaiIncidentClient {

    private static final Logger log = LoggerFactory.getLogger(DaidzaiIncidentClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public DaidzaiIncidentClient(
            @Value("${telemonitoring.daidzai.base-url:http://daidzai-service:8392}") String baseUrl,
            @Value("${telemonitoring.daidzai.timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Escalate a monitoring alert episode into a Daidzai emergency incident.
     * Returns the EMS incident id, or {@code null} when the push failed.
     */
    @SuppressWarnings("unchecked")
    public String createIncident(UUID tenantId, UUID episodeId, String patientCpid,
                                 String triggerClass, String metricCode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("emergencyCategory", "MEDICAL");
        body.put("severity", "CRITICAL");
        body.put("description", "Telemonitoring emergency escalation — trigger " + triggerClass
                + (metricCode != null ? ", metric " + metricCode : "")
                + ", alert episode " + episodeId);
        body.put("subjectIdentityMode", "CPID");
        body.put("subjectCpid", patientCpid);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/internal/v1/daidzai/incidents")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set("X-Tenant-ID", tenantId.toString());
                        h.set("X-Pod-ID", "national-spine");
                        h.set("X-Request-ID", UUID.randomUUID().toString());
                        h.set("X-Correlation-ID", UUID.randomUUID().toString());
                        h.set("X-Actor-ID", "telemonitoring-service");
                        h.set("X-Actor-Type", "SERVICE");
                        h.set("X-Purpose-Of-Use", "EMERGENCY");
                        h.set("Idempotency-Key", "telemonitoring-ems-" + episodeId);
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            // Daidzai incident id field is `id` (A5 idiom; snake/camel gotcha noted).
            Object id = response != null ? response.get("id") : null;
            return id != null ? id.toString() : null;
        } catch (RuntimeException e) {
            log.warn("Daidzai incident push failed for episode {}: {} — "
                    + "telemonitoring.alert.escalated.v1 remains the durable seam", episodeId, e.getMessage());
            return null;
        }
    }
}
