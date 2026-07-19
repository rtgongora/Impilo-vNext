package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationEntity;
import zw.gov.mohcc.impilo.coverage.domain.AuthorisationLineEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.AuthorisationLineRepository;
import zw.gov.mohcc.impilo.coverage.repository.AuthorisationRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authorisation lifecycle (spec §17). The 14-status header machine + line-level decisions.
 * On approval, each approved line reserves benefit through the V015 accumulator ledger
 * (idempotent by the line id), so a pending authorisation cannot be double-spent at claim time.
 */
@Service
public class AuthorisationService {

    /** Legal authorisation status transitions (spec §17.2). */
    private static final Map<String, Set<String>> TRANSITIONS = Map.ofEntries(
            Map.entry("DRAFT", Set.of("SUBMITTED", "CANCELLED")),
            Map.entry("SUBMITTED", Set.of("ACKNOWLEDGED", "IN_REVIEW", "INFORMATION_REQUESTED",
                    "APPROVED", "PARTIALLY_APPROVED", "DENIED", "WITHDRAWN")),
            Map.entry("ACKNOWLEDGED", Set.of("IN_REVIEW", "INFORMATION_REQUESTED", "APPROVED",
                    "PARTIALLY_APPROVED", "DENIED", "WITHDRAWN")),
            Map.entry("IN_REVIEW", Set.of("INFORMATION_REQUESTED", "APPROVED", "PARTIALLY_APPROVED", "DENIED")),
            Map.entry("INFORMATION_REQUESTED", Set.of("IN_REVIEW", "APPROVED", "PARTIALLY_APPROVED",
                    "DENIED", "WITHDRAWN")),
            Map.entry("APPROVED", Set.of("USED", "EXPIRED", "CANCELLED", "APPEALED")),
            Map.entry("PARTIALLY_APPROVED", Set.of("USED", "EXPIRED", "CANCELLED", "APPEALED", "APPROVED")),
            Map.entry("DENIED", Set.of("APPEALED", "CANCELLED")),
            Map.entry("APPEALED", Set.of("OVERTURNED", "DENIED", "APPROVED")),
            Map.entry("OVERTURNED", Set.of("APPROVED", "USED")),
            Map.entry("WITHDRAWN", Set.of()),
            Map.entry("EXPIRED", Set.of()),
            Map.entry("USED", Set.of()),
            Map.entry("CANCELLED", Set.of()));

    private static final Set<String> AUTH_TYPES = Set.of(
            "PRIOR", "CONCURRENT", "EXTENSION", "RETROSPECTIVE", "EMERGENCY_NOTIFICATION", "ADMISSION",
            "PROCEDURE", "MEDICINE", "DIAGNOSTIC", "REHABILITATION", "TRANSPORT", "CHRONIC");

    private final AuthorisationRepository authRepository;
    private final AuthorisationLineRepository lineRepository;
    private final MemberCoverageRepository memberRepository;
    private final BenefitAccumulatorService accumulatorService;
    private final CoverageEventService eventService;

    public AuthorisationService(AuthorisationRepository authRepository,
                                AuthorisationLineRepository lineRepository,
                                MemberCoverageRepository memberRepository,
                                BenefitAccumulatorService accumulatorService,
                                CoverageEventService eventService) {
        this.authRepository = authRepository;
        this.lineRepository = lineRepository;
        this.memberRepository = memberRepository;
        this.accumulatorService = accumulatorService;
        this.eventService = eventService;
    }

    public record LineInput(String benefitCode, String serviceCode, String description,
                            BigDecimal quantityRequested, BigDecimal estimatedAmount) {}

    public record LineDecision(UUID lineId, String outcome, BigDecimal quantityApproved,
                               BigDecimal approvedAmount, String reason) {}

    @Transactional(readOnly = true)
    public AuthorisationEntity get(UUID tenantId, UUID authId) {
        return authRepository.findByIdAndTenantId(authId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Authorisation not found: " + authId));
    }

    @Transactional(readOnly = true)
    public List<AuthorisationLineEntity> lines(UUID authId) {
        return lineRepository.findByAuthorisationId(authId);
    }

    @Transactional(readOnly = true)
    public List<AuthorisationEntity> forMember(UUID tenantId, String memberCpid) {
        return authRepository.findByTenantIdAndMemberCpidOrderByCreatedAtDesc(tenantId, memberCpid);
    }

    @Transactional(readOnly = true)
    public List<AuthorisationEntity> queue(UUID tenantId, String status) {
        return authRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status);
    }

    @Transactional
    public AuthorisationEntity create(UUID tenantId, String podId, UUID coverageId, String authType,
                                      String requestingProviderId, String facilityId, UUID referralId,
                                      String diagnosisSystem, String diagnosisCode, String justification,
                                      String urgency, LocalDate proposedServiceDate, List<LineInput> lines,
                                      UUID correlationId) {
        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(coverageId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage not found: " + coverageId));
        AuthorisationEntity a = AuthorisationEntity.create(tenantId, podId, coverageId, member.getClientId());
        if (authType != null && !authType.isBlank()) {
            String t = authType.trim().toUpperCase();
            if (!AUTH_TYPES.contains(t)) throw new IllegalArgumentException("Unknown auth_type: " + authType);
            a.setAuthType(t);
        }
        a.setRequestingProviderId(requestingProviderId);
        a.setFacilityId(facilityId);
        a.setReferralId(referralId);
        a.setDiagnosisSystem(diagnosisSystem);
        a.setDiagnosisCode(diagnosisCode);
        a.setClinicalJustification(justification);
        if (urgency != null && !urgency.isBlank()) a.setUrgency(urgency.trim().toUpperCase());
        a.setProposedServiceDate(proposedServiceDate);
        a.setCorrelationId(correlationId);
        BigDecimal total = BigDecimal.ZERO;
        authRepository.save(a);
        if (lines != null) {
            for (LineInput li : lines) {
                AuthorisationLineEntity line = AuthorisationLineEntity.create(tenantId, a.getId(), li.benefitCode());
                line.setServiceCode(li.serviceCode());
                line.setDescription(li.description());
                if (li.quantityRequested() != null) line.setQuantityRequested(li.quantityRequested());
                line.setEstimatedAmount(li.estimatedAmount());
                lineRepository.save(line);
                if (li.estimatedAmount() != null) total = total.add(li.estimatedAmount());
            }
        }
        a.setEstimatedAmount(total);
        authRepository.save(a);
        emit("created", a, correlationId); // status stays DRAFT; distinct event type avoids outbox key collisions
        return a;
    }

    @Transactional
    public AuthorisationEntity transition(UUID tenantId, String podId, UUID authId, String target,
                                          String reviewerId, String note, UUID correlationId) {
        AuthorisationEntity a = get(tenantId, authId);
        applyTransition(a, target == null ? "" : target.trim().toUpperCase());
        if (reviewerId != null) a.setReviewerId(reviewerId);
        if ("INFORMATION_REQUESTED".equals(a.getStatus())) a.setInformationRequest(note);
        if ("WITHDRAWN".equals(a.getStatus()) || "CANCELLED".equals(a.getStatus())) a.setDenialReason(note);
        authRepository.save(a);
        emit(eventAction(a.getStatus()), a, correlationId);
        return a;
    }

    /**
     * Reviewer decision over the lines. Approved/partially-approved lines reserve benefit
     * (idempotent by line id). Header status is derived from the line outcomes and must not
     * conceal a mixed result (spec §6.3 principle).
     */
    @Transactional
    public AuthorisationEntity decide(UUID tenantId, String podId, UUID authId, List<LineDecision> decisions,
                                      String reviewerId, LocalDate validityFrom, LocalDate validityTo,
                                      String conditions, UUID correlationId) {
        AuthorisationEntity a = get(tenantId, authId);
        if (Set.of("APPROVED", "DENIED", "USED", "CANCELLED", "WITHDRAWN", "EXPIRED").contains(a.getStatus())) {
            throw new IllegalStateException("Authorisation already in a terminal decision state: " + a.getStatus());
        }
        Map<UUID, LineDecision> byId = new LinkedHashMap<>();
        if (decisions != null) for (LineDecision d : decisions) byId.put(d.lineId(), d);

        List<AuthorisationLineEntity> lines = lineRepository.findByAuthorisationId(authId);
        int approved = 0, denied = 0;
        for (AuthorisationLineEntity line : lines) {
            LineDecision d = byId.get(line.getId());
            String outcome = d != null && d.outcome() != null ? d.outcome().trim().toUpperCase() : "APPROVED";
            switch (outcome) {
                case "DENIED" -> {
                    line.setLineStatus("DENIED");
                    line.setReason(d != null ? d.reason() : null);
                    denied++;
                }
                case "PARTIALLY_APPROVED", "APPROVED" -> {
                    BigDecimal qty = d != null && d.quantityApproved() != null
                            ? d.quantityApproved() : line.getQuantityRequested();
                    BigDecimal amt = d != null && d.approvedAmount() != null
                            ? d.approvedAmount() : line.getEstimatedAmount();
                    line.setQuantityApproved(qty);
                    line.setApprovedAmount(amt);
                    boolean partial = qty != null && line.getQuantityRequested() != null
                            && qty.compareTo(line.getQuantityRequested()) < 0;
                    line.setLineStatus(partial ? "PARTIALLY_APPROVED" : "APPROVED");
                    // Reserve benefit for the approved amount (idempotent by line id).
                    if (amt != null && amt.signum() > 0) {
                        String ref = "auth:" + a.getId() + ":" + line.getId();
                        line.setReservationRef(ref);
                        accumulatorService.reserve(tenantId, podId, a.getCoverageId(),
                                line.getBenefitCode(), amt, ref, correlationId);
                    }
                    approved++;
                }
                default -> throw new IllegalArgumentException("Unknown line outcome: " + outcome);
            }
            lineRepository.save(line);
        }

        String headerStatus = denied == 0 ? "APPROVED" : (approved == 0 ? "DENIED" : "PARTIALLY_APPROVED");
        applyTransition(a, headerStatus);
        a.setReviewerId(reviewerId);
        a.setDecisionAt(OffsetDateTime.now());
        a.setValidityFrom(validityFrom != null ? validityFrom : LocalDate.now());
        a.setValidityTo(validityTo);
        a.setConditions(conditions);
        authRepository.save(a);
        emit(headerStatus.equals("DENIED") ? "denied" : "approved", a, correlationId);
        return a;
    }

    private void applyTransition(AuthorisationEntity a, String target) {
        if (!TRANSITIONS.containsKey(target)) {
            throw new IllegalArgumentException("Unknown authorisation status: " + target);
        }
        String current = a.getStatus();
        if (current.equals(target)) return;
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new IllegalStateException("Illegal authorisation transition " + current + " -> " + target);
        }
        a.setStatus(target);
    }

    private static String eventAction(String status) {
        return switch (status) {
            case "INFORMATION_REQUESTED" -> "information-requested";
            case "APPROVED", "PARTIALLY_APPROVED" -> "approved";
            case "DENIED" -> "denied";
            case "SUBMITTED" -> "submitted";
            default -> status.toLowerCase();
        };
    }

    private void emit(String action, AuthorisationEntity a, UUID correlationId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("authorisation_id", a.getId().toString());
        payload.put("coverage_id", a.getCoverageId().toString());
        payload.put("member_cpid", a.getMemberCpid());
        payload.put("status", a.getStatus());
        payload.put("auth_type", a.getAuthType());
        eventService.emitDomain("AUTHORISATION", a.getId().toString(),
                "coverage.authorisation." + action, correlationId, a.getTenantId(), a.getPodId(),
                a.getCoverageId().toString(), "AUTHORISATION", payload);
    }
}
