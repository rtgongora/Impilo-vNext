package zw.gov.mohcc.impilo.coverage.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.FormularyEntity;
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
 * <p>OF-B8 medication path (Vol II §8.1.5/§10): when the charge line is a MEDICATION —
 * the caller passes {@code medicationCode} (ATC-coded against the ZIBO national registry) —
 * the plan's benefit is resolved through the V020 payer formulary ({@code cv_formulary}):
 * an active covered listing maps the medication to its plan benefit (tier + PA flag recorded);
 * an explicit exclusion or absent/expired listing prices honestly as NON-COVERED — a payer
 * formulary never covers by silence. Non-medication callers pass a benefit code directly and
 * are unchanged.</p>
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
    private final FormularyService formularyService;
    private final CoverageEventService eventService;

    public LiabilityEstimateService(MemberCoverageRepository memberRepository,
                                    CoveragePlanRepository planRepository,
                                    BenefitDefinitionRepository benefitRepository,
                                    LiabilityEstimateRepository estimateRepository,
                                    FormularyService formularyService,
                                    CoverageEventService eventService) {
        this.memberRepository = memberRepository;
        this.planRepository = planRepository;
        this.benefitRepository = benefitRepository;
        this.estimateRepository = estimateRepository;
        this.formularyService = formularyService;
        this.eventService = eventService;
    }

    @Transactional(readOnly = true)
    public List<LiabilityEstimateEntity> forMember(UUID tenantId, String memberCpid) {
        return estimateRepository.findByTenantIdAndMemberCpidOrderByCreatedAtDesc(tenantId, memberCpid);
    }

    /** Non-medication path — the caller names the plan benefit directly (unchanged contract). */
    @Transactional
    public LiabilityEstimateEntity estimate(UUID tenantId, String podId, UUID coverageId, String benefitCode,
                                            BigDecimal standardCharge, String facilityId, UUID correlationId) {
        return estimate(tenantId, podId, coverageId, benefitCode, null, null,
                standardCharge, facilityId, correlationId);
    }

    /**
     * Full path. Exactly one of {@code benefitCode} / {@code medicationCode} drives resolution:
     * a non-null {@code medicationCode} resolves the benefit THROUGH the payer formulary
     * (OF-B8 benefit-code mapping); otherwise {@code benefitCode} is used directly.
     */
    @Transactional
    public LiabilityEstimateEntity estimate(UUID tenantId, String podId, UUID coverageId, String benefitCode,
                                            String medicationCode, String codingSystem,
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
        FormularyEntity formularyEntry = null;
        String resolvedBenefitCode = benefitCode;
        boolean medicationLine = medicationCode != null && !medicationCode.isBlank();

        if (medicationLine && planVersionId != null) {
            // ── OF-B8: medication → payer formulary → benefit mapping ────
            FormularyService.FormularyResolution resolution = formularyService.resolve(
                    tenantId, planVersionId, medicationCode, codingSystem, OffsetDateTime.now());
            switch (resolution.status()) {
                case ON_FORMULARY -> {
                    formularyEntry = resolution.entry();
                    resolvedBenefitCode = formularyEntry.getBenefitCode();
                    BenefitDefinitionEntity def = benefitRepository
                            .findByTenantIdAndPlanVersionIdAndBenefitCode(tenantId, planVersionId, resolvedBenefitCode)
                            .orElse(null);
                    if (def != null) {
                        copay = def.getCopayAmount() != null ? def.getCopayAmount() : BigDecimal.ZERO;
                        coveragePct = def.getCoveragePercent() != null ? def.getCoveragePercent() : coveragePct;
                        currency = def.getCurrency();
                        // Formulary PA flag surfaces EXACTLY like the benefit-level flag.
                        requiresAuthorisation = def.isRequiresAuthorisation()
                                || formularyEntry.isRequiresAuthorisation();
                        assumptions = "Medication " + medicationCode + " on formulary (tier "
                                + formularyEntry.getTier() + ") under benefit " + resolvedBenefitCode
                                + " at " + coveragePct + "% after " + copay + " co-pay."
                                + (requiresAuthorisation ? " Prior authorisation REQUIRED for this medication." : "")
                                + " Estimate only — not a final claim determination.";
                    } else {
                        // A formulary row pointing at an undefined benefit is a
                        // configuration fault — priced honestly as non-covered.
                        coveragePct = BigDecimal.ZERO;
                        assumptions = "Medication " + medicationCode + " maps to benefit " + resolvedBenefitCode
                                + " which this plan version does not define — treated as non-covered. Estimate only.";
                    }
                }
                case EXCLUDED -> {
                    formularyEntry = resolution.entry();
                    resolvedBenefitCode = medicationCode;
                    coveragePct = BigDecimal.ZERO;
                    assumptions = "Medication " + medicationCode
                            + " is explicitly excluded from this plan's formulary — full charge is patient responsibility.";
                }
                case NOT_LISTED -> {
                    resolvedBenefitCode = medicationCode;
                    coveragePct = BigDecimal.ZERO;
                    assumptions = "Medication " + medicationCode
                            + " has no active listing on this plan's formulary — full charge is patient responsibility.";
                }
                default -> throw new IllegalStateException("Unhandled formulary status: " + resolution.status());
            }
        } else if (medicationLine) {
            resolvedBenefitCode = medicationCode;
            coveragePct = BigDecimal.ZERO;
            assumptions = "No plan version resolved for medication " + medicationCode
                    + " — treated as non-covered. Estimate only.";
        } else if (planVersionId != null && benefitCode != null) {
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
        e.setBenefitCode(resolvedBenefitCode);
        if (medicationLine) {
            e.setMedicationCode(medicationCode);
            e.setCodingSystem(codingSystem != null && !codingSystem.isBlank()
                    ? codingSystem : FormularyEntity.ATC_CODING_SYSTEM);
        }
        if (formularyEntry != null) {
            e.setFormularyRef(formularyEntry.getId());
            e.setFormularyTier(formularyEntry.getTier());
        }
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
        payload.put("benefit_code", resolvedBenefitCode);
        if (medicationLine) {
            payload.put("medication_code", medicationCode);
        }
        payload.put("patient_responsibility", patient);
        payload.put("payer_estimate", e.getPayerEstimate());
        eventService.emitDomain("LIABILITY_ESTIMATE", e.getId().toString(),
                "coverage.liability.estimated", correlationId, tenantId, podId,
                coverageId.toString(), "LIABILITY_ESTIMATE", payload);
        return e;
    }
}
