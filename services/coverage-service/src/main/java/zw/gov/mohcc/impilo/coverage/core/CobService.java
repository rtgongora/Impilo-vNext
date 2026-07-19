package zw.gov.mohcc.impilo.coverage.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.*;
import zw.gov.mohcc.impilo.coverage.repository.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Coordination of Benefits (spec §15). Orders a member's active coverages by priority and
 * produces a transparent payment waterfall: each payer in turn covers its benefit percentage of
 * the remaining balance; the patient bears what is left. Invariant: total payer reimbursement
 * never exceeds the allowed amount (no double payment).
 */
@Service
public class CobService {

    public static final String RULESET_VERSION = "ruvimbo-cob-v1";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> ACTIVE = Set.of("ACTIVE", "VERIFIED", "GRACE_PERIOD");

    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final BenefitDefinitionRepository benefitRepository;
    private final CobDecisionRepository cobRepository;
    private final CoverageEventService eventService;

    public CobService(MemberCoverageRepository memberRepository, CoveragePlanRepository planRepository,
                      BenefitDefinitionRepository benefitRepository, CobDecisionRepository cobRepository,
                      CoverageEventService eventService) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.benefitRepository = benefitRepository;
        this.cobRepository = cobRepository;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<CobDecisionEntity> forMember(UUID tenantId, String memberCpid) {
        return cobRepository.findByTenantIdAndMemberCpidOrderByCreatedAtDesc(tenantId, memberCpid);
    }

    @Transactional
    public CobDecisionEntity coordinate(UUID tenantId, String podId, String memberCpid, String benefitCode,
                                        BigDecimal standardCharge, UUID correlationId) {
        if (standardCharge == null || standardCharge.signum() < 0) {
            throw new IllegalArgumentException("standardCharge must be non-negative");
        }
        List<MemberCoverageEntity> coverages = memberRepository.findByTenantIdAndClientId(tenantId, memberCpid).stream()
                .filter(m -> ACTIVE.contains(m.getStatus()) && m.getPrincipalMemberId() == null)
                .sorted(Comparator.comparingInt(MemberCoverageEntity::getCoveragePriority))
                .toList();

        BigDecimal allowed = standardCharge;
        BigDecimal remaining = allowed;
        BigDecimal total = BigDecimal.ZERO;
        List<Map<String, Object>> waterfall = new ArrayList<>();

        for (MemberCoverageEntity cov : coverages) {
            if (remaining.signum() <= 0) break;
            CoveragePlanEntity plan = planRepository.findById(cov.getPlanId()).orElse(null);
            String payer = plan != null ? plan.getPayerId() : "UNKNOWN";
            UUID planVersionId = plan != null ? plan.getPlanVersionId() : null;
            BigDecimal pct = BigDecimal.ZERO;
            if (planVersionId != null && benefitCode != null) {
                BenefitDefinitionEntity def = benefitRepository
                        .findByTenantIdAndPlanVersionIdAndBenefitCode(tenantId, planVersionId, benefitCode).orElse(null);
                if (def != null && def.getCoveragePercent() != null) pct = def.getCoveragePercent();
            }
            BigDecimal pays = remaining.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP).min(remaining);
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("coverageId", cov.getId().toString());
            step.put("payer", payer);
            step.put("priority", cov.getCoveragePriority());
            step.put("coveragePercent", pct);
            step.put("paid", pays);
            waterfall.add(step);
            remaining = remaining.subtract(pays);
            total = total.add(pays);
        }

        CobDecisionEntity decision = new CobDecisionEntity();
        decision.setId(UUID.randomUUID());
        decision.setTenantId(tenantId);
        decision.setPodId(podId);
        decision.setMemberCpid(memberCpid);
        decision.setBenefitCode(benefitCode);
        decision.setStandardCharge(allowed.setScale(2, RoundingMode.HALF_UP));
        decision.setAllowedAmount(allowed.setScale(2, RoundingMode.HALF_UP));
        decision.setTotalPayerPaid(total.setScale(2, RoundingMode.HALF_UP));
        decision.setPatientResponsibility(remaining.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
        decision.setRulesetVersion(RULESET_VERSION);
        try { decision.setWaterfall(MAPPER.writeValueAsString(waterfall)); }
        catch (Exception e) { decision.setWaterfall("[]"); }
        cobRepository.save(decision);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("member_cpid", memberCpid);
        payload.put("benefit_code", benefitCode);
        payload.put("total_payer_paid", decision.getTotalPayerPaid());
        payload.put("patient_responsibility", decision.getPatientResponsibility());
        payload.put("payers", waterfall.size());
        eventService.emitDomain("COB", decision.getId().toString(), "coverage.cob.coordinated",
                correlationId, tenantId, podId, memberCpid, "COB", payload);
        return decision;
    }
}
