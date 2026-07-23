package zw.gov.mohcc.impilo.pharmacy.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pharmacy.domain.ClarificationStatus;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.ClarificationRequestEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseItemEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.ClarificationRequestRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.DispenseItemRepository;
import zw.gov.mohcc.impilo.pharmacy.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * OF-B15 — the substitution → prescriber clarification loop (§8.10.1).
 *
 * <p>A substitution proposal is evaluated against the rule engine:
 * auto-allowed rules apply immediately; rules requiring step-up create a
 * clarification request and the episode holds (fail-closed) until the
 * prescriber approves or declines. Decline never applies the substitution.
 * The patient-consent flag is recorded per §8.10.</p>
 */
@Service
public class ClarificationService {

    private static final Logger log = LoggerFactory.getLogger(ClarificationService.class);

    private final ClarificationRequestRepository clarificationRepository;
    private final DispenseItemRepository itemRepository;
    private final SubstitutionEngine substitutionEngine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ClarificationService(ClarificationRequestRepository clarificationRepository,
                                DispenseItemRepository itemRepository,
                                SubstitutionEngine substitutionEngine,
                                EventOutboxRepository outboxRepository,
                                ObjectMapper objectMapper) {
        this.clarificationRepository = clarificationRepository;
        this.itemRepository = itemRepository;
        this.substitutionEngine = substitutionEngine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Outcome of a substitution proposal: either the substitution was applied
     * immediately (auto-allowed tier) or a clarification request now awaits
     * the prescriber (approval-required tier).
     */
    public record ProposalOutcome(boolean autoApplied,
                                  DispenseItemEntity item,
                                  ClarificationRequestEntity clarification) {}

    /**
     * Propose a substitution for a dispense item. Fail-closed: a rule the
     * engine does not allow is rejected outright; a rule requiring step-up
     * creates a PENDING clarification (the episode holds via the engine's
     * pending-clarification gate) and emits
     * {@code pharmacy.dispense.clarification.v1}.
     */
    @Transactional
    public ProposalOutcome propose(UUID itemId, String targetDrugCode,
                                   String reason, boolean patientConsent) {
        TrustContext ctx = TrustContextHolder.require();
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for a substitution proposal");
        }

        DispenseItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        SubstitutionEngine.SubstitutionResult evaluation =
                substitutionEngine.evaluateSubstitution(itemId, targetDrugCode);
        if (!evaluation.allowed()) {
            throw new IllegalStateException("Substitution not allowed: " + evaluation.reason());
        }

        if (!evaluation.requiresStepUp()) {
            // Auto-allowed tier — apply immediately; consent still recorded on the item record.
            DispenseItemEntity applied =
                    substitutionEngine.applySubstitution(itemId, targetDrugCode, reason);
            log.info("Substitution auto-applied (no clarification needed): itemId={}, target={}",
                    itemId, targetDrugCode);
            return new ProposalOutcome(true, applied, null);
        }

        // Approval-required tier: one PENDING clarification per item at a time.
        if (clarificationRepository.existsByDispenseItemIdAndStatus(itemId, ClarificationStatus.PENDING)) {
            throw new IllegalStateException(
                    "A clarification is already pending for item " + itemId);
        }

        ClarificationRequestEntity request = new ClarificationRequestEntity();
        request.setTenantId(ctx.tenantId());
        request.setFacilityId(ctx.facilityId());
        request.setDispenseOrderId(item.getDispenseOrderId());
        request.setDispenseItemId(itemId);
        request.setSourceDrugCode(item.getDrugCode());
        request.setTargetDrugCode(targetDrugCode);
        request.setReason(reason);
        request.setPatientConsent(patientConsent);
        request.setStatus(ClarificationStatus.PENDING);
        request.setRequestedBy(ctx.actorId());
        request.setRequestedAt(OffsetDateTime.now());
        request = clarificationRepository.save(request);

        publishEvent(request, "DISPENSE_CLARIFICATION_REQUESTED", ctx.tenantId());

        log.info("Clarification requested: clarificationId={}, itemId={}, {} -> {}",
                request.getClarificationId(), itemId, item.getDrugCode(), targetDrugCode);
        return new ProposalOutcome(false, item, request);
    }

    /**
     * Prescriber resolution. Approve applies the substitution exactly once
     * (the APPROVED record is consumed by the engine's approval gate);
     * decline is fail-closed — the substitution is never applied and the
     * original prescribed line stands.
     */
    @Transactional
    public ClarificationRequestEntity resolve(UUID clarificationId, boolean approve, String note) {
        TrustContext ctx = TrustContextHolder.require();

        ClarificationRequestEntity request = clarificationRepository.findById(clarificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Clarification not found: " + clarificationId));
        if (request.getStatus() != ClarificationStatus.PENDING) {
            throw new IllegalStateException("Clarification " + clarificationId
                    + " is not pending (status=" + request.getStatus() + ")");
        }

        request.setResolvedBy(ctx.actorId());
        request.setResolvedAt(OffsetDateTime.now());
        request.setResolutionNote(note);

        if (approve) {
            request.setStatus(ClarificationStatus.APPROVED);
            request = clarificationRepository.save(request);
            // The engine's approval gate finds this APPROVED record and consumes it.
            substitutionEngine.applySubstitution(
                    request.getDispenseItemId(), request.getTargetDrugCode(), request.getReason());
        } else {
            request.setStatus(ClarificationStatus.DECLINED);
            request = clarificationRepository.save(request);
        }

        publishEvent(request, "DISPENSE_CLARIFICATION_RESOLVED", ctx.tenantId());

        log.info("Clarification resolved: clarificationId={}, decision={}, by={}",
                clarificationId, request.getStatus(), ctx.actorId());
        return request;
    }

    /** All clarifications for a dispense order (worklist view). */
    public List<ClarificationRequestEntity> forOrder(UUID dispenseOrderId) {
        return clarificationRepository.findByDispenseOrderIdOrderByRequestedAtDesc(dispenseOrderId);
    }

    public ClarificationRequestEntity get(UUID clarificationId) {
        return clarificationRepository.findById(clarificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Clarification not found: " + clarificationId));
    }

    // ------------------------------------------------------------------

    private void publishEvent(ClarificationRequestEntity request, String eventType, UUID tenantId) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("clarificationId", request.getClarificationId() != null
                    ? request.getClarificationId().toString() : null);
            payload.put("dispenseOrderId", request.getDispenseOrderId().toString());
            payload.put("dispenseItemId", request.getDispenseItemId().toString());
            payload.put("sourceDrugCode", request.getSourceDrugCode());
            payload.put("targetDrugCode", request.getTargetDrugCode());
            payload.put("reason", request.getReason());
            payload.put("patientConsent", request.isPatientConsent());
            payload.put("status", request.getStatus().name());
            payload.put("requestedBy", request.getRequestedBy());
            payload.put("resolvedBy", request.getResolvedBy());
            payload.put("resolutionNote", request.getResolutionNote());

            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("DISPENSE_CLARIFICATION");
            event.setAggregateId(request.getClarificationId() != null
                    ? request.getClarificationId().toString() : request.getDispenseItemId().toString());
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write clarification outbox event: {}", eventType, e);
        }
    }
}
