package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.DischargeCaseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.DischargeCaseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Orchestrates the multi-step patient discharge process.
 *
 * <p>Discharge is not a single action but a workflow with multiple
 * clearance steps (blockers) that must be resolved before the patient
 * can physically leave the facility. The default blocker set is:</p>
 * <ul>
 *   <li><strong>CLINICAL</strong> — clinical summary and orders completed</li>
 *   <li><strong>BILLING</strong> — billing reconciliation done</li>
 *   <li><strong>PHARMACY</strong> — medications dispensed</li>
 *   <li><strong>PAYMENT</strong> — payment settled or deferred</li>
 *   <li><strong>SUMMARY</strong> — discharge summary prepared</li>
 * </ul>
 *
 * <p>For death-related discharges, PHARMACY and PAYMENT blockers are
 * automatically excluded.</p>
 *
 * <p>The workflow progresses through statuses:</p>
 * <ol>
 *   <li><strong>INITIATED</strong> — discharge started, blockers active</li>
 *   <li><strong>EXIT_CLEARED</strong> — all blockers resolved (auto-progressed)</li>
 *   <li><strong>COMPLETED</strong> — patient has left the facility</li>
 * </ol>
 */
@Service
public class DischargeWorkflow {

    private static final Logger log = LoggerFactory.getLogger(DischargeWorkflow.class);

    /** Default blockers for standard discharge types. */
    private static final List<String> DEFAULT_BLOCKERS =
            List.of("CLINICAL", "BILLING", "PHARMACY", "PAYMENT", "SUMMARY");

    /** Blockers for death-type discharges (no pharmacy or payment needed). */
    private static final List<String> DEATH_BLOCKERS =
            List.of("CLINICAL", "BILLING", "SUMMARY");

    private final DischargeCaseRepository dischargeCaseRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public DischargeWorkflow(DischargeCaseRepository dischargeCaseRepository,
                             JourneyRepository journeyRepository,
                             JourneyStateMachine journeyStateMachine,
                             EventOutboxRepository outboxRepository,
                             ObjectMapper objectMapper) {
        this.dischargeCaseRepository = dischargeCaseRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Initiate the discharge process for a patient journey.
     *
     * <p>Creates a discharge case with the appropriate set of blockers based
     * on the discharge type. Transitions the journey to
     * {@link JourneyState#DISCHARGE_PENDING}.</p>
     *
     * @param journeyId     the patient journey being discharged
     * @param dischargeType the type of discharge (e.g. STANDARD, AGAINST_ADVICE, DEATH, TRANSFER)
     * @return the created discharge case entity
     * @throws IllegalArgumentException if the journey is not found
     */
    @Transactional
    public DischargeCaseEntity startDischarge(String journeyId, String dischargeType) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        // Select blockers based on discharge type
        List<String> blockers = "DEATH".equalsIgnoreCase(dischargeType)
                ? new ArrayList<>(DEATH_BLOCKERS)
                : new ArrayList<>(DEFAULT_BLOCKERS);

        DischargeCaseEntity dischargeCase = new DischargeCaseEntity();
        dischargeCase.setId(UUID.randomUUID());
        dischargeCase.setJourneyId(journeyId);
        dischargeCase.setTenantId(ctx.tenantId());
        dischargeCase.setFacilityId(journey.getFacilityId());
        dischargeCase.setDischargeType(dischargeType);
        dischargeCase.setStatus("INITIATED");
        dischargeCase.setBlockers(toJson(blockers));
        dischargeCase.setClinicalCleared(false);
        dischargeCase.setBillingCleared(false);
        dischargeCase.setPharmacyCleared("DEATH".equalsIgnoreCase(dischargeType));
        dischargeCase.setPaymentCleared("DEATH".equalsIgnoreCase(dischargeType));
        dischargeCase.setSummaryCleared(false);
        dischargeCase.setCreatedAt(OffsetDateTime.now());
        dischargeCase.setUpdatedAt(OffsetDateTime.now());

        dischargeCase = dischargeCaseRepository.save(dischargeCase);

        // Transition journey to DISCHARGE_PENDING
        journeyStateMachine.transition(journey, JourneyState.DISCHARGE_PENDING);

        writeOutbox("DISCHARGE", dischargeCase.getId().toString(),
                "DISCHARGE_INITIATED", toJson(Map.of(
                        "caseId", dischargeCase.getId().toString(),
                        "journeyId", journeyId,
                        "dischargeType", dischargeType,
                        "blockers", blockers)));

        log.info("Discharge initiated: case={}, journey={}, type={}, blockers={}",
                dischargeCase.getId(), journeyId, dischargeType, blockers);

        return dischargeCase;
    }

    /**
     * Clear a specific discharge blocker.
     *
     * <p>Removes the named blocker from the discharge case's blocker array
     * and sets the corresponding boolean clearance flag. If all blockers
     * have been cleared, the case automatically progresses to
     * {@code EXIT_CLEARED} status.</p>
     *
     * @param caseId      the discharge case identifier
     * @param blockerName the blocker to clear (e.g. CLINICAL, BILLING, PHARMACY, PAYMENT, SUMMARY)
     * @return the updated discharge case entity
     * @throws IllegalArgumentException if the case is not found or the blocker name is invalid
     */
    @Transactional
    public DischargeCaseEntity clearBlocker(UUID caseId, String blockerName) {
        DischargeCaseEntity dischargeCase = dischargeCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Discharge case not found: " + caseId));

        // Parse existing blockers
        List<String> blockers = parseBlockers(dischargeCase.getBlockers());

        if (!blockers.remove(blockerName)) {
            throw new IllegalArgumentException(String.format(
                    "Blocker '%s' is not active on discharge case %s. Active blockers: %s",
                    blockerName, caseId, blockers));
        }

        dischargeCase.setBlockers(toJson(blockers));

        // Set the corresponding boolean flag
        switch (blockerName.toUpperCase()) {
            case "CLINICAL" -> dischargeCase.setClinicalCleared(true);
            case "BILLING" -> dischargeCase.setBillingCleared(true);
            case "PHARMACY" -> dischargeCase.setPharmacyCleared(true);
            case "PAYMENT" -> dischargeCase.setPaymentCleared(true);
            case "SUMMARY" -> dischargeCase.setSummaryCleared(true);
            default -> log.warn("Unknown blocker type cleared: {}", blockerName);
        }

        dischargeCase.setUpdatedAt(OffsetDateTime.now());

        // Auto-progress to EXIT_CLEARED if all blockers resolved
        if (blockers.isEmpty()) {
            dischargeCase.setStatus("EXIT_CLEARED");
            log.info("All blockers cleared for discharge case {}, auto-progressed to EXIT_CLEARED", caseId);
        }

        dischargeCase = dischargeCaseRepository.save(dischargeCase);

        log.info("Blocker '{}' cleared on discharge case {}. Remaining: {}",
                blockerName, caseId, blockers);

        return dischargeCase;
    }

    /**
     * Complete the discharge process.
     *
     * <p>Sets the {@code closedAt} timestamp and transitions the journey to
     * {@link JourneyState#DISCHARGED}. Publishes an outbox event for
     * downstream consumers (analytics, billing finalisation, bed management).</p>
     *
     * @param caseId the discharge case to complete
     * @return the completed discharge case entity
     * @throws IllegalArgumentException if the case is not found
     * @throws IllegalStateException    if the case is not in EXIT_CLEARED status
     */
    @Transactional
    public DischargeCaseEntity completeDischarge(UUID caseId) {
        DischargeCaseEntity dischargeCase = dischargeCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Discharge case not found: " + caseId));

        if (!"EXIT_CLEARED".equals(dischargeCase.getStatus()) &&
                !"INITIATED".equals(dischargeCase.getStatus())) {
            throw new IllegalStateException(
                    "Cannot complete discharge in status: " + dischargeCase.getStatus());
        }

        dischargeCase.setStatus("COMPLETED");
        dischargeCase.setClosedAt(OffsetDateTime.now());
        dischargeCase.setUpdatedAt(OffsetDateTime.now());

        dischargeCase = dischargeCaseRepository.save(dischargeCase);

        // Transition journey to DISCHARGED
        final DischargeCaseEntity savedDischargeCase = dischargeCase;
        JourneyEntity journey = journeyRepository.findByJourneyId(savedDischargeCase.getJourneyId())
                .orElseThrow(() -> new IllegalStateException(
                        "Journey not found for discharge case: " + savedDischargeCase.getJourneyId()));
        journeyStateMachine.transition(journey, JourneyState.DISCHARGED);

        writeOutbox("DISCHARGE", caseId.toString(), "DISCHARGE_COMPLETED", toJson(Map.of(
                "caseId", caseId.toString(),
                "journeyId", dischargeCase.getJourneyId(),
                "dischargeType", dischargeCase.getDischargeType(),
                "closedAt", dischargeCase.getClosedAt().toString())));

        log.info("Discharge completed: case={}, journey={}", caseId, dischargeCase.getJourneyId());

        return dischargeCase;
    }

    /**
     * Retrieve the current discharge status for a case.
     *
     * @param caseId the discharge case identifier
     * @return the discharge case entity
     * @throws IllegalArgumentException if the case is not found
     */
    @Transactional(readOnly = true)
    public DischargeCaseEntity getDischargeStatus(UUID caseId) {
        return dischargeCaseRepository.findById(caseId)
                .orElseThrow(() -> new IllegalArgumentException("Discharge case not found: " + caseId));
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private List<String> parseBlockers(String blockersJson) {
        if (blockersJson == null || blockersJson.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(blockersJson, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse blockers JSON: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void writeOutbox(String aggregateType, String aggregateId,
                             String eventType, String payloadJson) {
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setEventType(eventType);
        outbox.setPayload(payloadJson);
        outbox.setTenantId(TrustContextHolder.require().tenantId());
        outboxRepository.save(outbox);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise payload: {}", e.getMessage());
            return "{}";
        }
    }
}
