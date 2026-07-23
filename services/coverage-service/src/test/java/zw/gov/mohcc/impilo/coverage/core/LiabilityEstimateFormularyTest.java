package zw.gov.mohcc.impilo.coverage.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * OF-B8 second half — the medication path of the liability engine: an
 * ATC-coded medication line resolves its plan benefit THROUGH the V020 payer
 * formulary (benefit-code mapping), the formulary PA flag surfaces exactly
 * like the benefit-level flag, and exclusion / not-listed / misconfigured
 * mappings price honestly as non-covered. The non-medication path is pinned
 * unchanged by {@link LiabilityEstimateServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class LiabilityEstimateFormularyTest {

    private static final String ATC = FormularyEntity.ATC_CODING_SYSTEM;

    @Mock private MemberCoverageRepository memberRepository;
    @Mock private CoveragePlanRepository planRepository;
    @Mock private BenefitDefinitionRepository benefitRepository;
    @Mock private LiabilityEstimateRepository estimateRepository;
    @Mock private FormularyService formularyService;
    @Mock private CoverageEventService eventService;

    private LiabilityEstimateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID coverageId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID planVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LiabilityEstimateService(memberRepository, planRepository,
                benefitRepository, estimateRepository, formularyService, eventService);

        MemberCoverageEntity member = new MemberCoverageEntity(tenantId, "pod-1", "CPID-1",
                planId, "M-1", "SELF", java.time.LocalDate.now());
        lenient().when(memberRepository.findByIdAndTenantId(coverageId, tenantId))
                .thenReturn(Optional.of(member));

        CoveragePlanEntity plan = new CoveragePlanEntity(tenantId, "pod-1", "PLAN-1",
                "Test Plan", "PAYER-1", "MEDICAL_AID", java.time.LocalDate.now());
        plan.setPlanVersionId(planVersionId);
        lenient().when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        lenient().when(estimateRepository.save(any(LiabilityEstimateEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private BenefitDefinitionEntity benefit(String code, String copay, String pct, boolean requiresPa) {
        BenefitDefinitionEntity def = BenefitDefinitionEntity.create(
                tenantId, "pod-1", planVersionId, code, "MEDICINE", "medicines benefit");
        def.setCopayAmount(new BigDecimal(copay));
        def.setCoveragePercent(new BigDecimal(pct));
        def.setCurrency("USD");
        def.setRequiresAuthorisation(requiresPa);
        return def;
    }

    private FormularyEntity listing(String medCode, String benefitCode, boolean covered,
                                    String tier, boolean requiresPa) {
        FormularyEntity f = FormularyEntity.create(tenantId, "pod-1", planVersionId, medCode, ATC, benefitCode);
        f.setCovered(covered);
        f.setTier(tier);
        f.setRequiresAuthorisation(requiresPa);
        f.setEffectiveFrom(OffsetDateTime.now().minusDays(30));
        return f;
    }

    private void stubResolution(String medCode, FormularyService.FormularyResolution resolution) {
        when(formularyService.resolve(eq(tenantId), eq(planVersionId), eq(medCode), eq(ATC),
                any(OffsetDateTime.class))).thenReturn(resolution);
    }

    // ── covered + mapped ─────────────────────────────────────────────────

    @Test
    void onFormularyMedication_mapsToBenefit_andDecomposesUnderIt() {
        FormularyEntity entry = listing("J01CA04", "ACUTE_MEDS", true, "GENERIC", false);
        stubResolution("J01CA04", new FormularyService.FormularyResolution(
                FormularyService.FormularyStatus.ON_FORMULARY, entry));
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "ACUTE_MEDS"))
                .thenReturn(Optional.of(benefit("ACUTE_MEDS", "2.00", "80.00", false)));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                null, "J01CA04", ATC, new BigDecimal("52.00"), null, null);

        // covered base = 52 - 2 copay = 50; payer 80% = 40; coinsurance 10; patient 2 + 10
        assertEquals("ACUTE_MEDS", e.getBenefitCode());
        assertEquals("J01CA04", e.getMedicationCode());
        assertEquals(ATC, e.getCodingSystem());
        assertEquals(entry.getId(), e.getFormularyRef());
        assertEquals("GENERIC", e.getFormularyTier());
        assertEquals(new BigDecimal("40.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("12.00"), e.getPatientResponsibility());
        assertFalse(e.isRequiresAuthorisation());
        assertTrue(e.getAssumptions().contains("on formulary (tier GENERIC)"));
        assertTrue(e.getAssumptions().contains("Estimate only"));
    }

    // ── PA flag parity ───────────────────────────────────────────────────

    @Test
    void formularyPaFlag_surfacesExactlyLikeBenefitLevelPa() {
        // Benefit itself does NOT require PA — the formulary row does.
        FormularyEntity entry = listing("N02AA01", "ACUTE_MEDS", true, "CONTROLLED", true);
        stubResolution("N02AA01", new FormularyService.FormularyResolution(
                FormularyService.FormularyStatus.ON_FORMULARY, entry));
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "ACUTE_MEDS"))
                .thenReturn(Optional.of(benefit("ACUTE_MEDS", "0.00", "100.00", false)));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                null, "N02AA01", ATC, new BigDecimal("25.00"), null, null);

        assertTrue(e.isRequiresAuthorisation());
        assertTrue(e.getAssumptions().contains("Prior authorisation REQUIRED"));
        assertEquals("ACUTE_MEDS", e.getBenefitCode());
    }

    @Test
    void benefitLevelPa_alsoSurvivesFormularyMapping() {
        FormularyEntity entry = listing("J01DD04", "SPECIALTY_MEDS", true, "SPECIALTY", false);
        stubResolution("J01DD04", new FormularyService.FormularyResolution(
                FormularyService.FormularyStatus.ON_FORMULARY, entry));
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "SPECIALTY_MEDS"))
                .thenReturn(Optional.of(benefit("SPECIALTY_MEDS", "0.00", "90.00", true)));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                null, "J01DD04", ATC, new BigDecimal("100.00"), null, null);

        assertTrue(e.isRequiresAuthorisation());
    }

    // ── honest non-covered postures ──────────────────────────────────────

    @Test
    void notListedMedication_isHonestlyNonCovered_fullChargePatient() {
        stubResolution("Z88XX01", FormularyService.FormularyResolution.notListed());

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                null, "Z88XX01", ATC, new BigDecimal("75.00"), null, null);

        assertEquals(new BigDecimal("0.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("75.00"), e.getPatientResponsibility());
        assertFalse(e.isRequiresAuthorisation());
        assertNull(e.getFormularyRef());
        assertTrue(e.getAssumptions().contains("no active listing"));
        // The medication code is still recorded for audit even when unlisted.
        assertEquals("Z88XX01", e.getMedicationCode());
    }

    @Test
    void excludedMedication_isNonCovered_withExclusionAssumption() {
        FormularyEntity entry = listing("N05BA01", "ACUTE_MEDS", false, "STANDARD", false);
        stubResolution("N05BA01", new FormularyService.FormularyResolution(
                FormularyService.FormularyStatus.EXCLUDED, entry));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                null, "N05BA01", ATC, new BigDecimal("10.00"), null, null);

        assertEquals(new BigDecimal("0.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("10.00"), e.getPatientResponsibility());
        assertTrue(e.getAssumptions().contains("explicitly excluded"));
    }

    @Test
    void formularyMappingToUndefinedBenefit_isNonCovered_neverSilentlyCovered() {
        FormularyEntity entry = listing("A10BA02", "GHOST_BENEFIT", true, "GENERIC", false);
        stubResolution("A10BA02", new FormularyService.FormularyResolution(
                FormularyService.FormularyStatus.ON_FORMULARY, entry));
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "GHOST_BENEFIT"))
                .thenReturn(Optional.empty());

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                null, "A10BA02", ATC, new BigDecimal("30.00"), null, null);

        assertEquals(new BigDecimal("0.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("30.00"), e.getPatientResponsibility());
        assertTrue(e.getAssumptions().contains("does not define"));
    }

    // ── plumbing invariants ──────────────────────────────────────────────

    @Test
    void nonMedicationCall_neverTouchesTheFormulary() {
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "GP_CONSULT"))
                .thenReturn(Optional.of(benefit("GP_CONSULT", "5.00", "80.00", false)));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                "GP_CONSULT", new BigDecimal("25.00"), null, null);

        assertEquals("GP_CONSULT", e.getBenefitCode());
        assertNull(e.getMedicationCode());
        assertNull(e.getFormularyRef());
        org.mockito.Mockito.verifyNoInteractions(formularyService);
    }
}
