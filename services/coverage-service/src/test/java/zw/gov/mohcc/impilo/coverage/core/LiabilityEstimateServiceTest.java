package zw.gov.mohcc.impilo.coverage.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.coverage.domain.BenefitDefinitionEntity;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.LiabilityEstimateEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.repository.BenefitDefinitionRepository;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.LiabilityEstimateRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * OF-B9/OF-B8 — the LIVE liability engine: covered/non-covered decomposition
 * math, and the PA-requirement flag recorded on the estimate at pricing time
 * (Vol II §10.4/§10.6 — the checkout caller learns the PA posture from the
 * SAME call that prices the benefit).
 */
@ExtendWith(MockitoExtension.class)
class LiabilityEstimateServiceTest {

    @Mock private MemberCoverageRepository memberRepository;
    @Mock private CoveragePlanRepository planRepository;
    @Mock private BenefitDefinitionRepository benefitRepository;
    @Mock private LiabilityEstimateRepository estimateRepository;
    @Mock private CoverageEventService eventService;

    private LiabilityEstimateService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID coverageId = UUID.randomUUID();
    private final UUID planId = UUID.randomUUID();
    private final UUID planVersionId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new LiabilityEstimateService(memberRepository, planRepository,
                benefitRepository, estimateRepository, eventService);

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
                tenantId, "pod-1", planVersionId, code, "MEDICATION", "test benefit");
        def.setCopayAmount(new BigDecimal(copay));
        def.setCoveragePercent(new BigDecimal(pct));
        def.setCurrency("USD");
        def.setRequiresAuthorisation(requiresPa);
        return def;
    }

    @Test
    void coveredBenefit_decomposesCharge_andRecordsNoPaWhenNotRequired() {
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "BEN-1"))
                .thenReturn(Optional.of(benefit("BEN-1", "10.00", "80.00", false)));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                "BEN-1", new BigDecimal("110.00"), null, null);

        // covered base = 110 - 10 copay = 100; payer 80%; coinsurance 20; patient 10 + 20
        assertEquals(new BigDecimal("80.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("20.00"), e.getCoinsurance());
        assertEquals(new BigDecimal("30.00"), e.getPatientResponsibility());
        assertFalse(e.isRequiresAuthorisation());
        assertTrue(e.getAssumptions().contains("Estimate only"));
    }

    @Test
    void paRequiredBenefit_flagRecordedOnEstimate_andInAssumptions() {
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                tenantId, planVersionId, "BEN-PA"))
                .thenReturn(Optional.of(benefit("BEN-PA", "0.00", "100.00", true)));

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                "BEN-PA", new BigDecimal("50.00"), null, null);

        assertTrue(e.isRequiresAuthorisation());
        assertTrue(e.getAssumptions().contains("Prior authorisation REQUIRED"));
        assertEquals(new BigDecimal("50.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("0.00"), e.getPatientResponsibility());
    }

    @Test
    void unknownBenefit_isNonCovered_fullChargePatient_noPaFlag() {
        when(benefitRepository.findByTenantIdAndPlanVersionIdAndBenefitCode(
                eq(tenantId), eq(planVersionId), eq("ZIBO-UNKNOWN")))
                .thenReturn(Optional.empty());

        LiabilityEstimateEntity e = service.estimate(tenantId, "pod-1", coverageId,
                "ZIBO-UNKNOWN", new BigDecimal("75.00"), null, null);

        assertEquals(new BigDecimal("0.00"), e.getPayerEstimate());
        assertEquals(new BigDecimal("75.00"), e.getPatientResponsibility());
        assertFalse(e.isRequiresAuthorisation());
        assertTrue(e.getAssumptions().contains("not covered"));
    }

    @Test
    void unknownCoverage_throws() {
        UUID missing = UUID.randomUUID();
        assertThrows(IllegalArgumentException.class, () -> service.estimate(
                tenantId, "pod-1", missing, "BEN-1", BigDecimal.TEN, null, null));
    }

    @Test
    void negativeCharge_rejected() {
        assertThrows(IllegalArgumentException.class, () -> service.estimate(
                tenantId, "pod-1", coverageId, "BEN-1", new BigDecimal("-1"), null, null));
    }
}
