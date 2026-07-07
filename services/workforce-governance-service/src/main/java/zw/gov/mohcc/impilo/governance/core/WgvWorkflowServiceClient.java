package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Best-effort caller into workflow-service to START an IATG identity-adjudication
 * workflow instance for an employment CONFLICT (WS-D), mirroring the org-registry
 * Channel-C {@code WorkflowServiceClient}.
 *
 * <p>Contract: {@code POST /internal/v1/workflows/instances} with
 * {@code {definitionId, initiatorRef, subjectRef, context}} + mandatory trust
 * headers. Non-blocking: every failure path (disabled, network, non-2xx,
 * malformed) returns {@link Optional#empty()} and never throws into the caller —
 * a failure leaves the conflict case OPEN with a null workflow instance
 * (adjudication pending/unavailable) rather than corrupting state.</p>
 *
 * <p>Config-gated by {@code impilo.governance.adjudication.enabled} (default
 * false); definition id defaults to the IATG IDENTITY adjudication definition.</p>
 */
@Component
public class WgvWorkflowServiceClient {

    private static final Logger log = LoggerFactory.getLogger(WgvWorkflowServiceClient.class);

    private final boolean enabled;
    private final String podId;
    private final UUID definitionId;
    private final RestClient restClient;

    public WgvWorkflowServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.governance.adjudication.enabled:false}") boolean enabled,
            @Value("${impilo.governance.adjudication.workflow-base-url:http://localhost:8250}") String baseUrl,
            @Value("${impilo.governance.adjudication.pod-id:national}") String podId,
            @Value("${impilo.governance.adjudication.definition-id:ad1d0000-0000-4000-8000-000000000001}") String definitionId) {
        this.enabled = enabled;
        this.podId = podId;
        this.definitionId = UUID.fromString(definitionId);
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public UUID definitionId() {
        return definitionId;
    }

    /**
     * Start the identity-adjudication workflow for an employment conflict.
     *
     * @return the started workflow instance id, or empty on any failure / when
     *         the feature is disabled (honest pending state).
     */
    public Optional<UUID> startAdjudication(UUID tenantId, String subjectRef,
                                            Map<String, Object> context, String initiatorRef,
                                            String correlationId) {
        if (!enabled) {
            log.info("Employment-conflict adjudication start skipped "
                    + "(impilo.governance.adjudication.enabled=false) — subject {} left pending", subjectRef);
            return Optional.empty();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId.toString());
        body.put("initiatorRef", initiatorRef);
        body.put("subjectRef", subjectRef);
        body.put("context", context == null ? Map.of() : context);

        String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
        try {
            JsonNode response = restClient.post()
                    .uri("/internal/v1/workflows/instances")
                    .header(CompanionHeaders.TENANT_ID, tenantId.toString())
                    .header(CompanionHeaders.POD_ID, podId)
                    .header(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString())
                    .header(CompanionHeaders.CORRELATION_ID, corr)
                    .header(CompanionHeaders.IDEMPOTENCY_KEY, "wgv-employment-conflict:" + subjectRef)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("instanceId")) {
                log.warn("Employment-conflict adjudication for subject {} returned no instanceId — leaving pending", subjectRef);
                return Optional.empty();
            }
            UUID instanceId = UUID.fromString(response.get("instanceId").asText());
            log.info("Started employment-conflict adjudication instance {} for subject {} (definition {})",
                    instanceId, subjectRef, definitionId);
            return Optional.of(instanceId);
        } catch (Exception e) {
            log.warn("Employment-conflict adjudication start for subject {} failed ({}: {}) — leaving pending/unavailable",
                    subjectRef, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
