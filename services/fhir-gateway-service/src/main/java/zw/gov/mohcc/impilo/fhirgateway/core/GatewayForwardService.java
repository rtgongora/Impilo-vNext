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

    public GatewayForwardService(FhirRouteRepository routeRepository,
                                 FhirAuditLogRepository auditLogRepository,
                                 EventOutboxRepository outboxRepository) {
        this.routeRepository = routeRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public ForwardResult forward(UUID tenantId, String actorId, UUID correlationId,
                                 String sourceIp, String resourceType,
                                 String operation, String payload) {
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

        FhirAuditLogEntity auditLog = new FhirAuditLogEntity();
        auditLog.setTenantId(tenantId);
        auditLog.setResourceType(resourceType);
        auditLog.setOperation(operation);
        auditLog.setSourceIp(sourceIp);
        auditLog.setActorId(actorId);
        auditLog.setOutcome(outcome);
        auditLog.setCorrelationId(correlationId);
        auditLogRepository.save(auditLog);

        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FHIR_REQUEST");
        event.setAggregateId(auditLog.getId().toString());
        event.setEventType("FHIR_" + operation + "_FORWARDED");
        event.setPayload(buildForwardPayload(resourceType, operation, outcome, targetEndpoint));
        event.setTenantId(tenantId.toString());
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        event.setSubjectType("FhirAuditLog");
        event.setSubjectId(auditLog.getId().toString());
        event.setPartitionKey(tenantId.toString());
        outboxRepository.save(event);

        return new ForwardResult(auditLog.getId(), resourceType, operation,
                outcome, targetEndpoint, correlationId);
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
            UUID correlationId
    ) {}
}
