package zw.gov.mohcc.impilo.mushex.service;

import com.fasterxml.jackson.databind.JsonNode;
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
import zw.gov.mohcc.impilo.mushex.integration.CoverageEligibilityClient;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Claims switching state machine (v1.3 settlement journey + legacy statuses).
 */
@Service
public class ClaimService {

    private static final Logger log = LoggerFactory.getLogger(ClaimService.class);

    private static final Map<ClaimStatus, Set<ClaimStatus>> VALID_TRANSITIONS;

    static {
        Map<ClaimStatus, Set<ClaimStatus>> m = new EnumMap<>(ClaimStatus.class);
        m.put(ClaimStatus.DRAFT, Set.of(ClaimStatus.SUBMITTED));
        m.put(ClaimStatus.RESUBMIT_PENDING, Set.of(ClaimStatus.SUBMITTED));
        m.put(ClaimStatus.SUBMITTED, Set.of(ClaimStatus.ELIGIBILITY_PENDING));
        m.put(ClaimStatus.ELIGIBILITY_PENDING, Set.of(ClaimStatus.ELIGIBILITY_VERIFIED, ClaimStatus.DENIED));
        m.put(ClaimStatus.ELIGIBILITY_VERIFIED, Set.of(ClaimStatus.PREAUTHORIZED, ClaimStatus.ADJUDICATED));
        m.put(ClaimStatus.PREAUTHORIZED, Set.of(ClaimStatus.ADJUDICATED));
        m.put(ClaimStatus.ADJUDICATED, Set.of(
                ClaimStatus.APPROVED,
                ClaimStatus.DENIED,
                ClaimStatus.PARTIAL,
                ClaimStatus.RESUBMIT_PENDING,
                ClaimStatus.REJECTED));
        m.put(ClaimStatus.PARTIAL, Set.of(ClaimStatus.APPROVED, ClaimStatus.RESUBMIT_PENDING));
        m.put(ClaimStatus.APPROVED, Set.of(ClaimStatus.REMITTED));
        m.put(ClaimStatus.REMITTED, Set.of(ClaimStatus.PAID));
        m.put(ClaimStatus.PAID, Set.of(ClaimStatus.SETTLED, ClaimStatus.REVERSED));
        m.put(ClaimStatus.SETTLED, Set.of(ClaimStatus.RECONCILED, ClaimStatus.DISPUTED));
        m.put(ClaimStatus.DISPUTED, Set.of(ClaimStatus.RECOVERED, ClaimStatus.SETTLED));
        m.put(ClaimStatus.RECEIVED, Set.of(ClaimStatus.ADJUDICATED, ClaimStatus.ELIGIBILITY_VERIFIED));
        m.put(ClaimStatus.REJECTED, Set.of(ClaimStatus.RESUBMIT_PENDING));
        m.put(ClaimStatus.DENIED, Set.of(ClaimStatus.APPEALED, ClaimStatus.RESUBMIT_PENDING));
        m.put(ClaimStatus.APPEALED, Set.of(ClaimStatus.ELIGIBILITY_PENDING, ClaimStatus.RESUBMIT_PENDING));
        m.put(ClaimStatus.RECONCILED, Set.of());
        m.put(ClaimStatus.REVERSED, Set.of());
        m.put(ClaimStatus.RECOVERED, Set.of());
        VALID_TRANSITIONS = Map.copyOf(m);
    }

    private final ClaimRepository claimRepository;
    private final ClaimEventRepository claimEventRepository;
    private final ClaimAttachmentRepository attachmentRepository;
    private final AdjudicationRepository adjudicationRepository;
    private final PaymentIntentService intentService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final CoverageEligibilityClient coverageEligibilityClient;

    public ClaimService(ClaimRepository claimRepository,
                        ClaimEventRepository claimEventRepository,
                        ClaimAttachmentRepository attachmentRepository,
                        AdjudicationRepository adjudicationRepository,
                        PaymentIntentService intentService,
                        EventOutboxRepository outboxRepository,
                        ObjectMapper objectMapper,
                        CoverageEligibilityClient coverageEligibilityClient) {
        this.claimRepository = claimRepository;
        this.claimEventRepository = claimEventRepository;
        this.attachmentRepository = attachmentRepository;
        this.adjudicationRepository = adjudicationRepository;
        this.intentService = intentService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.coverageEligibilityClient = coverageEligibilityClient;
    }

    @Transactional
    public ClaimEntity createClaim(String billId, String insurerId, UUID facilityId, String totals) {
        return createClaim(billId, insurerId, facilityId, totals, null, null, null, null);
    }

    /**
     * Creates a claim with optional coverage profile fields used by the eligibility gate.
     */
    @Transactional
    public ClaimEntity createClaim(String billId,
                                   String insurerId,
                                   UUID facilityId,
                                   String totals,
                                   String patientCpid,
                                   String planCode,
                                   String serviceCode,
                                   Boolean preauthRequired) {
        TrustContext ctx = TrustContextHolder.require();

        ClaimEntity claim = new ClaimEntity();
        claim.setClaimId(UlidGenerator.generate());
        claim.setTenantId(ctx.tenantId());
        claim.setFacilityId(facilityId != null ? facilityId : ctx.facilityId());
        claim.setBillId(billId);
        claim.setInsurerId(insurerId);
        claim.setStatus(ClaimStatus.DRAFT);
        claim.setTotals(totals);
        claim.setPatientCpid(patientCpid);
        claim.setPlanCode(planCode);
        claim.setServiceCode(serviceCode);
        if (preauthRequired != null) {
            claim.setPreauthRequired(preauthRequired);
        }

        claim = claimRepository.save(claim);

        recordClaimEvent(claim.getClaimId(), null, ClaimStatus.DRAFT, ctx.actorId(), "Claim created");

        log.info("Created claim: claimId={}, billId={}, insurerId={}", claim.getClaimId(), billId, insurerId);

        return claim;
    }

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
     * Insurer / switch acknowledgment: moves the claim into the coverage eligibility queue.
     * SUBMITTED → ELIGIBILITY_PENDING.
     */
    @Transactional
    public ClaimEntity receiveAck(String claimId, String externalRef) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        validateTransition(claim.getStatus(), ClaimStatus.ELIGIBILITY_PENDING);

        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.ELIGIBILITY_PENDING);
        claim.setExternalRef(externalRef);
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.ELIGIBILITY_PENDING, ctx.actorId(),
                "Queued for eligibility verification, ref: " + externalRef);

        log.info("Claim queued for eligibility: claimId={}, externalRef={}", claimId, externalRef);

        return claim;
    }

    /**
     * Calls coverage-service and advances ELIGIBILITY_PENDING → ELIGIBILITY_VERIFIED or DENIED.
     */
    @Transactional
    public ClaimEntity checkEligibilityGate(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        if (claim.getStatus() != ClaimStatus.ELIGIBILITY_PENDING) {
            throw new IllegalStateException("Eligibility gate requires status ELIGIBILITY_PENDING, was " + claim.getStatus());
        }

        String patientCpid = firstNonBlank(
                claim.getPatientCpid(), claim.getTotals(), "patient_cpid", "patientCpid");
        String planCode = firstNonBlank(claim.getPlanCode(), claim.getTotals(), "plan_code", "planCode");
        String serviceCode = firstNonBlank(claim.getServiceCode(), claim.getTotals(), "service_code", "serviceCode");

        if (patientCpid == null || planCode == null) {
            throw new IllegalStateException("patient CPID and plan code are required for eligibility check");
        }

        CoverageEligibilityClient.EligibilityResult eligibility = coverageEligibilityClient.evaluateEligibility(
                claim.getTenantId(), patientCpid, planCode, serviceCode != null ? serviceCode : "");

        ClaimStatus oldStatus = claim.getStatus();
        if (eligibility.eligible()) {
            validateTransition(oldStatus, ClaimStatus.ELIGIBILITY_VERIFIED);
            claim.setStatus(ClaimStatus.ELIGIBILITY_VERIFIED);
            claim.setDenialReason(null);
            if (eligibility.coverageReference() != null) {
                claim.setCoverageEligibilityRef(eligibility.coverageReference());
            }
            claim = claimRepository.save(claim);
            recordClaimEvent(claimId, oldStatus, ClaimStatus.ELIGIBILITY_VERIFIED, ctx.actorId(),
                    "Coverage eligibility verified");
            log.info("Eligibility verified for claimId={}", claimId);
        } else {
            validateTransition(oldStatus, ClaimStatus.DENIED);
            claim.setStatus(ClaimStatus.DENIED);
            String denial = eligibility.reasonCode() != null && !eligibility.reasonCode().isBlank()
                    ? eligibility.reasonCode()
                    : "COVERAGE_INELIGIBLE";
            claim.setDenialReason(denial);
            claim = claimRepository.save(claim);
            recordClaimEvent(claimId, oldStatus, ClaimStatus.DENIED, ctx.actorId(),
                    "Coverage ineligible (" + denial + ")");
            log.info("Eligibility denied for claimId={} reason={}", claimId, denial);
        }

        return claim;
    }

    @Transactional
    public ClaimEntity advanceToPreauthorized(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.PREAUTHORIZED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.PREAUTHORIZED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.PREAUTHORIZED, ctx.actorId(), "Preauthorization recorded");
        log.info("Claim preauthorized: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity recordAdjudication(String claimId, String decision,
                                          BigDecimal patientResidual, BigDecimal insurerPayable) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);

        ClaimStatus from = claim.getStatus();
        if (claim.isPreauthRequired()) {
            if (from != ClaimStatus.PREAUTHORIZED) {
                throw new IllegalStateException("Adjudication requires PREAUTHORIZED when preauthRequired=true");
            }
        } else if (from != ClaimStatus.PREAUTHORIZED && from != ClaimStatus.ELIGIBILITY_VERIFIED) {
            throw new IllegalStateException(
                    "Adjudication requires ELIGIBILITY_VERIFIED (no preauth) or PREAUTHORIZED");
        }

        validateTransition(from, ClaimStatus.ADJUDICATED);

        AdjudicationEntity adjudication = new AdjudicationEntity();
        adjudication.setId(UlidGenerator.generate());
        adjudication.setClaimId(claimId);
        adjudication.setDecision(decision);
        adjudication.setPatientResidual(patientResidual);
        adjudication.setInsurerPayable(insurerPayable);
        adjudicationRepository.save(adjudication);

        ClaimStatus oldStatus = claim.getStatus();
        claim.setStatus(ClaimStatus.ADJUDICATED);
        claim.setAdjudicatedAt(OffsetDateTime.now());
        claim = claimRepository.save(claim);

        recordClaimEvent(claimId, oldStatus, ClaimStatus.ADJUDICATED, ctx.actorId(),
                String.format("Adjudicated: patient residual=%s, insurer payable=%s",
                        patientResidual.toPlainString(), insurerPayable.toPlainString()));

        log.info("Claim adjudicated: claimId={}, patientResidual={}, insurerPayable={}",
                claimId, patientResidual.toPlainString(), insurerPayable.toPlainString());

        if (patientResidual.compareTo(BigDecimal.ZERO) > 0) {
            String idempotencyKey = "CLAIM_RESIDUAL_" + claimId;
            String residualMetadata;
            try {
                java.util.LinkedHashMap<String, Object> meta = new java.util.LinkedHashMap<>();
                meta.put("source", "claim_adjudication");
                meta.put("claimId", claimId);
                meta.put("payer_id", claim.getInsurerId());
                meta.put("provider_id", claim.getFacilityId().toString());
                residualMetadata = objectMapper.writeValueAsString(meta);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to serialize residual payment metadata", e);
            }
            intentService.createIntent(
                    SourceType.COSTA_BILL,
                    claim.getBillId(),
                    patientResidual,
                    "USD",
                    claim.getFacilityId(),
                    idempotencyKey,
                    residualMetadata
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

    @Transactional
    public ClaimEntity approveClaim(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.APPROVED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.APPROVED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.APPROVED, ctx.actorId(), "Claim approved for remittance");
        log.info("Claim approved: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity markRemitted(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.REMITTED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.REMITTED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.REMITTED, ctx.actorId(), "Insurer remittance issued");
        log.info("Claim remitted: claimId={}", claimId);
        return claim;
    }

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

        BigDecimal insurerPayable = adjudicationRepository.findByClaimId(claimId)
                .map(AdjudicationEntity::getInsurerPayable)
                .orElse(BigDecimal.ZERO);
        String svc = firstNonBlank(claim.getServiceCode(), claim.getTotals(), "service_code", "serviceCode");
        Map<String, Object> paidPayload = new LinkedHashMap<>();
        paidPayload.put("claimId", claimId);
        paidPayload.put("claim_id", claimId);
        paidPayload.put("status", "PAID");
        paidPayload.put("tenant_id", claim.getTenantId().toString());
        paidPayload.put("tenantId", claim.getTenantId().toString());
        paidPayload.put("patient_cpid", claim.getPatientCpid());
        paidPayload.put("patientCpid", claim.getPatientCpid());
        paidPayload.put("plan_code", claim.getPlanCode());
        paidPayload.put("planCode", claim.getPlanCode());
        paidPayload.put("service_code", svc != null ? svc : "");
        paidPayload.put("serviceCode", svc != null ? svc : "");
        paidPayload.put("insurer_payable", insurerPayable.toPlainString());
        paidPayload.put("insurerPayable", insurerPayable.toPlainString());
        paidPayload.put("amount", insurerPayable.toPlainString());
        publishEvent("CLAIM", claimId, "CLAIM_PAID", paidPayload, ctx.tenantId());

        return claim;
    }

    /**
     * Coverage appeal outcome: moves a denied or appealed claim back to the adjudication queue.
     */
    @Transactional
    public ClaimEntity markResubmitPendingAfterAppeal(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        ClaimStatus from = claim.getStatus();
        if (from != ClaimStatus.DENIED && from != ClaimStatus.APPEALED) {
            throw new IllegalStateException("Appeal resubmit requires DENIED or APPEALED, was " + from);
        }
        validateTransition(from, ClaimStatus.RESUBMIT_PENDING);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.RESUBMIT_PENDING);
        claim.setDenialReason(null);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.RESUBMIT_PENDING, ctx.actorId(),
                "Appeal overturned — queued for resubmission");
        log.info("Claim marked RESUBMIT_PENDING after appeal: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity markSettled(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.SETTLED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.SETTLED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.SETTLED, ctx.actorId(), "Claim settled");
        log.info("Claim settled: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity markReconciled(String claimId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.RECONCILED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.RECONCILED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.RECONCILED, ctx.actorId(), "Claim reconciled");
        log.info("Claim reconciled: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity raiseSettlementDispute(String claimId, String reason, String actorId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.DISPUTED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.DISPUTED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.DISPUTED,
                actorId != null ? actorId : ctx.actorId(),
                "Settlement dispute: " + (reason != null ? reason : ""));
        log.info("Settlement dispute raised: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity resolveSettlementDisputeToRecovered(String claimId, String actorId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.RECOVERED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.RECOVERED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.RECOVERED,
                actorId != null ? actorId : ctx.actorId(), "Settlement dispute resolved (recovered)");
        log.info("Settlement dispute resolved to RECOVERED: claimId={}", claimId);
        return claim;
    }

    @Transactional
    public ClaimEntity resolveSettlementDisputeToSettled(String claimId, String actorId) {
        TrustContext ctx = TrustContextHolder.require();
        ClaimEntity claim = getClaim(claimId);
        validateTransition(claim.getStatus(), ClaimStatus.SETTLED);
        ClaimStatus old = claim.getStatus();
        claim.setStatus(ClaimStatus.SETTLED);
        claim = claimRepository.save(claim);
        recordClaimEvent(claimId, old, ClaimStatus.SETTLED,
                actorId != null ? actorId : ctx.actorId(), "Settlement dispute resolved (back to settled)");
        log.info("Settlement dispute resolved to SETTLED: claimId={}", claimId);
        return claim;
    }

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

    public Page<ClaimEntity> listClaims(UUID tenantId, ClaimStatus status, String insurerId, Pageable pageable) {
        // A tenant-less list request scopes to no tenant and yields an empty page, consistent
        // with the sibling ops/fraud list endpoints (which pass a possibly-null tenant straight
        // to their repositories). Listing must not 500 when the trust-context tenant is absent.
        if (tenantId == null) {
            return Page.empty(pageable);
        }

        if (insurerId != null && !insurerId.isBlank() && status != null) {
            return claimRepository.findByTenantIdAndInsurerIdAndStatus(tenantId, insurerId, status, pageable);
        }
        if (insurerId != null && !insurerId.isBlank()) {
            return claimRepository.findByTenantIdAndInsurerId(tenantId, insurerId, pageable);
        }
        if (status != null) {
            return claimRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return claimRepository.findByTenantId(tenantId, pageable);
    }

    public List<ClaimEventEntity> getClaimEvents(String claimId) {
        getClaim(claimId);
        return claimEventRepository.findByClaimIdOrderByCreatedAtAsc(claimId);
    }

    public List<ClaimAttachmentEntity> getClaimAttachments(String claimId) {
        getClaim(claimId);
        return attachmentRepository.findByClaimId(claimId);
    }

    @Transactional
    public ClaimAttachmentEntity addAttachment(String claimId, String landelaDocId, String docType) {
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

    public ClaimEntity getClaim(String claimId) {
        return claimRepository.findById(claimId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
    }

    private void validateTransition(ClaimStatus from, ClaimStatus to) {
        Set<ClaimStatus> allowed = VALID_TRANSITIONS.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new IllegalStateException(
                    String.format("Invalid claim status transition: %s -> %s", from, to));
        }
    }

    private String firstNonBlank(String columnValue, String totalsJson, String snakeKey, String camelKey) {
        if (columnValue != null && !columnValue.isBlank()) {
            return columnValue;
        }
        if (totalsJson == null || totalsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(totalsJson);
            if (n.has(snakeKey) && !n.get(snakeKey).asText().isBlank()) {
                return n.get(snakeKey).asText();
            }
            if (n.has(camelKey) && !n.get(camelKey).asText().isBlank()) {
                return n.get(camelKey).asText();
            }
        } catch (Exception e) {
            log.warn("Could not parse totals JSON for coverage fields: {}", e.getMessage());
        }
        return null;
    }

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
