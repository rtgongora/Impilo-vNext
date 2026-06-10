package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestEntity;
import zw.gov.mohcc.impilo.governance.persistence.AccessRequestRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.*;

@Service
public class AccessRequestService {

    private final AccessRequestRepository repository;
    private final GovernanceEventService governanceEventService;
    private final ObjectMapper objectMapper;

    public AccessRequestService(AccessRequestRepository repository,
                                GovernanceEventService governanceEventService,
                                ObjectMapper objectMapper) {
        this.repository = repository;
        this.governanceEventService = governanceEventService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public AccessRequestEntity create(UUID tenantId, Map<String, Object> request) {
        TrustContext ctx = TrustContextHolder.require();
        AccessRequestEntity entity = new AccessRequestEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setRequestType(str(request.get("requestType")));
        entity.setStatus(strOrDefault(request.get("status"), "PENDING"));
        entity.setRequesterId(str(request.get("requesterId")));
        entity.setRequesterName(str(request.get("requesterName")));
        entity.setOrganisationId(parseUuid(request.get("organisationId")));
        entity.setTargetSubjectId(str(request.get("targetSubjectId")));
        entity.setRequestedRole(str(request.get("requestedRole")));
        entity.setRequestedScope(str(request.get("requestedScope")));
        entity.setRequestedEnvironment(str(request.get("requestedEnvironment")));
        entity.setRequestedDataScope(str(request.get("requestedDataScope")));
        entity.setRiskLevel(str(request.get("riskLevel")));
        entity.setPolicyPrecheckResult(str(request.get("policyPrecheckResult")));
        entity.setApprovalsRequired(writeJson(request.get("approvalsRequired")));
        entity.setPayload(writeJson(request.get("payload")));
        entity.setAuditTrail(writeJson(List.of(Map.of("event", "access_request.created", "by", ctx.actorId()))));
        entity.setFallbackRequestId(str(request.get("fallbackRequestId")));
        entity.initTimestamps();
        entity = repository.save(entity);
        emit(entity, "impilo.governance.access_request.created");
        return entity;
    }

    public List<AccessRequestEntity> list(UUID tenantId) {
        return repository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    public AccessRequestEntity get(UUID id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Access request not found"));
    }

    @Transactional
    public AccessRequestEntity transition(UUID id, String newStatus, String notes, String actorId) {
        AccessRequestEntity entity = get(id);
        entity.setStatus(newStatus);
        appendTrail(entity, Map.of("event", "access_request." + newStatus.toLowerCase(Locale.ROOT), "by", actorId, "notes", notes != null ? notes : ""));
        entity = repository.save(entity);
        emit(entity, "impilo.governance.access_request.updated");
        return entity;
    }

    private void appendTrail(AccessRequestEntity entity, Map<String, Object> entry) {
        try {
            List<Map<String, Object>> trail = new ArrayList<>();
            if (entity.getAuditTrail() != null && !entity.getAuditTrail().isBlank()) {
                trail.addAll(objectMapper.readValue(entity.getAuditTrail(), List.class));
            }
            trail.add(entry);
            entity.setAuditTrail(objectMapper.writeValueAsString(trail));
        } catch (Exception e) {
            entity.setAuditTrail(writeJson(List.of(entry)));
        }
    }

    private void emit(AccessRequestEntity entity, String eventType) {
        governanceEventService.enqueue("ACCESS_REQUEST", entity.getId().toString(), eventType,
                Map.of("requestId", entity.getId().toString(), "status", entity.getStatus(), "requestType", entity.getRequestType()),
                entity.getTenantId(),
                TrustContextHolder.get() != null && TrustContextHolder.get().correlationId() != null
                        ? TrustContextHolder.get().correlationId().toString() : null);
    }

    private String writeJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static String strOrDefault(Object value, String defaultValue) {
        String s = str(value);
        return s == null || s.isBlank() ? defaultValue : s;
    }

    private static UUID parseUuid(Object value) {
        if (value == null) return null;
        try {
            return UUID.fromString(value.toString());
        } catch (Exception e) {
            return null;
        }
    }
}
