package zw.gov.mohcc.impilo.varapi.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderComplianceActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderComplianceActionRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Service managing provider compliance actions.
 * Tracks outstanding requirements, due dates, and resolution.
 */
@Service
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final ProviderComplianceActionRepository complianceRepository;
    private final ProviderRepository providerRepository;
    private final EventOutboxRepository outboxRepository;

    public ComplianceService(
            ProviderComplianceActionRepository complianceRepository,
            ProviderRepository providerRepository,
            EventOutboxRepository outboxRepository) {
        this.complianceRepository = complianceRepository;
        this.providerRepository = providerRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Create a compliance action.
     */
    @Transactional
    public ProviderComplianceActionEntity createAction(
            Long providerId,
            String actionType,
            String description,
            String owner,
            LocalDate dueDate,
            Long relatedApplicationId) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating compliance action: providerId={}, type={}, actor={}",
                providerId, actionType, ctx.actorId());

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        ProviderComplianceActionEntity action = new ProviderComplianceActionEntity();
        action.setProvider(provider);
        action.setTenantId(ctx.tenantId());
        action.setActionType(actionType);
        action.setDescription(description);
        action.setOwner(owner);
        action.setDueDate(dueDate);
        action.setStatus("OPEN");

        action = complianceRepository.save(action);

        log.info("Compliance action created: id={}, providerId={}, type={}",
                action.getId(), providerId, actionType);
        publishEvent("PROVIDER_COMPLIANCE", action.getId().toString(),
                "varapi.compliance.action.created",
                String.format("{\"actionId\":%d,\"providerId\":%d,\"type\":\"%s\",\"dueDate\":\"%s\"}",
                        action.getId(), providerId, actionType, dueDate != null ? dueDate.toString() : "none"));

        return action;
    }

    /**
     * Mark compliance action as resolved.
     */
    @Transactional
    public ProviderComplianceActionEntity resolveAction(
            Long actionId,
            String completionNotes,
            String verifiedBy) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Resolving compliance action: id={}, actor={}", actionId, ctx.actorId());

        ProviderComplianceActionEntity action = complianceRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionId));

        if (!"OPEN".equals(action.getStatus())) {
            throw new IllegalStateException("Can only resolve open actions");
        }

        action.setStatus("RESOLVED");
        action.setCompletionNotes(completionNotes);
        action.setVerifiedBy(verifiedBy != null ? verifiedBy : ctx.actorId());
        action.setVerifiedAt(Instant.now());

        action = complianceRepository.save(action);

        log.info("Compliance action resolved: id={}", actionId);
        publishEvent("PROVIDER_COMPLIANCE", action.getId().toString(),
                "varapi.compliance.action.resolved",
                String.format("{\"actionId\":%d,\"providerId\":%d}",
                        actionId, action.getProvider().getId()));

        return action;
    }

    /**
     * Get open compliance actions for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getOpenActions(Long providerId) {
        return complianceRepository.findByProviderIdAndStatus(providerId, "OPEN");
    }

    /**
     * Get all compliance actions for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getActionsByProvider(Long providerId) {
        return complianceRepository.findByProviderId(providerId);
    }

    /**
     * Get overdue compliance actions.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getOverdueActions() {
        return complianceRepository.findByStatusAndDueDateBefore("OPEN", LocalDate.now());
    }

    /**
     * Get compliance actions by type.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getActionsByType(String actionType) {
        TrustContext ctx = TrustContextHolder.require();
        return complianceRepository.findByTenantIdAndActionType(ctx.tenantId(), actionType);
    }

    /**
     * Check if provider has any open compliance issues.
     */
    @Transactional(readOnly = true)
    public boolean hasOpenComplianceIssues(Long providerId) {
        List<ProviderComplianceActionEntity> open = complianceRepository.findByProviderIdAndStatus(providerId, "OPEN");
        return !open.isEmpty();
    }

    /**
     * Get count of open compliance issues.
     */
    @Transactional(readOnly = true)
    public int getOpenIssueCount(Long providerId) {
        List<ProviderComplianceActionEntity> open = complianceRepository.findByProviderIdAndStatus(providerId, "OPEN");
        return open.size();
    }

    /**
     * Create a compliance action (from controller).
     */
    @Transactional
    public ProviderComplianceActionEntity createComplianceAction(
            Long providerId,
            String complianceActionType,
            String description,
            String requirement,
            LocalDate dueDate,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating compliance action: providerId={}, type={}", providerId, complianceActionType);

        ProviderEntity provider = providerRepository.findById(providerId)
                .orElseThrow(() -> new IllegalArgumentException("Provider not found: " + providerId));

        ProviderComplianceActionEntity action = new ProviderComplianceActionEntity();
        action.setProvider(provider);
        action.setTenantId(ctx.tenantId());
        action.setActionType(complianceActionType);
        action.setDescription(description);
        action.setRequirement(requirement);
        action.setDueDate(dueDate);
        action.setStatus("OPEN");
        action.setCompletionNotes(notes);
        action.setVerifiedBy(ctx.actorId());
        action.setUpdatedBy(ctx.actorId());

        action = complianceRepository.save(action);
        log.info("Compliance action created: id={}", action.getId());
        return action;
    }

    /**
     * Fulfill a compliance action.
     */
    @Transactional
    public ProviderComplianceActionEntity fulfillAction(
            Long actionId,
            LocalDate fulfilledDate,
            String evidence,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Fulfilling compliance action: id={}", actionId);

        ProviderComplianceActionEntity action = complianceRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionId));

        if (!"OPEN".equals(action.getStatus())) {
            throw new IllegalStateException("Can only fulfill open actions");
        }

        action.setStatus("FULFILLED");
        action.setVerificationDate(fulfilledDate);
        action.setCompletionNotes(evidence);
        action.setVerifiedBy(ctx.actorId());
        action.setVerifiedAt(Instant.now());

        action = complianceRepository.save(action);
        log.info("Compliance action fulfilled: id={}", actionId);
        return action;
    }

    /**
     * Exempt a compliance action.
     */
    @Transactional
    public ProviderComplianceActionEntity exemptAction(Long actionId, String exemptionReason, String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Exempting compliance action: id={}", actionId);

        ProviderComplianceActionEntity action = complianceRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionId));

        action.setStatus("EXEMPTED");
        action.setCompletionNotes(exemptionReason + " | " + notes);
        action.setVerifiedBy(ctx.actorId());
        action.setVerifiedAt(Instant.now());

        action = complianceRepository.save(action);
        log.info("Compliance action exempted: id={}", actionId);
        return action;
    }

    /**
     * Escalate a compliance action (extend due date).
     */
    @Transactional
    public ProviderComplianceActionEntity escalateAction(
            Long actionId,
            LocalDate newDueDate,
            String reason,
            String notes) {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Escalating compliance action: id={}, newDueDate={}", actionId, newDueDate);

        ProviderComplianceActionEntity action = complianceRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionId));

        action.setDueDate(newDueDate);
        action.setCompletionNotes("Escalated: " + reason + " | " + notes);
        action.setVerifiedBy(ctx.actorId());

        action = complianceRepository.save(action);
        log.info("Compliance action escalated: id={}", actionId);
        return action;
    }

    /**
     * Get compliance action by ID.
     */
    @Transactional(readOnly = true)
    public ProviderComplianceActionEntity getComplianceAction(Long actionId) {
        return complianceRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action not found: " + actionId));
    }

    /**
     * Get compliance actions by provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getComplianceActionsByProvider(Long providerId) {
        return complianceRepository.findByProviderId(providerId);
    }

    /**
     * Get outstanding actions for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getOutstandingActions(Long providerId) {
        return complianceRepository.findByProviderIdAndStatus(providerId, "OPEN");
    }

    /**
     * Get overdue actions for a provider.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getOverdueActions(Long providerId) {
        return complianceRepository.findByProviderIdAndStatusAndDueDateBefore(providerId, "OPEN", LocalDate.now());
    }

    /**
     * Get actions due within a number of days.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getActionsDueWithin(int days) {
        LocalDate targetDate = LocalDate.now().plusDays(days);
        return complianceRepository.findByStatusAndDueDateBefore("OPEN", targetDate);
    }

    /**
     * Get all overdue actions.
     */
    @Transactional(readOnly = true)
    public List<ProviderComplianceActionEntity> getAllOverdueActions() {
        return complianceRepository.findByStatusAndDueDateBefore("OPEN", LocalDate.now());
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }
}