package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;

import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AdminGovernanceAccessRequestPersistence {

    private final AdminGovernanceAccessRequestStore redisStore;
    private final WorkforceGovernanceClient workforceGovernanceClient;

    public AdminGovernanceAccessRequestPersistence(AdminGovernanceAccessRequestStore redisStore,
                                                   WorkforceGovernanceClient workforceGovernanceClient) {
        this.redisStore = redisStore;
        this.workforceGovernanceClient = workforceGovernanceClient;
    }

    public AdminGovernanceDtos.AccessRequestRecord save(String tenantId,
                                                        AdminGovernanceDtos.AccessRequestRecord record,
                                                        String requesterId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestType", record.requestType());
        body.put("status", record.status());
        body.put("requesterId", record.requesterId());
        body.put("requesterName", record.requesterName());
        body.put("organisationId", record.organisationId());
        body.put("targetSubjectId", record.targetSubjectId());
        body.put("requestedRole", record.requestedRole());
        body.put("requestedScope", record.requestedScope());
        body.put("requestedEnvironment", record.requestedEnvironment());
        body.put("requestedDataScope", record.requestedDataScope());
        body.put("riskLevel", record.riskLevel());
        body.put("policyPrecheckResult", record.policyPrecheckResult());
        body.put("approvalsRequired", record.approvalsRequired());
        body.put("payload", record.payload());
        body.put("fallbackRequestId", record.id());

        JsonNode downstream = workforceGovernanceClient.postJson("/v1/internal/governance/access-requests", body);
        String now = OffsetDateTime.now().toString();
        if (downstream != null && !downstream.isNull() && downstream.has("id")) {
            AdminGovernanceDtos.AccessRequestRecord durable = withSoR(record, downstream.get("id").asText(), now);
            redisStore.save(tenantId, durable);
            return durable;
        }

        AdminGovernanceDtos.AccessRequestRecord fallback = withFallback(record, now);
        redisStore.save(tenantId, fallback);
        return fallback;
    }

    public List<AdminGovernanceDtos.AccessRequestRecord> list(String tenantId) {
        JsonNode downstream = workforceGovernanceClient.getJson("/v1/internal/governance/access-requests");
        if (downstream != null && downstream.isArray() && !downstream.isEmpty()) {
            List<AdminGovernanceDtos.AccessRequestRecord> fromSoR = new ArrayList<>();
            downstream.forEach(node -> fromSoR.add(mapFromSoR(node)));
            return fromSoR;
        }
        return redisStore.list(tenantId);
    }

    public Optional<AdminGovernanceDtos.AccessRequestRecord> find(String tenantId, String requestId) {
        Optional<AdminGovernanceDtos.AccessRequestRecord> cached = redisStore.find(tenantId, requestId);
        if (cached.isPresent()) return cached;
        JsonNode downstream = workforceGovernanceClient.getJson("/v1/internal/governance/access-requests");
        if (downstream != null && downstream.isArray()) {
            for (JsonNode node : downstream) {
                if (requestId.equals(node.path("id").asText())) {
                    return Optional.of(mapFromSoR(node));
                }
            }
        }
        return Optional.empty();
    }

    public AdminGovernanceDtos.AccessRequestRecord decide(String tenantId,
                                                          AdminGovernanceDtos.AccessRequestRecord existing,
                                                          String decision,
                                                          String actorId,
                                                          String notes) {
        String path = switch (normalize(decision)) {
            case "approve" -> "/v1/internal/governance/access-requests/" + resolveDurableId(existing) + "/approve";
            case "reject", "deny" -> "/v1/internal/governance/access-requests/" + resolveDurableId(existing) + "/reject";
            case "revoke" -> "/v1/internal/governance/access-requests/" + resolveDurableId(existing) + "/revoke";
            case "escalate" -> "/v1/internal/governance/access-requests/" + resolveDurableId(existing) + "/escalate";
            default -> null;
        };
        if (path != null && existing.durableRequestId() != null) {
            Map<String, Object> body = Map.of("notes", notes != null ? notes : "", "actorId", actorId);
            JsonNode result = workforceGovernanceClient.postJson(path, body);
            if (result != null && !result.isNull()) {
                AdminGovernanceDtos.AccessRequestRecord updated = mapFromSoR(result);
                redisStore.save(tenantId, updated);
                return updated;
            }
        }
        return redisStore.save(tenantId, existing);
    }

    private static AdminGovernanceDtos.AccessRequestRecord withSoR(AdminGovernanceDtos.AccessRequestRecord record, String durableId, String now) {
        return new AdminGovernanceDtos.AccessRequestRecord(
                record.id(),
                record.requestType(),
                record.status(),
                record.requesterId(),
                record.requesterName(),
                record.organisationId(),
                record.organisationName(),
                record.targetSubjectId(),
                record.requestedRole(),
                record.requestedScope(),
                record.requestedEnvironment(),
                record.requestedDataScope(),
                record.riskLevel(),
                record.policyPrecheckResult(),
                record.approvalsRequired(),
                record.auditTrail(),
                record.createdAt(),
                now,
                record.payload(),
                "workforce_governance",
                record.id(),
                durableId,
                durableId,
                false,
                now,
                "synced"
        );
    }

    private static AdminGovernanceDtos.AccessRequestRecord withFallback(AdminGovernanceDtos.AccessRequestRecord record, String now) {
        return new AdminGovernanceDtos.AccessRequestRecord(
                record.id(),
                record.requestType(),
                record.status(),
                record.requesterId(),
                record.requesterName(),
                record.organisationId(),
                record.organisationName(),
                record.targetSubjectId(),
                record.requestedRole(),
                record.requestedScope(),
                record.requestedEnvironment(),
                record.requestedDataScope(),
                record.riskLevel(),
                record.policyPrecheckResult(),
                record.approvalsRequired(),
                record.auditTrail(),
                record.createdAt(),
                now,
                record.payload(),
                "fallback_queue",
                record.id(),
                null,
                null,
                true,
                now,
                "fallback_only"
        );
    }

    private static AdminGovernanceDtos.AccessRequestRecord mapFromSoR(JsonNode node) {
        return new AdminGovernanceDtos.AccessRequestRecord(
                node.path("id").asText(),
                node.path("requestType").asText(""),
                node.path("status").asText("PENDING"),
                node.path("requesterId").asText(""),
                node.path("requesterName").asText(""),
                textOrNull(node, "organisationId"),
                null,
                node.path("targetSubjectId").asText(""),
                node.path("requestedRole").asText(""),
                node.path("requestedScope").asText(""),
                node.path("requestedEnvironment").asText(""),
                node.path("requestedDataScope").asText(""),
                node.path("riskLevel").asText(""),
                node.path("policyPrecheckResult").asText(""),
                List.of(),
                List.of(),
                node.path("createdAt").asText(""),
                node.path("updatedAt").asText(""),
                Map.of(),
                "workforce_governance",
                node.path("fallbackRequestId").asText(null),
                node.path("id").asText(),
                node.path("downstreamCorrelationId").asText(null),
                false,
                OffsetDateTime.now().toString(),
                "synced"
        );
    }

    private static String resolveDurableId(AdminGovernanceDtos.AccessRequestRecord record) {
        return record.durableRequestId() != null ? record.durableRequestId() : record.id();
    }

    private static String textOrNull(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
