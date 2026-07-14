package zw.gov.mohcc.impilo.coverage.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.Mock;
import zw.gov.mohcc.impilo.coverage.api.dto.PatientBillingCategoryResponse;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.domain.MemberCoverageEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;
import zw.gov.mohcc.impilo.coverage.repository.MemberCoverageRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Billing-category resolution against the UNIFIED ledgered subsidy model
 * (V013): active category-bearing enrolments win, value-only enrolments are
 * transparent, expired rows are ignored, and precedence falls through to
 * coverage plan then self-pay — behavioural parity with the pre-unification
 * cv_subsidy_enrollments resolution.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PatientBillingCategoryResolutionTest {

    @Mock CoveragePlanRepository planRepository;
    @Mock MemberCoverageRepository memberCoverageRepository;
    @Mock SubsidyEnrolmentRepository subsidyEnrolmentRepository;
    @Mock CoverageEventService eventService;

    private static final String TENANT = "11111111-1111-1111-1111-111111111111";
    private static final UUID TID = UUID.fromString(TENANT);
    private static final String CPID = "CPID-1";

    private CoveragePlanController controller() {
        return new CoveragePlanController(planRepository, memberCoverageRepository,
                subsidyEnrolmentRepository, eventService);
    }

    private SubsidyEnrolmentEntity enrolment(String exemptionCategory, LocalDate from) {
        SubsidyEnrolmentEntity e = new SubsidyEnrolmentEntity();
        e.setId(UUID.randomUUID());
        e.setTenantId(TID);
        e.setSubsidyProgramId(UUID.randomUUID());
        e.setMemberCpid(CPID);
        e.setStatus("ACTIVE");
        e.setExemptionCategory(exemptionCategory);
        e.setEffectiveFrom(from);
        return e;
    }

    @Test
    void activeSubsidyEnrolmentTakesPrecedence() {
        when(subsidyEnrolmentRepository.findByTenantIdAndMemberCpidAndStatus(
                eq(TID), eq(CPID), eq("ACTIVE")))
                .thenReturn(List.of(enrolment("INDIGENT", LocalDate.now().minusDays(1))));

        PatientBillingCategoryResponse body = controller().resolvePatientCategory(TENANT, CPID).getBody();

        assertEquals("INDIGENT", body.category());
        assertEquals("SUBSIDY_ENROLLMENT", body.source());
        // coverage plan is not consulted once an exemption enrolment is found
    }

    @Test
    void healthWorkerEnrolmentResolvesLikeAnyCategory_parityWithLegacyPath() {
        // Parity: a HEALTH_WORKER opt-in written through the unified model
        // resolves exactly like a legacy category enrolment did.
        when(subsidyEnrolmentRepository.findByTenantIdAndMemberCpidAndStatus(
                eq(TID), eq(CPID), eq("ACTIVE")))
                .thenReturn(List.of(enrolment("HEALTH_WORKER", LocalDate.now())));

        PatientBillingCategoryResponse body = controller().resolvePatientCategory(TENANT, CPID).getBody();

        assertEquals("HEALTH_WORKER", body.category());
        assertEquals("SUBSIDY_ENROLLMENT", body.source());
    }

    @Test
    void valueOnlyLedgerEnrolmentIsTransparentToBillingCategory() {
        // A cap/value enrolment with NO exemption_category must not hijack the
        // billing category (that would have been a behaviour change vs legacy).
        when(subsidyEnrolmentRepository.findByTenantIdAndMemberCpidAndStatus(
                eq(TID), eq(CPID), eq("ACTIVE")))
                .thenReturn(List.of(enrolment(null, LocalDate.now().minusDays(3))));
        when(memberCoverageRepository.findByTenantIdAndClientIdAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        PatientBillingCategoryResponse body = controller().resolvePatientCategory(TENANT, CPID).getBody();

        assertEquals("CASH", body.category());
        assertEquals("DEFAULT_SELF_PAY", body.source());
    }

    @Test
    void fallsBackToCoveragePlanTypeWhenNoEnrolment() {
        when(subsidyEnrolmentRepository.findByTenantIdAndMemberCpidAndStatus(
                any(), any(), any())).thenReturn(List.of());
        UUID planId = UUID.randomUUID();
        MemberCoverageEntity coverage = new MemberCoverageEntity(
                TID, "national-spine", CPID, planId, "M-1", "SELF", LocalDate.now());
        CoveragePlanEntity plan = new CoveragePlanEntity(
                TID, "national-spine", "PLN-PRIV", "Private Plan", "PAYER-X", "private", LocalDate.now());
        when(memberCoverageRepository.findByTenantIdAndClientIdAndStatus(eq(TID), eq(CPID), eq("ACTIVE")))
                .thenReturn(List.of(coverage));
        when(planRepository.findById(planId)).thenReturn(Optional.of(plan));

        PatientBillingCategoryResponse body = controller().resolvePatientCategory(TENANT, CPID).getBody();

        assertEquals("PRIVATE", body.category());
        assertEquals("COVERAGE_PLAN", body.source());
        assertEquals("PLN-PRIV", body.planCode());
    }

    @Test
    void defaultsToCashWhenNoCoverage() {
        when(subsidyEnrolmentRepository.findByTenantIdAndMemberCpidAndStatus(
                any(), any(), any())).thenReturn(List.of());
        when(memberCoverageRepository.findByTenantIdAndClientIdAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        PatientBillingCategoryResponse body = controller().resolvePatientCategory(TENANT, CPID).getBody();

        assertEquals("CASH", body.category());
        assertEquals("DEFAULT_SELF_PAY", body.source());
    }

    @Test
    void expiredEnrolmentIsIgnored() {
        SubsidyEnrolmentEntity expired = enrolment("ELDERLY", LocalDate.now().minusDays(10));
        expired.setEffectiveTo(LocalDate.now().minusDays(1)); // ended yesterday
        when(subsidyEnrolmentRepository.findByTenantIdAndMemberCpidAndStatus(
                any(), any(), any())).thenReturn(List.of(expired));
        when(memberCoverageRepository.findByTenantIdAndClientIdAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        PatientBillingCategoryResponse body = controller().resolvePatientCategory(TENANT, CPID).getBody();

        // expired enrolment does not apply; falls through to self-pay
        assertEquals("CASH", body.category());
        assertEquals("DEFAULT_SELF_PAY", body.source());
    }
}
