package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.domain.JourneyState;
import zw.gov.mohcc.impilo.pct.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.JourneyEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.TransferEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.JourneyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.TransferRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Manages inpatient transfer workflows between wards and facilities.
 *
 * <p>A transfer represents the movement of an admitted patient from one
 * ward/bed to another (intra-facility) or from one facility to another
 * (inter-facility). The workflow progresses through three stages:</p>
 * <ol>
 *   <li><strong>REQUESTED</strong> — a clinician requests the transfer</li>
 *   <li><strong>APPROVED</strong> — the receiving ward/facility authorises the transfer</li>
 *   <li><strong>COMPLETED</strong> — the patient has physically moved; the admission
 *       record is updated with the new ward/bed</li>
 * </ol>
 *
 * <p>Completing a transfer transitions the journey through
 * {@link JourneyState#TRANSFERRED} and back to {@link JourneyState#ADMITTED}
 * to reflect the patient's continued inpatient status at the new location.</p>
 */
@Service
public class TransferService {

    private static final Logger log = LoggerFactory.getLogger(TransferService.class);

    /** Admission statuses considered active for transfer lookups. */
    private static final List<String> ACTIVE_ADMISSION_STATUSES = List.of("ADMITTED");

    private final TransferRepository transferRepository;
    private final AdmissionRepository admissionRepository;
    private final JourneyRepository journeyRepository;
    private final JourneyStateMachine journeyStateMachine;
    private final EventOutboxRepository outboxRepository;
    private final TelemetryService telemetryService;
    private final ObjectMapper objectMapper;

    public TransferService(TransferRepository transferRepository,
                           AdmissionRepository admissionRepository,
                           JourneyRepository journeyRepository,
                           JourneyStateMachine journeyStateMachine,
                           EventOutboxRepository outboxRepository,
                           TelemetryService telemetryService,
                           ObjectMapper objectMapper) {
        this.transferRepository = transferRepository;
        this.admissionRepository = admissionRepository;
        this.journeyRepository = journeyRepository;
        this.journeyStateMachine = journeyStateMachine;
        this.outboxRepository = outboxRepository;
        this.telemetryService = telemetryService;
        this.objectMapper = objectMapper;
    }

    /**
     * Request a patient transfer to a new ward/bed.
     *
     * <p>Locates the current active admission for the journey and creates
     * a transfer record capturing the source and destination locations.
     * The transfer type indicates the nature of the move (e.g.
     * {@code INTRA_FACILITY}, {@code INTER_FACILITY}, {@code STEP_DOWN},
     * {@code STEP_UP}).</p>
     *
     * @param journeyId    the patient journey being transferred
     * @param toWardId     the destination ward
     * @param toBedId      the destination bed; may be {@code null} if deferred
     * @param transferType the type of transfer
     * @return the created transfer entity
     * @throws IllegalArgumentException if the journey or active admission is not found
     */
    @Transactional
    public TransferEntity requestTransfer(String journeyId, UUID toWardId,
                                          UUID toBedId, String transferType) {
        TrustContext ctx = TrustContextHolder.require();

        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalArgumentException("Journey not found: " + journeyId));

        AdmissionEntity admission = admissionRepository
                .findByJourneyIdAndStatusIn(journeyId, ACTIVE_ADMISSION_STATUSES)
                .orElseThrow(() -> new IllegalStateException(
                        "No active admission found for journey: " + journeyId));

        TransferEntity transfer = new TransferEntity();
        transfer.setId(UUID.randomUUID());
        transfer.setJourneyId(journeyId);
        transfer.setTenantId(ctx.tenantId());
        transfer.setAdmissionId(admission.getId());
        transfer.setFromWardId(admission.getWardId());
        transfer.setFromBedId(admission.getBedId());
        transfer.setToWardId(toWardId);
        transfer.setToBedId(toBedId);
        transfer.setTransferType(transferType);
        transfer.setStatus("REQUESTED");
        transfer.setRequestedBy(ctx.actorId());

        transfer = transferRepository.save(transfer);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", transfer.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("admissionId", admission.getId().toString());
        payload.put("fromWardId", admission.getWardId().toString());
        payload.put("toWardId", toWardId.toString());
        payload.put("transferType", transferType);
        payload.put("requestedBy", ctx.actorId());
        writeOutbox("TRANSFER", transfer.getId().toString(),
                "TRANSFER_REQUESTED", toJson(payload));

        log.info("Transfer requested: id={}, journey={}, from ward {} to ward {}",
                transfer.getId(), journeyId, admission.getWardId(), toWardId);

        return transfer;
    }

    /**
     * Approve a pending transfer request.
     *
     * @param transferId the transfer to approve
     * @return the updated transfer entity
     * @throws IllegalArgumentException if the transfer is not found
     * @throws IllegalStateException    if the transfer is not in REQUESTED status
     */
    @Transactional
    public TransferEntity approveTransfer(UUID transferId) {
        TrustContext ctx = TrustContextHolder.require();

        TransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));

        if (!"REQUESTED".equals(transfer.getStatus())) {
            throw new IllegalStateException(
                    "Cannot approve transfer in status: " + transfer.getStatus());
        }

        transfer.setStatus("APPROVED");
        transfer.setApprovedBy(ctx.actorId());

        transfer = transferRepository.save(transfer);

        log.info("Transfer approved: id={}, journey={}", transferId, transfer.getJourneyId());

        return transfer;
    }

    /**
     * Complete a transfer by moving the patient to the new ward/bed.
     *
     * <p>Updates the underlying admission record with the new ward and bed
     * from the transfer. The transfer status is set to {@code COMPLETED}.
     * The journey is transitioned to {@link JourneyState#TRANSFERRED} and
     * then immediately to {@link JourneyState#ADMITTED} to reflect continued
     * inpatient status at the new location.</p>
     *
     * @param transferId the transfer to complete
     * @return the completed transfer entity
     * @throws IllegalArgumentException if the transfer is not found
     * @throws IllegalStateException    if the transfer is not in REQUESTED or APPROVED status
     */
    @Transactional
    public TransferEntity completeTransfer(UUID transferId) {
        TrustContext ctx = TrustContextHolder.require();

        TransferEntity transfer = transferRepository.findById(transferId)
                .orElseThrow(() -> new IllegalArgumentException("Transfer not found: " + transferId));
        UUID admissionId = transfer.getAdmissionId();
        String journeyId = transfer.getJourneyId();

        if (!"REQUESTED".equals(transfer.getStatus()) && !"APPROVED".equals(transfer.getStatus())) {
            throw new IllegalStateException(
                    "Cannot complete transfer in status: " + transfer.getStatus());
        }

        // Update admission with new location
        AdmissionEntity admission = admissionRepository.findById(admissionId)
                .orElseThrow(() -> new IllegalStateException(
                        "Admission not found for transfer: " + admissionId));

        admission.setWardId(transfer.getToWardId());
        admission.setBedId(transfer.getToBedId());
        admission.setUpdatedAt(OffsetDateTime.now());
        admissionRepository.save(admission);

        // Complete the transfer
        transfer.setStatus("COMPLETED");
        transfer.setCompletedBy(ctx.actorId());
        transfer.setCompletedAt(OffsetDateTime.now());
        transfer = transferRepository.save(transfer);

        // Transition journey: current state -> TRANSFERRED -> ADMITTED
        JourneyEntity journey = journeyRepository.findByJourneyId(journeyId)
                .orElseThrow(() -> new IllegalStateException(
                        "Journey not found for transfer: " + journeyId));

        journeyStateMachine.transition(journey, JourneyState.TRANSFERRED);
        journeyStateMachine.transition(journey, JourneyState.ADMITTED);

        // Outbox event
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("transferId", transfer.getId().toString());
        payload.put("journeyId", journeyId);
        payload.put("admissionId", admissionId.toString());
        payload.put("fromWardId", transfer.getFromWardId().toString());
        payload.put("toWardId", transfer.getToWardId().toString());
        payload.put("transferType", transfer.getTransferType());
        payload.put("completedBy", ctx.actorId());
        payload.put("completedAt", transfer.getCompletedAt().toString());
        writeOutbox("TRANSFER", transfer.getId().toString(),
                "TRANSFER_COMPLETED", toJson(payload));

        // Telemetry
        Map<String, Object> telemetryData = new LinkedHashMap<>();
        telemetryData.put("transferId", transfer.getId().toString());
        telemetryData.put("fromWardId", transfer.getFromWardId().toString());
        telemetryData.put("toWardId", transfer.getToWardId().toString());
        telemetryData.put("transferType", transfer.getTransferType());
        telemetryService.record("transfer.completed", journeyId, telemetryData);

        log.info("Transfer completed: id={}, journey={}, ward {} -> ward {}",
                transferId, journeyId, transfer.getFromWardId(), transfer.getToWardId());

        return transfer;
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

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
            log.warn("Failed to serialise outbox payload: {}", e.getMessage());
            return "{}";
        }
    }
}
