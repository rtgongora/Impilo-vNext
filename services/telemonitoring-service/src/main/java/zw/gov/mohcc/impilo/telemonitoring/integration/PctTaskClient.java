package zw.gov.mohcc.impilo.telemonitoring.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Creates CHW/care-team tasks in PCT on plan activation (§14.2 approval step: "generates
 * the initial PCT task set … where applicable"; §14 canonical stance 3 — PCT keeps task
 * system-of-record, telemonitoring keeps plan/alert system-of-record).
 *
 * <p>Uses PCT's <b>generic task lane</b> {@code POST /v1/tasks} (TaskService/pct_tasks —
 * {@code journey_id} is nullable, so a monitoring-plan task needs no journey), NOT the
 * referral-scoped lane from TM-B7 which is bound to {@code pct_referrals}.</p>
 *
 * <p>Best-effort and non-blocking: a degraded PCT never rolls back a clinician's
 * activation. The durable record of the request is the
 * {@code telemonitoring.plan.task_requested.v1} outbox event emitted in the same
 * transaction as the activation; this HTTP push is the immediate delivery attempt and its
 * outcome is reported back to the caller for inclusion in that event.</p>
 */
@Component
public class PctTaskClient {

    private static final Logger log = LoggerFactory.getLogger(PctTaskClient.class);

    private final RestClient restClient;
    private final String baseUrl;

    public PctTaskClient(
            @Value("${telemonitoring.pct.base-url:http://pct-service:8088}") String baseUrl,
            @Value("${telemonitoring.pct.timeout-ms:4000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMs, 2000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /**
     * Create a monitoring task in PCT's generic task lane. Never throws.
     *
     * @return true when PCT accepted the task, false when the push failed (the
     *         task_requested outbox event remains the durable seam either way)
     */
    public boolean createTask(UUID tenantId, String taskType, String assigneeId, String assigneeRole,
                              UUID workspaceId, OffsetDateTime dueAt, String notes) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("taskType", taskType);
        body.put("assigneeId", assigneeId);
        body.put("assigneeRole", assigneeRole);
        body.put("workspaceId", workspaceId);
        body.put("dueAt", dueAt);
        body.put("notes", notes);
        try {
            restClient.post()
                    .uri(baseUrl + "/v1/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> {
                        h.set("X-Tenant-ID", tenantId.toString());
                        h.set("X-Pod-ID", "national-spine");
                        h.set("X-Request-ID", UUID.randomUUID().toString());
                        h.set("X-Correlation-ID", UUID.randomUUID().toString());
                        h.set("X-Actor-ID", "telemonitoring-service");
                        h.set("X-Actor-Type", "SERVICE");
                        h.set("X-Purpose-Of-Use", "TREATMENT");
                        h.set("Idempotency-Key", "telemonitoring-task-" + UUID.randomUUID());
                    })
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.warn("PCT task push failed (type={}, assignee={}): {} — task_requested outbox event remains the durable seam",
                    taskType, assigneeId, e.getMessage());
            return false;
        }
    }
}
