package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.RiskAssessmentEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.RiskAssessmentRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiskAssessmentServiceTest {

    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private RiskAssessmentService riskAssessmentService;

    @BeforeEach
    void setUp() {
        riskAssessmentService = new RiskAssessmentService(riskAssessmentRepository, outboxRepository);
        // Echo the persisted entity back (assigning an id, as a real DB would).
        when(riskAssessmentRepository.save(any(RiskAssessmentEntity.class))).thenAnswer(inv -> {
            RiskAssessmentEntity e = inv.getArgument(0);
            if (e.getId() == null) e.setId(1L);
            return e;
        });
    }

    private RiskAssessmentEntity captureSaved() {
        ArgumentCaptor<RiskAssessmentEntity> captor = ArgumentCaptor.forClass(RiskAssessmentEntity.class);
        verify(riskAssessmentRepository).save(captor.capture());
        return captor.getValue();
    }

    @Nested
    @DisplayName("Assess Risk — verdict is computed server-side")
    class AssessRisk {

        @Test
        void noFactors_computesLowAllow() {
            riskAssessmentService.assess(UUID.randomUUID(), "user-1", UUID.randomUUID(),
                    "LOGIN", "session-123", List.of());
            RiskAssessmentEntity saved = captureSaved();
            assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(saved.getRecommendation()).isEqualTo(Recommendation.ALLOW);
            assertThat(saved.getRiskScore().intValue()).isZero();
        }

        @Test
        void compromisedCredential_computesHighReview() {
            // A single high-weight signal (60) → HIGH → REVIEW. The caller cannot declare ALLOW.
            riskAssessmentService.assess(UUID.randomUUID(), "user-1", UUID.randomUUID(),
                    "LOGIN", null, List.of("KNOWN_COMPROMISED_CREDENTIAL"));
            RiskAssessmentEntity saved = captureSaved();
            assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.HIGH);
            assertThat(saved.getRecommendation()).isEqualTo(Recommendation.REVIEW);
        }

        @Test
        void stackedFactors_computesCriticalDeny() {
            // 60 + 40 = 100 → CRITICAL → DENY.
            riskAssessmentService.assess(UUID.randomUUID(), "user-1", UUID.randomUUID(),
                    "DATA_EXPORT", null, List.of("KNOWN_COMPROMISED_CREDENTIAL", "IMPOSSIBLE_TRAVEL"));
            RiskAssessmentEntity saved = captureSaved();
            assertThat(saved.getRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
            assertThat(saved.getRecommendation()).isEqualTo(Recommendation.DENY);
        }

        @Test
        void publishesOutboxEventOnAssess() {
            riskAssessmentService.assess(UUID.randomUUID(), "admin", UUID.randomUUID(),
                    "DATA_EXPORT", null, List.of("NEW_DEVICE"));
            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getAggregateType()).isEqualTo("RISK_ASSESSMENT");
            assertThat(captor.getValue().getEventType()).isEqualTo("RISK_ASSESSED");
        }
    }

    @Nested
    @DisplayName("List Assessments")
    class ListAssessments {

        @Test
        void returnsAllAssessmentsForTenant() {
            UUID tenantId = UUID.randomUUID();
            RiskAssessmentEntity r1 = new RiskAssessmentEntity();
            r1.setId(1L);
            RiskAssessmentEntity r2 = new RiskAssessmentEntity();
            r2.setId(2L);
            when(riskAssessmentRepository.findByTenantId(tenantId)).thenReturn(List.of(r1, r2));

            assertThat(riskAssessmentService.listAssessments(tenantId)).hasSize(2);
        }
    }
}
