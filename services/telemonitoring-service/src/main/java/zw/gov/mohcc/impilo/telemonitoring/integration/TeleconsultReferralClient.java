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
 * Rung-6/7 seam (OF-B26, §14.7): creates the durable teleconsult case through the
 * Vol I Stage 1 front door — PCT's {@code POST /v1/referrals}
 * ({@code TelemedicineController} → {@code TelemedicineOrchestrationService.createReferral},
 * the case spine where sessionId==referralId and {@code telemedicine.session.referral_created}
 * is emitted). Telemonitoring never spins up ad-hoc media sessions outside that spine.
 *
 * <p><b>Provenance (origin=MONITORING_ALERT).</b> The referral aggregate has no dedicated
 * origin column, so provenance rides the two fields the SoR already treats as
 * caller-supplied identity/type:
 * <ul>
 *   <li>{@code client_offline_id} = {@code MONITORING_ALERT:{episodeId}} — the SoR's own
 *       replay-dedup key (the {@code PREFIX:id} sourceRef idiom), which ALSO makes the
 *       best-effort push idempotent: a retried dispatch returns the same case, never a
 *       second one;</li>
 *   <li>{@code referralType} = {@code MONITORING_ALERT} (free-typed column, default
 *       SPECIALIST) so worklists can discriminate the origin.</li>
 * </ul>
 *
 * <p>§14.6 wording law: the reason is CODED (trigger class + metric + episode ref) —
 * never a diagnosis or patient-facing text.</p>
 *
 * <p>Best-effort, never throws: the durable record of the escalation is the episode's
 * ladder entry + {@code telemonitoring.alert.escalated.v1} outbox event; this push is
 * the immediate delivery attempt and its outcome is recorded honestly.</p>
 */
@Component
public class TeleconsultReferralClient {

    private static final Logger log = LoggerFactory.getLogger(TeleconsultReferralClient.class);

    public static final String ORIGIN_MONITORING_ALERT = "MONITORING_ALERT";

    private final RestClient restClient;
    private final String baseUrl;

    public TeleconsultReferralClient(
            @Value("${telemonitoring.pct.base-url:http://pct-service:8088}") String baseUrl,
            @Value("${telemonitoring.pct.timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Create the rung-6/7 virtual-review case. Returns the created referral/case id,
     * or {@code null} when the push failed (recorded honestly on the episode).
     *
     * @param urgent rung 7 (urgent teleconsultation) vs rung 6 (scheduled virtual review)
     */
    @SuppressWarnings("unchecked")
    public String createMonitoringReviewReferral(UUID tenantId, UUID episodeId, String patientCpid,
                                                 String triggerClass, String metricCode, boolean urgent) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patientId", patientCpid);
        // Coded reason — trigger + metric + episode ref only (§14.6: no diagnosis text).
        body.put("reason", "Monitoring alert escalation — trigger " + triggerClass
                + (metricCode != null ? ", metric " + metricCode : "")
                + ", episode " + episodeId);
        body.put("referralType", ORIGIN_MONITORING_ALERT);
        body.put("urgency", urgent ? "URGENT" : "ROUTINE");
        body.put("modality", "virtual");
        body.put("virtual_mode", "video");
        body.put("client_offline_id", ORIGIN_MONITORING_ALERT + ":" + episodeId);
        try {
            Map<String, Object> response = restClient.post()
                    .uri(baseUrl + "/v1/referrals")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set("X-Tenant-ID", tenantId.toString());
                        h.set("X-Pod-ID", "national-spine");
                        h.set("X-Request-ID", UUID.randomUUID().toString());
                        h.set("X-Correlation-ID", UUID.randomUUID().toString());
                        h.set("X-Actor-ID", "telemonitoring-service");
                        h.set("X-Actor-Type", "SERVICE");
                        h.set("X-Purpose-Of-Use", "TREATMENT");
                        h.set("Idempotency-Key", ORIGIN_MONITORING_ALERT + "-" + episodeId);
                    })
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response == null) {
                return null;
            }
            Object data = response.get("data");
            Object id = data instanceof Map<?, ?> dataMap ? dataMap.get("id") : response.get("id");
            return id != null ? id.toString() : null;
        } catch (RuntimeException e) {
            log.warn("Rung-6/7 teleconsult referral push failed for episode {}: {} — "
                    + "telemonitoring.alert.escalated.v1 remains the durable seam", episodeId, e.getMessage());
            return null;
        }
    }
}
