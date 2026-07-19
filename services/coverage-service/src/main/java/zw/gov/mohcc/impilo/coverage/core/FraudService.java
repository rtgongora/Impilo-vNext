package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.ClaimEntity;
import zw.gov.mohcc.impilo.coverage.domain.ClaimLineEntity;
import zw.gov.mohcc.impilo.coverage.domain.FraudFlagEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.ClaimLineRepository;
import zw.gov.mohcc.impilo.coverage.repository.ClaimRepository;
import zw.gov.mohcc.impilo.coverage.repository.FraudFlagRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.math.BigDecimal;
import java.util.*;

/**
 * Fraud, waste & abuse controls (spec §25). Automated detection creates risk flags; adverse
 * action must follow a governed review (open -> reviewing -> confirmed/dismissed). Detection
 * never auto-denies — it only flags.
 */
@Service
public class FraudService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final FraudFlagRepository flagRepository;
    private final ClaimRepository claimRepository;
    private final ClaimLineRepository claimLineRepository;
    private final MemberCoverageRepository memberRepository;
    private final CoverageEventService eventService;

    public FraudService(FraudFlagRepository flagRepository, ClaimRepository claimRepository,
                        ClaimLineRepository claimLineRepository, MemberCoverageRepository memberRepository,
                        CoverageEventService eventService) {
        this.flagRepository = flagRepository;
        this.claimRepository = claimRepository;
        this.claimLineRepository = claimLineRepository;
        this.memberRepository = memberRepository;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<FraudFlagEntity> list(UUID tenantId, String status) {
        return status != null && !status.isBlank()
                ? flagRepository.findByTenantIdAndStatusOrderByCreatedAtDesc(tenantId, status.trim().toUpperCase())
                : flagRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    /**
     * Screen a claim for common FWA signals (spec §25): duplicate claim, claim after membership
     * termination, impossible amount. Returns the flags raised (also persisted).
     */
    @Transactional
    public List<FraudFlagEntity> screenClaim(UUID tenantId, String podId, UUID claimId, UUID correlationId) {
        ClaimEntity claim = claimRepository.findByIdAndTenantId(claimId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Claim not found: " + claimId));
        List<FraudFlagEntity> raised = new ArrayList<>();

        // 1. Duplicate claim: same member + provider + encounter on another claim.
        if (claim.getEncounterId() != null) {
            long dupes = claimRepository.findByTenantIdAndCoverageId(tenantId, claim.getCoverageId()).stream()
                    .filter(c -> !c.getId().equals(claim.getId()))
                    .filter(c -> Objects.equals(c.getEncounterId(), claim.getEncounterId())
                            && Objects.equals(c.getProviderId(), claim.getProviderId())
                            && !Set.of("VOIDED", "REVERSED").contains(c.getStatus()))
                    .count();
            if (dupes > 0) raised.add(flag(tenantId, podId, "CLAIM", claimId.toString(), "DUPLICATE_CLAIM", "HIGH",
                    Map.of("encounter", claim.getEncounterId(), "provider", claim.getProviderId(), "duplicates", dupes), correlationId));
        }

        // 2. Claim after membership termination.
        MemberCoverageEntity member = memberRepository.findById(claim.getCoverageId()).orElse(null);
        if (member != null && Set.of("TERMINATED", "CANCELLED", "EXPIRED").contains(member.getStatus())) {
            raised.add(flag(tenantId, podId, "CLAIM", claimId.toString(), "CLAIM_AFTER_TERMINATION", "HIGH",
                    Map.of("membership_status", member.getStatus()), correlationId));
        }

        // 3. Impossible amount: any line submitted > 100000, or total <= 0.
        BigDecimal cap = new BigDecimal("100000");
        boolean impossible = claim.getTotalAmount() == null || claim.getTotalAmount().signum() <= 0;
        for (ClaimLineEntity l : claimLineRepository.findByClaimIdOrderByLineNumber(claimId)) {
            if (l.getSubmittedAmount() != null && l.getSubmittedAmount().compareTo(cap) > 0) impossible = true;
        }
        if (impossible) raised.add(flag(tenantId, podId, "CLAIM", claimId.toString(), "IMPOSSIBLE_AMOUNT", "MEDIUM",
                Map.of("total", String.valueOf(claim.getTotalAmount())), correlationId));

        return raised;
    }

    @Transactional
    public FraudFlagEntity review(UUID tenantId, UUID flagId, String status, String reviewerId, String resolution) {
        FraudFlagEntity flag = flagRepository.findByIdAndTenantId(flagId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Fraud flag not found: " + flagId));
        String target = status == null ? "" : status.trim().toUpperCase();
        if (!Set.of("REVIEWING", "CONFIRMED", "DISMISSED").contains(target)) {
            throw new IllegalArgumentException("Invalid review status: " + status);
        }
        flag.setStatus(target);
        flag.setReviewerId(reviewerId);
        flag.setResolution(resolution);
        return flagRepository.save(flag);
    }

    private FraudFlagEntity flag(UUID tenantId, String podId, String subjectType, String subjectRef,
                                 String flagType, String severity, Map<String, Object> detail, UUID correlationId) {
        String json;
        try { json = MAPPER.writeValueAsString(detail); } catch (Exception e) { json = "{}"; }
        FraudFlagEntity f = flagRepository.save(FraudFlagEntity.create(tenantId, podId, subjectType, subjectRef,
                flagType, severity, json));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("flag_id", f.getId().toString());
        payload.put("subject_type", subjectType);
        payload.put("subject_ref", subjectRef);
        payload.put("flag_type", flagType);
        payload.put("severity", severity);
        eventService.emitDomain("FRAUD_FLAG", f.getId().toString(), "coverage.fraud.flagged",
                correlationId, tenantId, podId, subjectRef, "FRAUD_FLAG", payload);
        return f;
    }
}
