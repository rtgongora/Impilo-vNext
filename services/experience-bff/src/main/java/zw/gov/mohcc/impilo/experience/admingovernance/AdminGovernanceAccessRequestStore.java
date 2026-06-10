package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Repository
public class AdminGovernanceAccessRequestStore {

    private static final String REQUEST_PREFIX = "experience:bff:admin-gov:request:";
    private static final String INDEX_PREFIX = "experience:bff:admin-gov:requests:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl = Duration.ofDays(90);

    public AdminGovernanceAccessRequestStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public AdminGovernanceDtos.AccessRequestRecord save(String tenantId, AdminGovernanceDtos.AccessRequestRecord record) {
        try {
            String json = objectMapper.writeValueAsString(record);
            String key = requestKey(tenantId, record.id());
            redis.opsForValue().set(key, json, ttl);
            redis.opsForSet().add(indexKey(tenantId), record.id());
            redis.expire(indexKey(tenantId), ttl);
            return record;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist access request", e);
        }
    }

    public Optional<AdminGovernanceDtos.AccessRequestRecord> find(String tenantId, String requestId) {
        try {
            String json = redis.opsForValue().get(requestKey(tenantId, requestId));
            if (json == null || json.isBlank()) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, AdminGovernanceDtos.AccessRequestRecord.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public List<AdminGovernanceDtos.AccessRequestRecord> list(String tenantId) {
        Set<String> ids = redis.opsForSet().members(indexKey(tenantId));
        if (ids == null || ids.isEmpty()) return List.of();
        return ids.stream()
                .map(id -> find(tenantId, id).orElse(null))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(AdminGovernanceDtos.AccessRequestRecord::createdAt).reversed())
                .collect(Collectors.toList());
    }

    public AdminGovernanceDtos.AccessRequestRecord appendAudit(
            String tenantId,
            String requestId,
            Map<String, Object> auditEntry) {
        AdminGovernanceDtos.AccessRequestRecord current = find(tenantId, requestId)
                .orElseThrow(() -> new NoSuchElementException("Access request not found"));
        List<Map<String, Object>> trail = new ArrayList<>(current.auditTrail() != null ? current.auditTrail() : List.of());
        trail.add(auditEntry);
        AdminGovernanceDtos.AccessRequestRecord updated = new AdminGovernanceDtos.AccessRequestRecord(
                current.id(),
                current.requestType(),
                current.status(),
                current.requesterId(),
                current.requesterName(),
                current.organisationId(),
                current.organisationName(),
                current.targetSubjectId(),
                current.requestedRole(),
                current.requestedScope(),
                current.requestedEnvironment(),
                current.requestedDataScope(),
                current.riskLevel(),
                current.policyPrecheckResult(),
                current.approvalsRequired(),
                trail,
                current.createdAt(),
                OffsetDateTime.now().toString(),
                current.payload(),
                current.sourceOfRecordStatus(),
                current.fallbackRequestId(),
                current.durableRequestId(),
                current.downstreamCorrelationId(),
                current.reconciliationRequired(),
                current.lastSyncAttemptAt(),
                current.lastSyncStatus()
        );
        return save(tenantId, updated);
    }

    public static AdminGovernanceDtos.AccessRequestRecord newRecord(
            String requestType,
            String requesterId,
            String requesterName,
            Map<String, Object> payload,
            AdminGovernanceDtos.PolicyDecision decision) {
        String now = OffsetDateTime.now().toString();
        return new AdminGovernanceDtos.AccessRequestRecord(
                UUID.randomUUID().toString(),
                requestType,
                decision.approvalsRequired() != null && !decision.approvalsRequired().isEmpty() ? "PENDING" : "SUBMITTED",
                requesterId,
                requesterName,
                str(payload.get("organisationId")),
                str(payload.get("organisationName")),
                str(payload.get("targetSubjectId")),
                str(payload.get("roleTemplate")),
                str(payload.get("requestedScope")),
                str(payload.get("requestedEnvironment")),
                str(payload.get("requestedDataScope")),
                str(payload.get("riskLevel")),
                decision.allowed() ? "ALLOWED" : "DENIED",
                decision.approvalsRequired() != null ? decision.approvalsRequired() : List.of(),
                List.of(Map.of("event", "access_request.created", "at", now)),
                now,
                now,
                payload,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static String requestKey(String tenantId, String requestId) {
        return REQUEST_PREFIX + tenantId + ":" + requestId;
    }

    private static String indexKey(String tenantId) {
        return INDEX_PREFIX + tenantId;
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }
}
