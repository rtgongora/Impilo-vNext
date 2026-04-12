package zw.gov.mohcc.impilo.fhirgateway.core;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirAuditLogEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.entity.FhirRouteEntity;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirAuditLogRepository;
import zw.gov.mohcc.impilo.fhirgateway.persistence.repository.FhirRouteRepository;

@Service
public class GatewayForwardService {

    private static final Logger log = LoggerFactory.getLogger(GatewayForwardService.class);

    private final FhirRouteRepository routeRepository;
    private final FhirAuditLogRepository auditLogRepository;
    private final EventOutboxRepository outboxRepository;
    private final ConsentEnforcementService consentEnforcementService;

    public GatewayForwardService(FhirRouteRepository routeRepository,
                                 FhirAuditLogRepository auditLogRepository,
                                 EventOutboxRepository outboxRepository,
                                 ConsentEnforcementService consentEnforcementService) {
        this.routeRepository = routeRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxRepository = outboxRepository;
        this.consentEnforcementService = consentEnforcementService;
    }

    @Transactional
    public ForwardResult forward(UUID tenantId, String actorId, UUID correlationId,
                                 String sourceIp, String resourceType,
                                 String operation, String payload,
                                 String subjectCpid, String purposeOfUse) {

        // ── Consent enforcement (Health OS: Privacy by Architecture) ──
        ConsentOutcome consentOutcome = consentEnforcementService.evaluate(
                actorId, subjectCpid, resourceType, purposeOfUse, tenantId);

        if (consentOutcome == ConsentOutcome.DENY) {
            log.warn("Consent DENIED: actor={} subject={} resourceType={} tenant={}",
                    actorId, subjectCpid, resourceType, tenantId);

            FhirAuditLogEntity auditLog = buildAuditLog(tenantId, resourceType, operation,
                    sourceIp, actorId, "CONSENT_DENIED", consentOutcome, correlationId);
            auditLogRepository.save(auditLog);

            EventOutboxEntity event = buildOutboxEvent(auditLog, operation,
                    "CONSENT_DENIED", null, tenantId, correlationId);
            outboxRepository.save(event);

            return new ForwardResult(auditLog.getId(), resourceType, operation,
                    "CONSENT_DENIED", null, correlationId, consentOutcome.name());
        }

        // ── Route lookup ──
        List<FhirRouteEntity> routes = routeRepository
                .findByTenantIdAndResourceTypeAndEnabledTrue(tenantId, resourceType);

        String outcome;
        String targetEndpoint;

        if (routes.isEmpty()) {
            outcome = "NO_ROUTE";
            targetEndpoint = null;
            log.warn("No active route found for tenant={} resourceType={}", tenantId, resourceType);
        } else {
            FhirRouteEntity route = routes.get(0);
            targetEndpoint = route.getTargetEndpoint();
            outcome = "SUCCESS";
            log.info("Forwarding {} {} to {} for tenant={}", operation, resourceType,
                    targetEndpoint, tenantId);
        }

        FhirAuditLogEntity auditLog = buildAuditLog(tenantId, resourceType, operation,
                sourceIp, actorId, outcome, consentOutcome, correlationId);
        auditLogRepository.save(auditLog);

        EventOutboxEntity event = buildOutboxEvent(auditLog, operation,
                outcome, targetEndpoint, tenantId, correlationId);
        outboxRepository.save(event);

        return new ForwardResult(auditLog.getId(), resourceType, operation,
                outcome, targetEndpoint, correlationId, consentOutcome.name());
    }

    /**
     * Backward-compatible overload for callers that do not yet supply consent parameters.
     */
    @Transactional
    public ForwardResult forward(UUID tenantId, String actorId, UUID correlationId,
                                 String sourceIp, String resourceType,
                                 String operation, String payload) {
        return forward(tenantId, actorId, correlationId, sourceIp,
                resourceType, operation, payload, null, null);
    }

    private FhirAuditLogEntity buildAuditLog(UUID tenantId, String resourceType,
                                              String operation, String sourceIp,
                                              String actorId, String outcome,
                                              ConsentOutcome consentOutcome,
                                              UUID correlationId) {
        FhirAuditLogEntity auditLog = new FhirAuditLogEntity();
        auditLog.setTenantId(tenantId);
        auditLog.setResourceType(resourceType);
        auditLog.setOperation(operation);
        auditLog.setSourceIp(sourceIp);
        auditLog.setActorId(actorId);
        auditLog.setOutcome(outcome);
        auditLog.setConsentOutcome(consentOutcome != null ? consentOutcome.name() : null);
        auditLog.setCorrelationId(correlationId);
        return auditLog;
    }

    private EventOutboxEntity buildOutboxEvent(FhirAuditLogEntity auditLog,
                                                String operation, String outcome,
                                                String targetEndpoint,
                                                UUID tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FHIR_REQUEST");
        event.setAggregateId(auditLog.getId().toString());
        event.setEventType("FHIR_" + operation + "_FORWARDED");
        event.setPayload(buildForwardPayload(auditLog.getResourceType(), operation,
                outcome, targetEndpoint));
        event.setTenantId(tenantId.toString());
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        event.setSubjectType("FhirAuditLog");
        event.setSubjectId(auditLog.getId().toString());
        event.setPartitionKey(tenantId.toString());
        return event;
    }

    private String buildForwardPayload(String resourceType, String operation,
                                       String outcome, String targetEndpoint) {
        return "{\"resourceType\":\"" + resourceType + "\""
                + ",\"operation\":\"" + operation + "\""
                + ",\"outcome\":\"" + outcome + "\""
                + ",\"targetEndpoint\":" + (targetEndpoint != null ? "\"" + targetEndpoint + "\"" : "null")
                + "}";
    }

    public record ForwardResult(
            Long auditLogId,
            String resourceType,
            String operation,
            String outcome,
            String targetEndpoint,
            UUID correlationId,
            String consentOutcome
    ) {}
}
