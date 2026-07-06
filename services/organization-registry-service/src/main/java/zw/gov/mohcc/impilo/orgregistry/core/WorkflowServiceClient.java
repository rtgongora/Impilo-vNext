package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgClaimSubmissionEntity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Best-effort caller into workflow-service to START an IATG adjudication
 * workflow instance for a Channel-C claim escalated to adjudication.
 *
 * <p>Contract: {@code POST /internal/v1/workflows/instances} with a
 * {@code StartInstanceRequest{definitionId, initiatorRef, subjectRef, context}}
 * and the mandatory trust headers. The engine is used as-is; nothing here
 * mutates workflow-service state beyond starting one instance.</p>
 *
 * <p><b>Non-blocking / honest failure:</b> every failure path (feature disabled,
 * network error, non-2xx, malformed response) returns {@link Optional#empty()}
 * and never throws into the claim transaction. A failure therefore leaves the
 * claim in an honest {@code UNDER_REVIEW} state with a null workflow instance id
 * (adjudication pending/unavailable) rather than corrupting claim state.</p>
 *
 * <p>Config-gated by {@code impilo.orgregistry.adjudication.enabled} (default
 * false): the external coupling to workflow-service is opt-in per environment.</p>
 */
@Component
public class WorkflowServiceClient {

    private static final Logger log = LoggerFactory.getLogger(WorkflowServiceClient.class);

    private final boolean enabled;
    private final String baseUrl;
    private final String podId;
    private final UUID definitionId;
    private final RestClient restClient;

    public WorkflowServiceClient(
            RestClient.Builder restClientBuilder,
            @Value("${impilo.orgregistry.adjudication.enabled:false}") boolean enabled,
            @Value("${impilo.orgregistry.adjudication.workflow-base-url:http://localhost:8250}") String baseUrl,
            @Value("${impilo.orgregistry.adjudication.pod-id:national}") String podId,
            @Value("${impilo.orgregistry.adjudication.definition-id:ad1d0000-0000-4000-8000-000000000003}") String definitionId) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
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
     * Starts the adjudication workflow for {@code claim}, using the claim id as
     * the workflow {@code subjectRef} and threading the claim linkage through the
     * workflow {@code context} so the resulting decision is resolvable back to
     * this claim.
     *
     * @return the started workflow instance id, or empty on any failure /
     *         when the feature is disabled (honest pending state).
     */
    public Optional<UUID> startAdjudication(UUID tenantId, OrgClaimSubmissionEntity claim,
                                            String initiatorRef, String correlationId) {
        if (!enabled) {
            log.info("Adjudication start skipped (impilo.orgregistry.adjudication.enabled=false) — "
                    + "claim {} left UNDER_REVIEW pending", claim.getId());
            return Optional.empty();
        }
        String claimId = claim.getId().toString();
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("claimId", claimId);
        context.put("organizationId", claim.getOrganizationId().toString());
        context.put("subjectHealthId", claim.getSubjectHealthId());
        context.put("claimedRole", claim.getClaimedRole());
        context.put("tenantId", tenantId.toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("definitionId", definitionId.toString());
        body.put("initiatorRef", initiatorRef);
        body.put("subjectRef", claimId);
        body.put("context", context);

        String corr = correlationId != null ? correlationId : UUID.randomUUID().toString();
        try {
            JsonNode response = restClient.post()
                    .uri("/internal/v1/workflows/instances")
                    .header(CompanionHeaders.TENANT_ID, tenantId.toString())
                    .header(CompanionHeaders.POD_ID, podId)
                    .header(CompanionHeaders.REQUEST_ID, UUID.randomUUID().toString())
                    .header(CompanionHeaders.CORRELATION_ID, corr)
                    .header(CompanionHeaders.IDEMPOTENCY_KEY, "org-claim-adjudication:" + claimId)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response == null || !response.hasNonNull("instanceId")) {
                log.warn("Adjudication start for claim {} returned no instanceId — leaving pending", claimId);
                return Optional.empty();
            }
            UUID instanceId = UUID.fromString(response.get("instanceId").asText());
            log.info("Started adjudication workflow instance {} for claim {} (definition {})",
                    instanceId, claimId, definitionId);
            return Optional.of(instanceId);
        } catch (Exception e) {
            log.warn("Adjudication start for claim {} failed ({}: {}) — leaving claim UNDER_REVIEW pending/unavailable",
                    claimId, e.getClass().getSimpleName(), e.getMessage());
            return Optional.empty();
        }
    }
}
