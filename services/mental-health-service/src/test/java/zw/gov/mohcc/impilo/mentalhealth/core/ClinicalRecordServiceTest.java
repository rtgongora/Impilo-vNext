package zw.gov.mohcc.impilo.mentalhealth.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;

import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.AssessmentEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.entity.ReferralEntity;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.AssessmentRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.FollowupRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.ReferralRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.RiskFormulationRepository;
import zw.gov.mohcc.impilo.mentalhealth.persistence.repository.SafetyPlanRepository;

/** Assessment capacity consistency and risk-formulation vocabulary (W13). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClinicalRecordServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID REFERRAL = UUID.fromString("00000000-0000-4000-8000-000000000005");

    @Mock private ReferralRepository referralRepository;
    @Mock private AssessmentRepository assessmentRepository;
    @Mock private RiskFormulationRepository riskFormulationRepository;
    @Mock private SafetyPlanRepository safetyPlanRepository;
    @Mock private FollowupRepository followupRepository;

    private ClinicalRecordService service;

    @BeforeEach
    void setUp() {
        service = new ClinicalRecordService(referralRepository, assessmentRepository,
                riskFormulationRepository, safetyPlanRepository, followupRepository);
        when(referralRepository.findByIdAndTenantId(REFERRAL, TENANT)).thenReturn(Optional.of(new ReferralEntity()));
        doAnswer(inv -> inv.getArgument(0)).when(assessmentRepository).save(any());
        doAnswer(inv -> inv.getArgument(0)).when(riskFormulationRepository).save(any());
    }

    @Test
    @DisplayName("capacityAssessed=true with no outcome is refused")
    void capacityAssessedWithoutOutcomeRefused() {
        assertThatThrownBy(() -> service.recordAssessment(TENANT, REFERRAL, "INITIAL", "note", true, null, "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("capacityAssessed=false with an outcome is refused — an unassessed capacity claims nothing")
    void capacityUnassessedWithOutcomeRefused() {
        assertThatThrownBy(() -> service.recordAssessment(TENANT, REFERRAL, "INITIAL", "note", false, "HAS_CAPACITY", "dr-x", null))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct assessment succeeds")
    void assessmentSucceeds() {
        AssessmentEntity a = service.recordAssessment(TENANT, REFERRAL, "INITIAL", "note", true, "HAS_CAPACITY", "dr-x", null);
        assertThat(a.getCapacityOutcome()).isEqualTo("HAS_CAPACITY");
    }

    @Test
    @DisplayName("risk formulation with a bad risk level is refused")
    void badRiskLevelRefused() {
        AssessmentEntity a = assessment();
        when(assessmentRepository.findByIdAndTenantId(a.getId(), TENANT)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.recordRiskFormulation(TENANT, a.getId(), "EXTREME", "LOW", "x", "dr-x"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    @DisplayName("a correct risk formulation succeeds")
    void riskFormulationSucceeds() {
        AssessmentEntity a = assessment();
        when(assessmentRepository.findByIdAndTenantId(a.getId(), TENANT)).thenReturn(Optional.of(a));

        var rf = service.recordRiskFormulation(TENANT, a.getId(), "MODERATE", "LOW", "summary", "dr-x");
        assertThat(rf.getRiskToSelf()).isEqualTo("MODERATE");
    }

    private AssessmentEntity assessment() {
        AssessmentEntity a = new AssessmentEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setReferralId(REFERRAL);
        a.setAssessmentType("INITIAL");
        a.setAssessedBy("dr-x");
        a.setAssessedAt(OffsetDateTime.now());
        a.setCreatedAt(OffsetDateTime.now());
        return a;
    }
}
