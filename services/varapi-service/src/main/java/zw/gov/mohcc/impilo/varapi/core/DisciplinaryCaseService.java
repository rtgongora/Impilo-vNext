package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.DisciplinaryActionRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderDisciplinaryCaseRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Provider disciplinary casework — previously a dead entity with no service/controller (R2/G9).
 * Opens cases, records disciplinary actions (an interim suspension/revocation also writes a
 * {@link DisciplinaryActionEntity} so PIC eligibility — which reads that table — reflects it), and
 * closes cases with a decision. Emits outbox events for downstream (Tuso PIC mirror, surveillance).
 */
@Service
public class DisciplinaryCaseService {

    private static final Logger log = LoggerFactory.getLogger(DisciplinaryCaseService.class);
    /** Action types that block PIC eligibility (PicEligibilityAssessmentService keys on these). */
    private static final Set<String> BLOCKING_ACTIONS = Set.of("SUSPENSION", "REVOCATION");

    private final ProviderDisciplinaryCaseRepository caseRepository;
    private final DisciplinaryActionRepository actionRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public DisciplinaryCaseService(ProviderDisciplinaryCaseRepository caseRepository,
                                   DisciplinaryActionRepository actionRepository,
                                   ProviderRepository providerRepository,
                                   EventOutboxRepository outboxRepository) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public ProviderDisciplinaryCaseEntity openCase(Long providerId, String triggerType, String summary,
                                                   String routeToAuthority) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderEntity provider = providerRepository.findByIdAndTenantId(providerId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));
        if (triggerType == null || triggerType.isBlank()) {
            throw new IllegalArgumentException("A trigger type is required to open a disciplinary case");
        }

        ProviderDisciplinaryCaseEntity c = new ProviderDisciplinaryCaseEntity();
        c.setProvider(provider);
        c.setTenantId(ctx.tenantId());
        c.setTriggerType(triggerType.trim().toUpperCase(java.util.Locale.ROOT));
        c.setStatus("OPEN");
        c.setOpenedDate(LocalDate.now());
        c.setSummary(summary);
        c.setRouteToAuthority(routeToAuthority);
        c = caseRepository.save(c);

        publishEvent(c.getId().toString(), "varapi.disciplinary.case.opened",
                String.format("{\"caseId\":%d,\"providerId\":%d,\"triggerType\":\"%s\"}",
                        c.getId(), providerId, c.getTriggerType()));
        log.info("Disciplinary case opened: caseId={}, providerId={}, trigger={}", c.getId(), providerId, c.getTriggerType());
        return c;
    }

    /**
     * Record a disciplinary action on an open case. A SUSPENSION/REVOCATION also writes a
     * DisciplinaryActionEntity (ACTIVE) so the PIC-eligibility read-side reflects the sanction.
     */
    @Transactional
    public DisciplinaryActionEntity recordAction(Long caseId, String actionType, String severity,
                                                 String description, LocalDate expiryDate) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderDisciplinaryCaseEntity c = caseRepository.findByIdAndTenantId(caseId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Disciplinary case not found: " + caseId));
        if (!"OPEN".equalsIgnoreCase(c.getStatus())) {
            throw new IllegalStateException("Cannot record an action on a closed disciplinary case: " + caseId);
        }
        String type = actionType == null ? "" : actionType.trim().toUpperCase(java.util.Locale.ROOT);

        DisciplinaryActionEntity action = new DisciplinaryActionEntity();
        action.setProvider(c.getProvider());
        action.setTenantId(ctx.tenantId());
        action.setActionType(type);
        action.setSeverity(severity);
        action.setDescription(description);
        action.setStatus("ACTIVE");
        action.setImposedDate(LocalDate.now());
        action.setExpiryDate(expiryDate);
        action.setImposedBy(ctx.actorId());
        action = actionRepository.save(action);

        publishEvent(c.getId().toString(), "varapi.disciplinary.case.actioned",
                String.format("{\"caseId\":%d,\"actionId\":%d,\"actionType\":\"%s\",\"blocksPic\":%b}",
                        c.getId(), action.getId(), type, BLOCKING_ACTIONS.contains(type)));
        log.info("Disciplinary action recorded: caseId={}, actionType={}, blocksPic={}",
                caseId, type, BLOCKING_ACTIONS.contains(type));
        return action;
    }

    @Transactional
    public ProviderDisciplinaryCaseEntity closeCase(Long caseId, String finalDecision, String finalRecommendation) {
        TrustContext ctx = TrustContextHolder.require();
        ProviderDisciplinaryCaseEntity c = caseRepository.findByIdAndTenantId(caseId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Disciplinary case not found: " + caseId));
        if (finalDecision == null || finalDecision.isBlank()) {
            throw new IllegalArgumentException("A final decision is required to close a disciplinary case");
        }
        c.setStatus("CLOSED");
        c.setFinalDecision(finalDecision);
        c.setFinalRecommendation(finalRecommendation);
        c.setFinalDecisionDate(LocalDate.now());
        c = caseRepository.save(c);

        publishEvent(c.getId().toString(), "varapi.disciplinary.case.closed",
                String.format("{\"caseId\":%d,\"finalDecision\":\"%s\"}", c.getId(), finalDecision));
        return c;
    }

    @Transactional(readOnly = true)
    public List<ProviderDisciplinaryCaseEntity> listByProvider(Long providerId) {
        return caseRepository.findByProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public List<ProviderDisciplinaryCaseEntity> listOpen() {
        return caseRepository.findByTenantIdAndStatus(TrustContextHolder.require().tenantId(), "OPEN");
    }

    @Transactional(readOnly = true)
    public ProviderDisciplinaryCaseEntity getCase(Long caseId) {
        return caseRepository.findByIdAndTenantId(caseId, TrustContextHolder.require().tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Disciplinary case not found: " + caseId));
    }

    private void publishEvent(String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("PROVIDER_DISCIPLINARY");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}
