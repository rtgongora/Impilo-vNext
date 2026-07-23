package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.LiabilityEstimateEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.BenefitDefinitionRepository;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.LiabilityEstimateRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Patient-liability estimation (spec §18). COSTA owns price; Coverage applies benefit and
 * payer-responsibility rules. The caller supplies the COSTA standard charge; this computes the
 * allowed amount, payer estimate and patient responsibility from the plan's benefit definition:
 *
 *   allowed          = standardCharge (Wave-2: contractual allowed == charge; tariff carve-out is W3)
 *   payer estimate   = (allowed - copay) * coveragePercent
 *   coinsurance      = (allowed - copay) - payerEstimate   (the member's share of the covered part)
 *   patient          = copay + coinsurance + nonCovered
 *
 * An estimate is explicitly NOT a final claim determination (recorded in assumptions).
 */
@Service
public class LiabilityEstimateService {

    public static final String RULESET_VERSION = "ruvimbo-liability-v1";
    private static final long VALIDITY_SECONDS = 7L * 24 * 3600;

    private final MemberCoverageRepository memberRepository;
    private final CoveragePlanRepository planRepository;
    private final BenefitDefinitionRepository benefitRepository;
    private final LiabilityEstimateRepository estimateRepository;
    private final CoverageEventService eventService;

    public LiabilityEstimateService(MemberCoverageRepository memberRepository,
                                    CoveragePlanRepository planRepository,
                                    BenefitDefinitionRepository benefitRepository,
                                    LiabilityEstimateRepository estimateRepository,
                                    CoverageEventService eventService) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.benefitRepository = benefitRepository;
        this.estimateRepository = estimateRepository;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<LiabilityEstimateEntity> forMember(UUID tenantId, String memberCpid) {
        return estimateRepository.findByTenantIdAndMemberCpidOrderByCreatedAtDesc(tenantId, memberCpid);
    }

    @Transactional
    public LiabilityEstimateEntity estimate(UUID tenantId, String podId, UUID coverageId, String benefitCode,
                                            BigDecimal standardCharge, String facilityId, UUID correlationId) {
        if (standardCharge == null || standardCharge.signum() < 0) {
            throw new IllegalArgumentException("standardCharge must be a non-negative amount from COSTA");
        }
        MemberCoverageEntity member = memberRepository.findByIdAndTenantId(coverageId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Coverage not found: " + coverageId));
        CoveragePlanEntity plan = planRepository.findById(member.getPlanId()).orElse(null);
        UUID planVersionId = plan != null ? plan.getPlanVersionId() : null;

        BigDecimal copay = BigDecimal.ZERO;
        BigDecimal coveragePct = new BigDecimal("100.00");
        String currency = "USD";
        String assumptions;
        boolean requiresAuthorisation = false;
        if (planVersionId != null && benefitCode != null) {
            BenefitDefinitionEntity def = benefitRepository
                    .findByTenantIdAndPlanVersionIdAndBenefitCode(tenantId, planVersionId, benefitCode)
                    .orElse(null);
            if (def != null) {
                copay = def.getCopayAmount() != null ? def.getCopayAmount() : BigDecimal.ZERO;
                coveragePct = def.getCoveragePercent() != null ? def.getCoveragePercent() : coveragePct;
                currency = def.getCurrency();
                // OF-B8 (§10.6): PA requirement recorded as an estimate assumption.
                requiresAuthorisation = def.isRequiresAuthorisation();
                assumptions = "Covered benefit " + benefitCode + " at " + coveragePct + "% after "
                        + copay + " co-pay."
                        + (requiresAuthorisation ? " Prior authorisation REQUIRED for this benefit." : "")
                        + " Estimate only — not a final claim determination.";
            } else {
                coveragePct = BigDecimal.ZERO;
                assumptions = "Benefit " + benefitCode + " not covered on this plan — full charge is patient responsibility.";
            }
        } else {
            coveragePct = BigDecimal.ZERO;
            assumptions = "No plan benefit resolved — treated as non-covered. Estimate only.";
        }

        BigDecimal allowed = standardCharge; // Wave 2: contractual-allowed == charge; tariff carve-out is W3.
        BigDecimal coveredBase = allowed.subtract(copay).max(BigDecimal.ZERO);
        BigDecimal payerEstimate;
        BigDecimal coinsurance;
        BigDecimal nonCovered;
        BigDecimal patient;
        if (coveragePct.signum() == 0) {
            // Non-covered benefit: no payer contribution — the covered base is patient-borne.
            payerEstimate = BigDecimal.ZERO;
            coinsurance = BigDecimal.ZERO;
            nonCovered = coveredBase;
            patient = copay.add(nonCovered);
        } else {
            payerEstimate = coveredBase.multiply(coveragePct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            coinsurance = coveredBase.subtract(payerEstimate).max(BigDecimal.ZERO);
            nonCovered = BigDecimal.ZERO;
            patient = copay.add(coinsurance);
        }
        patient = patient.setScale(2, RoundingMode.HALF_UP);

        LiabilityEstimateEntity e = new LiabilityEstimateEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(tenantId);
        e.setPodId(podId);
        e.setCoverageId(coverageId);
        e.setMemberCpid(member.getClientId());
        e.setFacilityId(facilityId);
        e.setBenefitCode(benefitCode);
        e.setStandardCharge(standardCharge.setScale(2, RoundingMode.HALF_UP));
        e.setAllowedAmount(allowed.setScale(2, RoundingMode.HALF_UP));
        e.setPayerEstimate(payerEstimate.setScale(2, RoundingMode.HALF_UP));
        e.setCopay(copay.setScale(2, RoundingMode.HALF_UP));
        e.setCoinsurance(coinsurance.setScale(2, RoundingMode.HALF_UP));
        e.setNonCovered(nonCovered.setScale(2, RoundingMode.HALF_UP));
        e.setPatientResponsibility(patient);
        e.setCurrency(currency);
        e.setAssumptions(assumptions);
        e.setRequiresAuthorisation(requiresAuthorisation);
        e.setRulesetVersion(RULESET_VERSION);
        e.setExpiresAt(OffsetDateTime.now().plusSeconds(VALIDITY_SECONDS));
        estimateRepository.save(e);

        java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("estimate_id", e.getId().toString());
        payload.put("coverage_id", coverageId.toString());
        payload.put("benefit_code", benefitCode);
        payload.put("patient_responsibility", patient);
        payload.put("payer_estimate", e.getPayerEstimate());
        eventService.emitDomain("LIABILITY_ESTIMATE", e.getId().toString(),
                "coverage.liability.estimated", correlationId, tenantId, podId,
                coverageId.toString(), "LIABILITY_ESTIMATE", payload);
        return e;
    }
}
