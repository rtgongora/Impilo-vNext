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
    private final FhirForwarder fhirForwarder;
    private final ForwardAuditRecorder auditRecorder;
    /**
     * Optional environment-wide default FHIR base (e.g. {@code http://hapi-fhir:8090/fhir}). When
     * set and no explicit per-tenant {@code fhir_route} row matches, the gateway forwards to
     * {@code {base}/{resourceType}}. Empty (the default) preserves the strict NO_ROUTE behaviour.
     * This makes governed FHIR writes work with a single config value while per-tenant route rows
     * still override, instead of requiring a route row per (tenant × resource type).
     */
    private final String defaultTargetBase;

    public GatewayForwardService(FhirRouteRepository routeRepository,
                                 FhirAuditLogRepository auditLogRepository,
                                 EventOutboxRepository outboxRepository,
                                 ConsentEnforcementService consentEnforcementService,
                                 FhirForwarder fhirForwarder,
                                 ForwardAuditRecorder auditRecorder,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${fhir-gateway.default-target-base:}") String defaultTargetBase) {
        this.routeRepository = routeRepository;
        this.auditLogRepository = auditLogRepository;
        this.outboxRepository = outboxRepository;
        this.consentEnforcementService = consentEnforcementService;
        this.fhirForwarder = fhirForwarder;
        this.auditRecorder = auditRecorder;
        this.defaultTargetBase = defaultTargetBase == null ? "" : defaultTargetBase.trim();
    }

    /**
     * Not {@code @Transactional}. This method makes two HTTP calls — consent, then the destination
     * FHIR server — and a database transaction spanning them would hold a Postgres connection for
     * the duration of both, on every forward. Persistence is delegated to
     * {@link ForwardAuditRecorder}, which is a separate bean precisely because Spring does not
     * proxy self-invocation: a {@code @Transactional} private method here would have been
     * annotation without effect.
     */
    public ForwardResult forward(UUID tenantId, String actorId, UUID correlationId,
                                 String sourceIp, String resourceType,
                                 String operation, String payload,
                                 String subjectCpid, String purposeOfUse) {

        // ── Consent enforcement (Health OS: Privacy by Architecture) ──
        // The operation is load-bearing: a write proceeds when no directive speaks to the point,
        // a read does not. Only DENY refuses — PERMIT_NO_DIRECTIVE proceeds and records on the
        // audit row that it did so in the absence of a directive, not on the authority of one.
        ConsentOutcome consentOutcome = consentEnforcementService.evaluate(
                actorId, subjectCpid, resourceType, purposeOfUse, tenantId, operation);

        if (consentOutcome == ConsentOutcome.DENY) {
            log.warn("Consent DENIED: actor={} subject={} resourceType={} tenant={}",
                    actorId, subjectCpid, resourceType, tenantId);

            FhirAuditLogEntity auditLog = auditRecorder.record(
                    buildAuditLog(tenantId, resourceType, operation, sourceIp, actorId,
                            "CONSENT_DENIED", consentOutcome, correlationId, subjectCpid, null, null),
                    saved -> buildOutboxEvent(saved, operation, "CONSENT_DENIED", null,
                            tenantId, correlationId));

            return new ForwardResult(auditLog.getId(), resourceType, operation,
                    "CONSENT_DENIED", null, correlationId, consentOutcome.name(), null,
                    "refused before any call was made");
        }

        // ── Route lookup ──
        List<FhirRouteEntity> routes = routeRepository
                .findByTenantIdAndResourceTypeAndEnabledTrue(tenantId, resourceType);

        String outcome;
        String targetEndpoint;
        Integer downstreamStatus = null;
        String downstreamDetail = null;

        if (routes.isEmpty() && defaultTargetBase.isEmpty()) {
            outcome = "NO_ROUTE";
            targetEndpoint = null;
            log.warn("No active route found for tenant={} resourceType={}", tenantId, resourceType);
        } else {
            if (routes.isEmpty()) {
                // Environment-wide default: {base}/{resourceType}.
                targetEndpoint = defaultTargetBase.replaceAll("/+$", "") + "/" + resourceType;
                log.info("No explicit route for tenant={} resourceType={}; using default target {}",
                        tenantId, resourceType, targetEndpoint);
            } else {
                targetEndpoint = routes.get(0).getTargetEndpoint();
            }
            log.info("Forwarding {} {} to {} for tenant={}", operation, resourceType,
                    targetEndpoint, tenantId);
            // Actually deliver the resource downstream and record the REAL outcome — previously
            // SUCCESS was recorded without any HTTP call (the resource was never forwarded).
            FhirForwarder.ForwardAttempt attempt =
                    fhirForwarder.send(targetEndpoint, resourceType, operation, payload);
            outcome = attempt.delivered() ? "SUCCESS" : "FORWARD_FAILED";
            // The status was already known here and discarded. A transport failure reports 0, which
            // is not an HTTP status and must stay distinguishable from one the server actually sent.
            downstreamStatus = attempt.status() == 0 ? null : attempt.status();
            downstreamDetail = attempt.detail();
        }

        final String finalOutcome = outcome;
        final String finalTarget = targetEndpoint;
        FhirAuditLogEntity auditLog = auditRecorder.record(
                buildAuditLog(tenantId, resourceType, operation, sourceIp, actorId, outcome,
                        consentOutcome, correlationId, subjectCpid, targetEndpoint, downstreamStatus),
                saved -> buildOutboxEvent(saved, operation, finalOutcome, finalTarget,
                        tenantId, correlationId));

        return new ForwardResult(auditLog.getId(), resourceType, operation,
                outcome, targetEndpoint, correlationId, consentOutcome.name(),
                downstreamStatus, downstreamDetail);
    }

    /**
     * Backward-compatible overload for callers that do not yet supply consent parameters.
     */
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
                                              UUID correlationId, String subjectCpid,
                                              String targetEndpoint, Integer downstreamStatus) {
        FhirAuditLogEntity auditLog = new FhirAuditLogEntity();
        auditLog.setTenantId(tenantId);
        auditLog.setResourceType(resourceType);
        auditLog.setOperation(operation);
        auditLog.setSourceIp(sourceIp);
        auditLog.setActorId(actorId);
        auditLog.setOutcome(outcome);
        auditLog.setConsentOutcome(consentOutcome != null ? consentOutcome.name() : null);
        auditLog.setCorrelationId(correlationId);
        auditLog.setSubjectCpid(subjectCpid);
        auditLog.setTargetEndpoint(targetEndpoint);
        auditLog.setDownstreamStatus(downstreamStatus);
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

    /**
     * @param downstreamStatus the HTTP status the destination returned, or null when no call was
     *                         made (CONSENT_DENIED, NO_ROUTE) or the failure was transport-level.
     *                         Callers MUST be able to tell a 409 from a 422 from "unreachable":
     *                         offline-edge's conflict-review path turns on exactly that, and
     *                         collapsing them is why every 409 it saw degraded silently to FAILED.
     */
    public record ForwardResult(
            Long auditLogId,
            String resourceType,
            String operation,
            String outcome,
            String targetEndpoint,
            UUID correlationId,
            String consentOutcome,
            Integer downstreamStatus,
            String downstreamDetail
    ) {}
}
