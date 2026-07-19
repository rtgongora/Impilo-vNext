package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.core.BenefitAccumulatorService.MovementResult;
import zw.gov.mohcc.impilo.coverage.domain.*;
import zw.gov.mohcc.impilo.coverage.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Claims v2 lifecycle + real line-level adjudication (spec §19, §6).
 *
 * <p>Adjudication is explainable and line-independent: each line is validated against benefit
 * coverage, authorisation, and remaining benefit; the covered share consumes the reservation-aware
 * accumulator (converting an authorisation's reservation where present). The header status is
 * derived from the line outcomes and never conceals a mixed result. On an approved liability a
 * remittance (settlement instruction) is created PENDING and a {@code settlement.initiated} event
 * is emitted — MUSHEX executes the actual payout (credential-gated; the outbox event is the seam).
 */
@Service
public class ClaimAdjudicationService {

    private static final Logger log = LoggerFactory.getLogger(ClaimAdjudicationService.class);
    public static final String RULESET_VERSION = "ruvimbo-adjudication-v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClaimRepository claimRepository;
    private final ClaimLineRepository lineRepository;
    private final ClaimEventRepository eventRepository;
    private final RemittanceRepository remittanceRepository;
    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final BenefitDefinitionRepository benefitRepository;
    private final AuthorisationRepository authRepository;
    private final AuthorisationLineRepository authLineRepository;
    private final BenefitAccumulatorService accumulatorService;
    private final CoverageEventService eventService;

    public ClaimAdjudicationService(ClaimRepository claimRepository, ClaimLineRepository lineRepository,
                                    ClaimEventRepository eventRepository, RemittanceRepository remittanceRepository,
                                    MemberCoverageRepository memberRepository, CoveragePlanRepository planRepository,
                                    BenefitDefinitionRepository benefitRepository, AuthorisationRepository authRepository,
                                    AuthorisationLineRepository authLineRepository,
                                    BenefitAccumulatorService accumulatorService, CoverageEventService eventService) {
        this.claimRepository = claimRepository;
        this.lineRepository = lineRepository;
        this.eventRepository = eventRepository;
        this.remittanceRepository = remittanceRepository;
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.benefitRepository = benefitRepository;
        this.authRepository = authRepository;
        this.authLineRepository = authLineRepository;
        this.accumulatorService = accumulatorService;
        this.eventService = eventService;
    }

    public record LineInput(String benefitCode, String serviceCode, String description,
                            BigDecimal quantity, BigDecimal submittedAmount) {}

    public record ScrubResult(boolean pass, List<String> blocking, List<String> warnings) {}

    // ---- Reads -------------------------------------------------------------

    @Transactional(readOnly = true)
    public ClaimEntity get(UUID tenantId, UUID claimId) {
        return claimRepository.findByIdAndTenantId(claimId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
    }

    @Transactional(readOnly = true)
    public List<ClaimLineEntity> lines(UUID claimId) {
        return lineRepository.findByClaimIdOrderByLineNumber(claimId);
    }

    @Transactional(readOnly = true)
    public List<ClaimEventEntity> events(UUID claimId) {
        return eventRepository.findByClaimIdOrderByOccurredAt(claimId);
    }

    // ---- Create + scrub + submit ------------------------------------------

    @Transactional
    public ClaimEntity createDraft(UUID tenantId, String podId, UUID coverageId, String claimType,
                                   String facilityId, String providerId, String encounterId,
                                   UUID authorisationId, UUID referralId, List<LineInput> lineInputs,
                                   UUID correlationId) {
        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(coverageId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage not found: " + coverageId));
        CoveragePlanEntity plan = planRepository.findById(member.getPlanId()).orElse(null);
        BigDecimal total = BigDecimal.ZERO;
        String lineSummary = "[]";
        try { lineSummary = MAPPER.writeValueAsString(lineInputs != null ? lineInputs : List.of()); } catch (Exception ignored) {}
        ClaimEntity claim = ClaimEntity.draft(tenantId, podId, coverageId, member.getClientId(),
                facilityId, providerId, encounterId, claimType != null ? claimType : "OUTPATIENT", lineSummary, total);
        claim.setPlanVersionId(plan != null ? plan.getPlanVersionId() : null);
        claim.setAuthorisationId(authorisationId);
        claim.setReferralId(referralId);
        claimRepository.save(claim);
        int n = 1;
        if (lineInputs != null) {
            for (LineInput li : lineInputs) {
                ClaimLineEntity line = ClaimLineEntity.create(tenantId, claim.getId(), n++, li.benefitCode(),
                        li.submittedAmount() != null ? li.submittedAmount() : BigDecimal.ZERO);
                line.setServiceCode(li.serviceCode());
                line.setDescription(li.description());
                if (li.quantity() != null) line.setQuantity(li.quantity());
                lineRepository.save(line);
                total = total.add(line.getSubmittedAmount());
            }
        }
        claim.setTotalAmount(total);
        claimRepository.save(claim);
        logEvent(claim, "created", null, "DRAFT", providerId, null, correlationId);
        emit("created", claim, correlationId);
        return claim;
    }

    /** Claim scrubbing (spec §19.3 / §2.8): blocking errors + warnings before submission. */
    @Transactional
    public ScrubResult scrub(UUID tenantId, UUID claimId) {
        ClaimEntity claim = get(tenantId, claimId);
        List<String> blocking = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(claim.getCoverageId(), tenantId).orElse(null);
        if (member == null) blocking.add("COVERAGE_NOT_FOUND");
        else if (!Set.of("ACTIVE", "VERIFIED", "GRACE_PERIOD").contains(member.getStatus())) {
            blocking.add("COVERAGE_INACTIVE:" + member.getStatus());
        } else if (!"VERIFIED".equals(member.getVerificationStatus())) {
            warnings.add("COVERAGE_UNVERIFIED:" + member.getVerificationStatus());
        }
        if (claim.getProviderId() == null || claim.getProviderId().isBlank()) blocking.add("PROVIDER_MISSING");
        if (claim.getFacilityId() == null || claim.getFacilityId().isBlank()) blocking.add("FACILITY_MISSING");

        List<ClaimLineEntity> lines = lineRepository.findByClaimIdOrderByLineNumber(claimId);
        if (lines.isEmpty()) blocking.add("NO_CLAIM_LINES");
        UUID planVersionId = claim.getPlanVersionId();
        BigDecimal sum = BigDecimal.ZERO;
        for (ClaimLineEntity l : lines) {
            if (l.getSubmittedAmount() == null || l.getSubmittedAmount().signum() < 0) blocking.add("LINE_AMOUNT_INVALID");
            else sum = sum.add(l.getSubmittedAmount());
            BenefitDefinitionEntity def = planVersionId == null ? null : benefitRepository
                    .findByTenantIdAndPlanVersionIdAndBenefitCode(tenantId, planVersionId, l.getBenefitCode()).orElse(null);
            if (def == null) warnings.add("BENEFIT_NOT_COVERED:" + l.getBenefitCode());
            else if (def.isRequiresAuthorisation() && claim.getAuthorisationId() == null) {
                blocking.add("AUTHORISATION_REQUIRED:" + l.getBenefitCode());
            }
        }
        if (claim.getTotalAmount() != null && sum.compareTo(claim.getTotalAmount()) != 0) {
            warnings.add("TOTAL_MISMATCH");
        }

        ScrubResult result = new ScrubResult(blocking.isEmpty(), blocking, warnings);
        try {
            claim.setScrubResult(MAPPER.writeValueAsString(Map.of("pass", result.pass(),
                    "blocking", blocking, "warnings", warnings)));
        } catch (Exception ignored) {}
        claim.setStatus(result.pass() ? "VALIDATED" : "VALIDATION_FAILED");
        claimRepository.save(claim);
        logEvent(claim, "scrubbed", "DRAFT", claim.getStatus(), null,
                result.pass() ? null : String.join(",", blocking), null);
        return result;
    }

    @Transactional
    public ClaimEntity submit(UUID tenantId, UUID claimId, UUID correlationId) {
        ClaimEntity claim = get(tenantId, claimId);
        if (!Set.of("DRAFT", "VALIDATED", "VALIDATION_FAILED").contains(claim.getStatus())) {
            throw new IllegalStateException("Claim cannot be submitted from status " + claim.getStatus());
        }
        ScrubResult scrub = scrub(tenantId, claimId);
        if (!scrub.pass()) {
            throw new IllegalStateException("Claim failed validation: " + String.join(",", scrub.blocking()));
        }
        claim.setStatus("SUBMITTED");
        claim.setSubmittedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        logEvent(claim, "submitted", "VALIDATED", "SUBMITTED", claim.getProviderId(), null, correlationId);
        emit("submitted", claim, correlationId);
        return claim;
    }

    // ---- Adjudication ------------------------------------------------------

    @Transactional
    public ClaimEntity adjudicate(UUID tenantId, String podId, UUID claimId, UUID correlationId) {
        ClaimEntity claim = get(tenantId, claimId);
        if (!Set.of("SUBMITTED", "ACKNOWLEDGED", "IN_ADJUDICATION", "PENDED").contains(claim.getStatus())) {
            throw new IllegalStateException("Claim not in an adjudicable state: " + claim.getStatus());
        }
        UUID planVersionId = claim.getPlanVersionId();
        List<ClaimLineEntity> lines = lineRepository.findByClaimIdOrderByLineNumber(claimId);
        List<String> claimReasons = new ArrayList<>();

        BigDecimal totalAllowed = BigDecimal.ZERO, totalApproved = BigDecimal.ZERO,
                totalDenied = BigDecimal.ZERO, totalPatient = BigDecimal.ZERO;
        int approvedLines = 0, deniedLines = 0;

        for (ClaimLineEntity line : lines) {
            List<String> reasons = new ArrayList<>();
            BigDecimal submitted = line.getSubmittedAmount();
            BenefitDefinitionEntity def = planVersionId == null ? null : benefitRepository
                    .findByTenantIdAndPlanVersionIdAndBenefitCode(tenantId, planVersionId, line.getBenefitCode()).orElse(null);

            if (def == null) {
                denyLine(line, submitted, List.of("BENEFIT_NOT_COVERED"));
                totalDenied = totalDenied.add(submitted);
                totalPatient = totalPatient.add(submitted);
                deniedLines++;
                continue;
            }
            // Authorisation matching for benefits that require it.
            if (def.isRequiresAuthorisation()) {
                boolean authorised = hasApprovedAuthLine(tenantId, claim.getAuthorisationId(), line.getBenefitCode());
                if (!authorised) {
                    denyLine(line, submitted, List.of("AUTHORISATION_REQUIRED"));
                    totalDenied = totalDenied.add(submitted);
                    totalPatient = totalPatient.add(submitted);
                    deniedLines++;
                    continue;
                }
                reasons.add("AUTHORISATION_MATCHED");
            }

            BigDecimal allowed = submitted; // Wave 3: contractual-allowed == submitted; tariff carve-out is a refinement.
            BigDecimal copay = def.getCopayAmount() != null ? def.getCopayAmount() : BigDecimal.ZERO;
            BigDecimal pct = def.getCoveragePercent() != null ? def.getCoveragePercent() : new BigDecimal("100");
            BigDecimal coveredBase = allowed.subtract(copay).max(BigDecimal.ZERO);
            BigDecimal payer = coveredBase.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal coinsurance = coveredBase.subtract(payer).max(BigDecimal.ZERO);

            // Consume the covered share from the reservation-aware accumulator (converts an
            // authorisation reservation where present; else direct within the limit).
            String ref = "claim:" + claim.getId() + ":" + line.getId();
            MovementResult mv = payer.signum() > 0
                    ? accumulatorService.consume(tenantId, podId, claim.getCoverageId(), line.getBenefitCode(), payer, ref, correlationId)
                    : null;
            if (mv != null && !mv.applied()) {
                // Benefit exhausted — deny the covered portion; patient bears copay + full charge share.
                denyLine(line, submitted, List.of("BENEFIT_LIMIT_EXCEEDED"));
                totalDenied = totalDenied.add(submitted);
                totalPatient = totalPatient.add(submitted);
                deniedLines++;
                continue;
            }
            line.setConsumeRef(ref);
            line.setAllowedAmount(allowed);
            line.setApprovedAmount(payer);
            line.setDeniedAmount(BigDecimal.ZERO);
            line.setCopay(copay);
            line.setCoinsurance(coinsurance);
            BigDecimal patient = copay.add(coinsurance).setScale(2, RoundingMode.HALF_UP);
            line.setPatientResponsibility(patient);
            line.setLineStatus("APPROVED");
            reasons.add("COVERED_AT_" + pct.stripTrailingZeros().toPlainString() + "PCT");
            setReasons(line, reasons);
            lineRepository.save(line);

            totalAllowed = totalAllowed.add(allowed);
            totalApproved = totalApproved.add(payer);
            totalPatient = totalPatient.add(patient);
            approvedLines++;
        }

        String status = deniedLines == 0 ? "APPROVED" : (approvedLines == 0 ? "DENIED" : "PARTIALLY_APPROVED");
        if (approvedLines > 0) claimReasons.add("APPROVED_LINES:" + approvedLines);
        if (deniedLines > 0) claimReasons.add("DENIED_LINES:" + deniedLines);

        claim.setStatus("IN_ADJUDICATION");
        claim.setAllowedAmount(totalAllowed);
        claim.setApprovedAmount(totalApproved);
        claim.setDeniedAmount(totalDenied);
        claim.setPatientResponsibility(totalPatient);
        claim.setRulesetVersion(RULESET_VERSION);
        setClaimReasons(claim, claimReasons);
        claim.setStatus(status);
        claim.setAdjudicatedAt(OffsetDateTime.now());
        claimRepository.save(claim);
        logEvent(claim, "adjudicated", "SUBMITTED", status, "adjudication-engine", null, correlationId);
        emit("adjudicated", claim, correlationId);

        // Settlement instruction for an approved payer liability (MUSHEX executes payout — gated).
        if (totalApproved.signum() > 0) {
            initiateSettlement(tenantId, podId, claim, totalApproved, correlationId);
        }
        return claim;
    }

    private void initiateSettlement(UUID tenantId, String podId, ClaimEntity claim, BigDecimal amount, UUID correlationId) {
        String ref = "SET-" + claim.getClaimNumber();
        RemittanceEntity rem = new RemittanceEntity(tenantId, podId, resolvePayer(claim), claim.getProviderId(),
                amount, claim.getCurrency(), ref, null, "Settlement instruction for claim " + claim.getClaimNumber());
        rem.setClaimId(claim.getId());
        rem.setStatus("PENDING");
        rem.setSettlementStatus("PENDING");
        remittanceRepository.save(rem);
        claim.setSettlementRef(ref);
        claim.setStatus("PAYMENT_SCHEDULED");
        claimRepository.save(claim);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("claim_id", claim.getId().toString());
        payload.put("claim_number", claim.getClaimNumber());
        payload.put("remittance_id", rem.getId().toString());
        payload.put("amount", amount);
        payload.put("provider_id", claim.getProviderId());
        payload.put("settlement_ref", ref);
        payload.put("rail", "MUSHEX");
        payload.put("note", "Approved liability; MUSHEX executes payout (credential-gated).");
        eventService.emitDomain("SETTLEMENT", rem.getId().toString(), "coverage.settlement.initiated",
                correlationId, tenantId, podId, claim.getId().toString(), "CLAIM", payload);
        logEvent(claim, "settlement-initiated", "PARTIALLY_APPROVED/APPROVED", "PAYMENT_SCHEDULED",
                "settlement-engine", null, correlationId);
    }

    // ---- Lineage: void / replace / reverse --------------------------------

    @Transactional
    public ClaimEntity voidClaim(UUID tenantId, UUID claimId, String reason, UUID correlationId) {
        ClaimEntity claim = get(tenantId, claimId);
        if (Set.of("PAID", "VOIDED", "REVERSED").contains(claim.getStatus())) {
            throw new IllegalStateException("Claim cannot be voided from status " + claim.getStatus());
        }
        releaseLineReservations(tenantId, claim, "void");
        claim.setStatus("VOIDED");
        claimRepository.save(claim);
        logEvent(claim, "voided", null, "VOIDED", null, reason, correlationId);
        emit("voided", claim, correlationId);
        return claim;
    }

    @Transactional
    public ClaimEntity reverse(UUID tenantId, UUID claimId, String reason, UUID correlationId) {
        ClaimEntity claim = get(tenantId, claimId);
        releaseLineReservations(tenantId, claim, "reverse");
        claim.setReversed(true);
        claim.setStatus("REVERSED");
        claimRepository.save(claim);
        logEvent(claim, "reversed", null, "REVERSED", null, reason, correlationId);
        emit("reversed", claim, correlationId);
        return claim;
    }

    @Transactional
    public ClaimEntity replace(UUID tenantId, String podId, UUID originalId, UUID correlationId) {
        ClaimEntity original = get(tenantId, originalId);
        List<ClaimLineEntity> origLines = lineRepository.findByClaimIdOrderByLineNumber(originalId);
        List<LineInput> inputs = origLines.stream()
                .map(l -> new LineInput(l.getBenefitCode(), l.getServiceCode(), l.getDescription(),
                        l.getQuantity(), l.getSubmittedAmount()))
                .toList();
        ClaimEntity replacement = createDraft(tenantId, podId, original.getCoverageId(), original.getClaimType(),
                original.getFacilityId(), original.getProviderId(), original.getEncounterId(),
                original.getAuthorisationId(), original.getReferralId(), inputs, correlationId);
        replacement.setReplacesClaimId(original.getId());
        claimRepository.save(replacement);
        original.setReplacedByClaimId(replacement.getId());
        original.setStatus("VOIDED");
        releaseLineReservations(tenantId, original, "replace");
        claimRepository.save(original);
        logEvent(replacement, "replaces", null, "DRAFT", null, "replaces " + original.getClaimNumber(), correlationId);
        logEvent(original, "replaced", null, "VOIDED", null, "replaced by " + replacement.getClaimNumber(), correlationId);
        return replacement;
    }

    private void releaseLineReservations(UUID tenantId, ClaimEntity claim, String action) {
        for (ClaimLineEntity l : lineRepository.findByClaimIdOrderByLineNumber(claim.getId())) {
            if (l.getConsumeRef() != null && l.getApprovedAmount() != null && l.getApprovedAmount().signum() > 0) {
                // Return the consumed covered share to the accumulator (idempotent by a distinct ref).
                try {
                    accumulatorService.release(tenantId, claim.getPodId(), claim.getCoverageId(), l.getBenefitCode(),
                            l.getApprovedAmount(), l.getConsumeRef() + ":" + action, null);
                } catch (RuntimeException e) {
                    log.warn("Reservation release skipped for line {} ({}): {}", l.getId(), action, e.getMessage());
                }
            }
        }
    }

    // ---- helpers -----------------------------------------------------------

    private boolean hasApprovedAuthLine(UUID tenantId, UUID authorisationId, String benefitCode) {
        if (authorisationId == null) return false;
        AuthorisationEntity auth = authRepository.findByIdAndTenantId(authorisationId, tenantId).orElse(null);
        if (auth == null || !Set.of("APPROVED", "PARTIALLY_APPROVED", "USED").contains(auth.getStatus())) return false;
        return authLineRepository.findByAuthorisationId(authorisationId).stream()
                .anyMatch(al -> al.getBenefitCode().equalsIgnoreCase(benefitCode)
                        && Set.of("APPROVED", "PARTIALLY_APPROVED").contains(al.getLineStatus()));
    }

    private String resolvePayer(ClaimEntity claim) {
        MemberCoverageEntity m = memberRepository.findById(claim.getCoverageId()).orElse(null);
        if (m == null) return "UNKNOWN";
        CoveragePlanEntity plan = planRepository.findById(m.getPlanId()).orElse(null);
        return plan != null ? plan.getPayerId() : "UNKNOWN";
    }

    private void denyLine(ClaimLineEntity line, BigDecimal submitted, List<String> reasons) {
        line.setAllowedAmount(BigDecimal.ZERO);
        line.setApprovedAmount(BigDecimal.ZERO);
        line.setDeniedAmount(submitted);
        line.setPatientResponsibility(submitted);
        line.setLineStatus("DENIED");
        setReasons(line, reasons);
        lineRepository.save(line);
    }

    private void setReasons(ClaimLineEntity line, List<String> reasons) {
        try { line.setReasonCodes(MAPPER.writeValueAsString(reasons)); } catch (Exception ignored) {}
    }

    private void setClaimReasons(ClaimEntity claim, List<String> reasons) {
        try { claim.setReasonCodes(MAPPER.writeValueAsString(reasons)); } catch (Exception ignored) {}
    }

    private void logEvent(ClaimEntity claim, String type, String from, String to, String actor,
                          String reason, UUID correlationId) {
        eventRepository.save(ClaimEventEntity.of(claim.getTenantId(), claim.getId(), type, from, to, actor, reason, correlationId));
    }

    private void emit(String action, ClaimEntity claim, UUID correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("claim_id", claim.getId().toString());
        payload.put("claim_number", claim.getClaimNumber());
        payload.put("coverage_id", claim.getCoverageId().toString());
        payload.put("status", claim.getStatus());
        payload.put("approved_amount", claim.getApprovedAmount());
        payload.put("patient_responsibility", claim.getPatientResponsibility());
        eventService.emitDomain("CLAIM", claim.getId().toString(), "coverage.claim." + action,
                correlationId, claim.getTenantId(), claim.getPodId(), claim.getCoverageId().toString(), "CLAIM", payload);
    }
}
