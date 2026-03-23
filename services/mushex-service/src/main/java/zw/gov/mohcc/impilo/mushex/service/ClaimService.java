package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.mushex.domain.entity.AdjudicationEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimAttachmentEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.ClaimEventEntity;
import zw.gov.mohcc.impilo.mushex.domain.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ClaimStatus;
import zw.gov.mohcc.impilo.mushex.domain.enums.SourceType;
import zw.gov.mohcc.impilo.mushex.domain.repository.AdjudicationRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimAttachmentRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimEventRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.ClaimRepository;
import zw.gov.mohcc.impilo.mushex.domain.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Claims switching state machine.
 *
 * Manages insurance claims from draft through submission, adjudication, and payment.
 * Every state transition is recorded as a ClaimEventEntity for full audit trail.
 * When adjudication yields a patient residual, a new payment intent is created.
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private static final Map<ClaimStatus, Set<ClaimStatus>> VALID_TRANSITIONS;

    static {
        VALID_TRANSITIONS = new EnumMap<>(ClaimStatus.class);
        VALID_TRANSITIONS.put(ClaimStatus.DRAFT, Set.of(
                ClaimStatus.SUBMITTED));
        VALID_TRANSITIONS.put(ClaimStatus.SUBMITTED, Set.of(
                ClaimStatus.RECEIVED, ClaimStatus.REJECTED));
        VALID_TRANSITIONS.put(ClaimStatus.RECEIVED, Set.of(
                ClaimStatus.ADJUDICATED, ClaimStatus.REJECTED));
        VALID_TRANSITIONS.put(ClaimStatus.ADJUDICATED, Set.of(
                ClaimStatus.PAID, ClaimStatus.PARTIAL, ClaimStatus.RESUBMIT_PENDING));
        VALID_TRANSITIONS.put(ClaimStatus.PARTIAL, Set.of(
                ClaimStatus.PAID, ClaimStatus.RESUBMIT_PENDING));
        VALID_TRANSITIONS.put(ClaimStatus.RESUBMIT_PENDING, Set.of(
                ClaimStatus.SUBMITTED));
        VALID_TRANSITIONS.put(ClaimStatus.PAID, Set.of());
        VALID_TRANSITIONS.put(ClaimStatus.REJECTED, Set.of(
                ClaimStatus.RESUBMIT_PENDING));
    }

    private final ClaimRepository claimRepository;
    private final ClaimEventRepository claimEventRepository;
    private final ClaimAttachmentRepository attachmentRepository;
    private final AdjudicationRepository adjudicationRepository;
    private final PaymentIntentService intentService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ClaimService(ClaimRepository claimRepository,
                        ClaimEventRepository claimEventRepository,
                        ClaimAttachmentRepository attachmentRepository,
                        AdjudicationRepository adjudicationRepository,
                        PaymentIntentService intentService,
                        EventOutboxRepository outboxRepository,
                        ObjectMapper objectMapper) {
        this.claimRepository = claimRepository;
        this.claimEventRepository = claimEventRepository;
        this.attachmentRepository = attachmentRepository;
        this.adjudicationRepository = adjudicationRepository;
        this.intentService = intentService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new insurance claim in DRAFT status.
     *
     * @param billId     the COSTA bill ID this claim is for
     * @param insurerId  the insurer profile ID
     * @param facilityId the facility submitting the claim
     * @param totals     JSON string of claim totals breakdown
     * @return the created claim entity
     */
    @Transactional
    public ClaimEntity createClaim(String billId, String insurerId, UUID facilityId, String totals) {
        TrustContext ctx = TrustContextHolder.require();

        ClaimEntity claim = new ClaimEntity();
        claim.setClaimId(UlidGenerator.generate());
        claim.setTenantId(ctx.tenantId());
        claim.setFacilityId(facilityId != null ? facilityId : ctx.facilityId());
        claim.setBillId(billId);
        claim.setInsurerId(insurerId);
        claim.setStatus(ClaimStatus.DRAFT);
        claim.setTotals(totals);

        claim = claimRepository.save(claim);

        recordClaimEvent(claim.getClaimId(), null, ClaimStatus.DRAFT, ctx.actorId(), "Claim created");

        log.info("Created claim: claimId={}, billId={}, insurerId={}", claim.getClaimId(), billId, insurerId);

        return claim;
    }

    /**
     * Submit a claim for processing. Transitions DRAFT -> SUBMITTED.
     *
     * @param claimId the claim to submit
     * @return the submitted claim entity
     */
    @Transactional
    public ClaimEntity submitClaim(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        validateTransition(claim.getStatus(), ClaimStatus.SUBMITTED);

        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.SUBMITTED);
        claim.setSubmittedAt(OffsetDateTime.now());
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.SUBMITTED, ctx.actorId(), "Claim submitted");

        log.info("Claim submitted: claimId={}, {} -> SUBMITTED", claimId, oldStatus);

        publishEvent("CLAIM", claimId, "CLAIM_SUBMITTED",
                Map.of(
                        "claimId", claimId,
                        "billId", claim.getBillId(),
                        "insurerId", claim.getInsurerId(),
                        "submittedAt", claim.getSubmittedAt().toString()
                ),
                ctx.tenantId());

        return claim;
    }

    /**
     * Record acknowledgment of receipt from the insurer.
     * Transitions SUBMITTED -> RECEIVED.
     *
     * @param claimId     the claim acknowledged
     * @param externalRef the insurer's external reference number
     * @return the updated claim entity
     */
    @Transactional
    public ClaimEntity receiveAck(String claimId, String externalRef) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        validateTransition(claim.getStatus(), ClaimStatus.RECEIVED);

        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.RECEIVED);
        claim.setExternalRef(externalRef);
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.RECEIVED, ctx.actorId(),
                "Insurer acknowledgment received, ref: " + externalRef);

        log.info("Claim acknowledged: claimId={}, externalRef={}", claimId, externalRef);

        return claim;
    }

    /**
     * Record the adjudication decision from the insurer.
     * Creates an AdjudicationEntity and transitions claim to ADJUDICATED.
     * If patientResidual > 0, creates a new payment intent for the patient portion.
     *
     * @param claimId          the claim being adjudicated
     * @param decision         JSON decision details from the insurer
     * @param patientResidual  amount the patient must pay
     * @param insurerPayable   amount the insurer will pay
     * @return the updated claim entity
     */
    @Transactional
    public ClaimEntity recordAdjudication(String claimId, String decision,
                                          BigDecimal patientResidual, BigDecimal insurerPayable) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        validateTransition(claim.getStatus(), ClaimStatus.ADJUDICATED);

        // Create adjudication record
        AdjudicationEntity adjudication = new AdjudicationEntity();
        adjudication.setId(UlidGenerator.generate());
        adjudication.setClaimId(claimId);
        adjudication.setDecision(decision);
        adjudication.setPatientResidual(patientResidual);
        adjudication.setInsurerPayable(insurerPayable);
        adjudicationRepository.save(adjudication);

        // Transition claim
        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.ADJUDICATED);
        claim.setAdjudicatedAt(OffsetDateTime.now());
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.ADJUDICATED, ctx.actorId(),
                String.format("Adjudicated: patient residual=%s, insurer payable=%s",
                        patientResidual.toPlainString(), insurerPayable.toPlainString()));

        log.info("Claim adjudicated: claimId={}, patientResidual={}, insurerPayable={}",
                claimId, patientResidual.toPlainString(), insurerPayable.toPlainString());

        // If patient residual > 0, create a new payment intent for the patient
        if (patientResidual.compareTo(BigDecimal.ZERO) > 0) {
            String idempotencyKey = "CLAIM_RESIDUAL_" + claimId;
            intentService.createIntent(
                    SourceType.COSTA_BILL,
                    claim.getBillId(),
                    patientResidual,
                    "USD",
                    claim.getFacilityId(),
                    idempotencyKey,
                    "{\"source\":\"claim_adjudication\",\"claimId\":\"" + claimId + "\"}"
            );
            log.info("Created patient residual intent for claim {}: amount={}",
                    claimId, patientResidual.toPlainString());
        }

        publishEvent("CLAIM", claimId, "CLAIM_ADJUDICATED",
                Map.of(
                        "claimId", claimId,
                        "patientResidual", patientResidual.toPlainString(),
                        "insurerPayable", insurerPayable.toPlainString()
                ),
                ctx.tenantId());

        return claim;
    }

    /**
     * Mark a claim as paid by the insurer.
     * Transitions ADJUDICATED -> PAID.
     *
     * @param claimId the claim that has been paid
     * @return the updated claim entity
     */
    @Transactional
    public ClaimEntity markPaid(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        validateTransition(claim.getStatus(), ClaimStatus.PAID);

        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.PAID);
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.PAID, ctx.actorId(), "Claim payment received from insurer");

        log.info("Claim marked paid: claimId={}", claimId);

        publishEvent("CLAIM", claimId, "CLAIM_PAID",
                Map.of("claimId", claimId),
                ctx.tenantId());

        return claim;
    }

    /**
     * Dispute a claim, moving it to RESUBMIT_PENDING for correction and resubmission.
     *
     * @param claimId the claim to dispute
     * @param reason  the dispute reason
     * @param actorId the actor raising the dispute
     * @return the updated claim entity
     */
    @Transactional
    public ClaimEntity disputeClaim(String claimId, String reason, String actorId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        validateTransition(claim.getStatus(), ClaimStatus.RESUBMIT_PENDING);

        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.RESUBMIT_PENDING);
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.RESUBMIT_PENDING,
                actorId != null ? actorId : ctx.actorId(),
                "Dispute raised: " + reason);

        log.info("Claim disputed: claimId={}, from={}, reason={}", claimId, oldStatus, reason);

        publishEvent("CLAIM", claimId, "CLAIM_DISPUTED",
                Map.of(
                        "claimId", claimId,
                        "reason", reason != null ? reason : "",
                        "fromStatus", oldStatus.name()
                ),
                ctx.tenantId());

        return claim;
    }

    /**
     * Add an attachment to a claim.
     *
     * @param claimId      the claim to attach to
     * @param landelaDocId the Landela document service document ID
     * @param docType      the document type (e.g. "INVOICE", "CLINICAL_NOTE", "AUTHORIZATION")
     * @return the created attachment entity
     */
    @Transactional
    public ClaimAttachmentEntity addAttachment(String claimId, String landelaDocId, String docType) {
        // Verify claim exists
        getClaim(claimId);

        ClaimAttachmentEntity attachment = new ClaimAttachmentEntity();
        attachment.setId(UlidGenerator.generate());
        attachment.setClaimId(claimId);
        attachment.setLandelaDocId(landelaDocId);
        attachment.setDocType(docType);

        attachment = attachmentRepository.save(attachment);

        log.info("Added attachment to claim {}: docId={}, type={}", claimId, landelaDocId, docType);

        return attachment;
    }

    /**
     * Fetch a claim by ID, throwing if not found.
     */
    public ClaimEntity getClaim(String claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
    }

    public List<ClaimEventEntity> getEventsByClaimId(String claimId) {
        return claimEventRepository.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    public List<ClaimAttachmentEntity> getAttachmentsByClaimId(String claimId) {
        return attachmentRepository.findByClaimId(claimId);
    }

    /**
     * Validate that a claim status transition is allowed.
     */
    private void validateTransition(ClaimStatus from, ClaimStatus to) {
        Set<ClaimStatus> allowed = VALID_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException(
                    String.format("Invalid claim status transition: %s -> %s", from, to));
        }
    }

    /**
     * Record a claim event for audit trail.
     */
    private void recordClaimEvent(String claimId, ClaimStatus fromState, ClaimStatus toState,
                                  String actorId, String reason) {
        ClaimEventEntity event = new ClaimEventEntity();
        event.setId(UlidGenerator.generate());
        event.setClaimId(claimId);
        event.setFromState(fromState != null ? fromState.name() : null);
        event.setToState(toState.name());
        event.setActorId(actorId);
        event.setReason(reason);
        claimEventRepository.save(event);
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, Map<String, Object> payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write outbox event: {}", eventType, e);
        }
    }
}
