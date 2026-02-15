package zw.gov.mohcc.impilo.dags.core;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessDecisionEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;
import zw.gov.mohcc.impilo.dags.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.dags.persistence.repository.AccessDecisionRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.AccessRequestRepository;
import zw.gov.mohcc.impilo.dags.persistence.repository.EventOutboxRepository;

@Service
public class AccessRequestService {

    private final AccessRequestRepository requestRepository;
    private final AccessDecisionRepository decisionRepository;
    private final EventOutboxRepository outboxRepository;
    private final EnforcementService enforcementService;
    private final AuditService auditService;

    public AccessRequestService(AccessRequestRepository requestRepository,
                                AccessDecisionRepository decisionRepository,
                                EventOutboxRepository outboxRepository,
                                EnforcementService enforcementService,
                                AuditService auditService) {
        this.requestRepository = requestRepository;
        this.decisionRepository = decisionRepository;
        this.outboxRepository = outboxRepository;
        this.enforcementService = enforcementService;
        this.auditService = auditService;
    }

    @Transactional
    public AccessRequestEntity submit(UUID tenantId, String actorId, UUID correlationId,
                                      String resourceType, String resourceId,
                                      String purpose, String justification) {
        AccessRequestEntity request = new AccessRequestEntity();
        request.setTenantId(tenantId);
        request.setRequesterId(actorId);
        request.setResourceType(resourceType);
        request.setResourceId(resourceId);
        request.setPurpose(purpose);
        request.setJustification(justification);
        request = requestRepository.save(request);

        publishEvent("ACCESS_REQUEST", request.getId().toString(), "ACCESS_REQUESTED",
                buildRequestPayload(request), tenantId.toString(), correlationId);

        auditService.log(tenantId, "ACCESS_REQUESTED", actorId,
                resourceType, resourceId,
                "{\"requestId\":" + request.getId() + ",\"purpose\":\"" + purpose + "\"}",
                correlationId);

        return request;
    }

    @Transactional
    public AccessRequestEntity approve(Long requestId, UUID tenantId, String actorId,
                                       UUID correlationId, String reason) {
        AccessRequestEntity request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + requestId));

        if (request.getStatus() != AccessRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Request is not in SUBMITTED status, current: " + request.getStatus());
        }

        String permitToken = enforcementService.issuePermitToken(requestId, tenantId, request.getRequesterId());

        request.setStatus(AccessRequestStatus.APPROVED);
        request.setPermitToken(permitToken);
        request = requestRepository.save(request);

        AccessDecisionEntity decision = new AccessDecisionEntity();
        decision.setTenantId(tenantId);
        decision.setRequestId(requestId);
        decision.setDecision(DecisionType.APPROVED);
        decision.setDecidedBy(actorId);
        decision.setReason(reason);
        decisionRepository.save(decision);

        publishEvent("ACCESS_REQUEST", requestId.toString(), "ACCESS_APPROVED",
                buildApprovalPayload(request, permitToken), tenantId.toString(), correlationId);

        auditService.log(tenantId, "ACCESS_APPROVED", actorId,
                request.getResourceType(), request.getResourceId(),
                "{\"requestId\":" + requestId + ",\"reason\":\"" + (reason != null ? reason : "") + "\"}",
                correlationId);

        return request;
    }

    @Transactional
    public AccessRequestEntity deny(Long requestId, UUID tenantId, String actorId,
                                    UUID correlationId, String reason) {
        AccessRequestEntity request = requestRepository.findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + requestId));

        if (request.getStatus() != AccessRequestStatus.SUBMITTED) {
            throw new IllegalStateException("Request is not in SUBMITTED status, current: " + request.getStatus());
        }

        request.setStatus(AccessRequestStatus.DENIED);
        request = requestRepository.save(request);

        AccessDecisionEntity decision = new AccessDecisionEntity();
        decision.setTenantId(tenantId);
        decision.setRequestId(requestId);
        decision.setDecision(DecisionType.DENIED);
        decision.setDecidedBy(actorId);
        decision.setReason(reason);
        decisionRepository.save(decision);

        publishEvent("ACCESS_REQUEST", requestId.toString(), "ACCESS_DENIED",
                buildDenialPayload(request, reason), tenantId.toString(), correlationId);

        auditService.log(tenantId, "ACCESS_DENIED", actorId,
                request.getResourceType(), request.getResourceId(),
                "{\"requestId\":" + requestId + ",\"reason\":\"" + (reason != null ? reason : "") + "\"}",
                correlationId);

        return request;
    }

    @Transactional(readOnly = true)
    public Page<AccessRequestEntity> list(UUID tenantId, AccessRequestStatus status, Pageable pageable) {
        if (status != null) {
            return requestRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return requestRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public AccessRequestEntity findById(Long id, UUID tenantId) {
        return requestRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Access request not found: " + id));
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }

    private String buildRequestPayload(AccessRequestEntity r) {
        return "{\"id\":" + r.getId()
                + ",\"tenantId\":\"" + r.getTenantId() + "\""
                + ",\"requesterId\":\"" + r.getRequesterId() + "\""
                + ",\"resourceType\":\"" + r.getResourceType() + "\""
                + ",\"resourceId\":\"" + (r.getResourceId() != null ? r.getResourceId() : "") + "\""
                + ",\"purpose\":\"" + r.getPurpose() + "\""
                + ",\"status\":\"" + r.getStatus() + "\""
                + "}";
    }

    private String buildApprovalPayload(AccessRequestEntity r, String permitToken) {
        return "{\"id\":" + r.getId()
                + ",\"tenantId\":\"" + r.getTenantId() + "\""
                + ",\"requesterId\":\"" + r.getRequesterId() + "\""
                + ",\"status\":\"APPROVED\""
                + ",\"permitToken\":\"" + permitToken + "\""
                + "}";
    }

    private String buildDenialPayload(AccessRequestEntity r, String reason) {
        return "{\"id\":" + r.getId()
                + ",\"tenantId\":\"" + r.getTenantId() + "\""
                + ",\"requesterId\":\"" + r.getRequesterId() + "\""
                + ",\"status\":\"DENIED\""
                + ",\"reason\":\"" + (reason != null ? reason : "") + "\""
                + "}";
    }
}
