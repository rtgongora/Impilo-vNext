package zw.gov.mohcc.impilo.varapi.core;

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
 * Best-effort caller into workflow-service to START an IATG provider-claim
 * adjudication workflow for a WS-F provider-claim CONFLICT (a claim onto a Health
 * ID that already owns a distinct provider profile), mirroring the org-registry
 * Channel-C {@code WorkflowServiceClient}.
 *
 * <p>Non-blocking: every failure path returns {@link Optional#empty()} and never
 * throws into the caller. Config-gated by {@code impilo.varapi.adjudication.enabled}
 * (default false) — when off, the caller's behaviour is unchanged.</p>
 */
@Component
public class ProviderWorkflowServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ProviderWorkflowServiceClient.class);

    private final boolean enabled;
    private final String podId;
    private final UUID definitionId;
    private final RestClient restClient;

    public ProviderWorkflowServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.varapi.adjudication.enabled:false}") boolean enabled,
            @Value("${impilo.varapi.adjudication.workflow-base-url:http://localhost:8250}") String baseUrl,
            @Value("${impilo.varapi.adjudication.pod-id:national}") String podId,
            @Value("${impilo.varapi.adjudication.definition-id:ad1d0000-0000-4000-8000-000000000002}") String definitionId) {
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
     * Start the provider-claim adjudication workflow.
     *
     * @param subjectRef the provider public id in conflict.
     * @return the started workflow instance id, or empty on any failure / disabled.
     */
    public Optional<UUID> startAdjudication(UUID tenantId, String subjectRef,
                                            Map<String, Object> context, String initiatorRef,
                                            String correlationId) {
        if (!enabled) {
            log.info("Provider-claim adjudication start skipped "
                    + "(impilo.varapi.adjudication.enabled=false) — subject {} left as CONFLICT", subjectRef);
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
                    .header(CompanionHeaders.IDEMPOTENCY_KEY, "varapi-provider-claim-conflict:" + subjectRef)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("instanceId")) {
                log.warn("Provider-claim adjudication for subject {} returned no instanceId — left as CONFLICT", subjectRef);
                return Optional.empty();
            }
            UUID instanceId = UUID.fromString(response.get("instanceId").asText());
            log.info("Started provider-claim adjudication instance {} for subject {} (definition {})",
                    instanceId, subjectRef, definitionId);
            return Optional.of(instanceId);
        } catch (Exception e) {
            log.warn("Provider-claim adjudication start for subject {} failed ({}: {}) — left as CONFLICT",
                    subjectRef, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
