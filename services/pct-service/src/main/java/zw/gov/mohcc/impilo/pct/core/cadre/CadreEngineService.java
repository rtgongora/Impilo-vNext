package zw.gov.mohcc.impilo.pct.core.cadre;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.CadreDecisionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.CadreDecisionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Application service around the pure {@link CadreEngine}: resolves the C9 decision, stamps the
 * {@code auditRef}, persists the resolution as an audit row, and emits an outbox event.
 *
 * <p>Authorization is enforced upstream at the Envoy ext_authz layer (TSHEPO) before the request reaches
 * PCT. This service does <strong>not</strong> author policy; the relevant rule is policy {@code CADRE-ACTION}
 * (track P / WS-OPA {@code impilo.authz}) — see {@code tshepo-policy-contract-list.md}. When a downstream
 * action requires a Tshepo check at execution, the cockpit calls the existing ext_authz path.</p>
 */
@Service
public class CadreEngineService {

    private static final Logger log = LoggerFactory.getLogger(CadreEngineService.class);

    private final CadreDecisionRepository decisionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public CadreEngineService(CadreDecisionRepository decisionRepository,
                              EventOutboxRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.decisionRepository = decisionRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Resolve a Cadre Engine decision and audit it.
     *
     * @param request    the C9 input
     * @param journeyId  optional journey correlation (may be {@code null})
     * @param encounterId optional encounter correlation (may be {@code null})
     * @return the decision, with {@code auditRef} stamped
     */
    @Transactional
    public CadreDecision resolveAndAudit(CadreDecisionRequest request, String journeyId, String encounterId) {
        TrustContext ctx = TrustContextHolder.require();
        CadreDecision base = CadreEngine.resolve(request);

        UUID auditRef = UUID.randomUUID();
        CadreDecision decided = new CadreDecision(
                base.permittedWorkflows(), base.cockpitSpine(), base.escalation(), auditRef.toString());

        CadreDecisionEntity row = new CadreDecisionEntity();
        row.setAuditRef(auditRef);
        row.setTenantId(ctx.tenantId());
        row.setActorId(ctx.actorId());
        row.setCorrelationId(ctx.correlationId());
        row.setJourneyId(journeyId);
        row.setEncounterId(encounterId);
        row.setInRole(request.roleOrUnknown());
        row.setInCadre(request.cadreOrUnknown());
        row.setInScope(request.scopeOrFacility());
        row.setInVisitType(request.visitTypeOrGeneral());
        row.setInAcuity(request.acuity());
        row.setInContext(request.contextOrOutpatient());
        row.setInAccessState(request.accessStateOrUnknown());
        row.setPermittedWorkflows(toJson(decided.permittedWorkflows()));
        row.setCockpitSpine(toJson(decided.cockpitSpine()));
        row.setEscalation(toJson(decided.escalation()));
        decisionRepository.save(row);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("auditRef", auditRef.toString());
        payload.put("actorId", ctx.actorId());
        payload.put("journeyId", journeyId);
        payload.put("encounterId", encounterId);
        payload.put("cadre", request.cadreOrUnknown());
        payload.put("context", request.contextOrOutpatient());
        payload.put("acuity", request.acuityClamped());
        payload.put("permittedWorkflows", decided.permittedWorkflows());
        writeOutbox("CADRE_DECISION", auditRef.toString(), "CADRE_DECISION_RESOLVED", toJson(payload), ctx.tenantId());

        log.info("pct.cadre.resolved auditRef={} actor={} cadre={} context={} acuity={} workflows={} correlationId={}",
                auditRef, ctx.actorId(), request.cadreOrUnknown(), request.contextOrOutpatient(),
                request.acuityClamped(), decided.permittedWorkflows(), ctx.correlationId());

        return decided;
    }

    private void writeOutbox(String aggregateType, String aggregateId, String eventType,
                             String payloadJson, UUID tenantId) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(tenantId);
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise cadre payload: {}", e.getMessage());
            return "{}";
        }
    }
}
