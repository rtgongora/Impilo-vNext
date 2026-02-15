package zw.gov.mohcc.impilo.ia.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
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
import zw.gov.mohcc.impilo.ia.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.ia.persistence.entity.RiskAssessmentEntity;
import zw.gov.mohcc.impilo.ia.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.ia.persistence.repository.RiskAssessmentRepository;

@ExtendWith(MockitoExtension.class)
class RiskAssessmentServiceTest {

    @Mock private RiskAssessmentRepository riskAssessmentRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private RiskAssessmentService riskAssessmentService;

    @BeforeEach
    void setUp() {
        riskAssessmentService = new RiskAssessmentService(riskAssessmentRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Assess Risk")
    class AssessRisk {

        @Test
        void performsAndSavesAssessment() {
            UUID tenantId = UUID.randomUUID();
            String actorId = "user-1";
            UUID correlationId = UUID.randomUUID();

            RiskAssessmentEntity saved = new RiskAssessmentEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setActorId(actorId);
            saved.setContextType("LOGIN");
            saved.setRiskScore(new BigDecimal("0.2500"));
            saved.setRiskLevel(RiskLevel.LOW);
            saved.setRecommendation(Recommendation.ALLOW);
            saved.setAssessedBy(actorId);

            when(riskAssessmentRepository.save(any(RiskAssessmentEntity.class))).thenReturn(saved);

            RiskAssessmentEntity result = riskAssessmentService.assess(
                    tenantId, actorId, correlationId,
                    "LOGIN", "session-123",
                    new BigDecimal("0.2500"), RiskLevel.LOW,
                    "[{\"factor\":\"known_device\"}]", Recommendation.ALLOW);

            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getContextType()).isEqualTo("LOGIN");
            assertThat(result.getRiskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(result.getRecommendation()).isEqualTo(Recommendation.ALLOW);
        }

        @Test
        void publishesOutboxEventOnAssess() {
            UUID tenantId = UUID.randomUUID();
            RiskAssessmentEntity saved = new RiskAssessmentEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setActorId("admin");
            saved.setContextType("DATA_EXPORT");
            saved.setRiskScore(new BigDecimal("0.8500"));
            saved.setRiskLevel(RiskLevel.HIGH);
            saved.setRecommendation(Recommendation.STEP_UP);
            saved.setAssessedBy("admin");

            when(riskAssessmentRepository.save(any(RiskAssessmentEntity.class))).thenReturn(saved);

            riskAssessmentService.assess(tenantId, "admin", UUID.randomUUID(),
                    "DATA_EXPORT", null, new BigDecimal("0.8500"),
                    RiskLevel.HIGH, null, Recommendation.STEP_UP);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());

            EventOutboxEntity event = captor.getValue();
            assertThat(event.getAggregateType()).isEqualTo("RISK_ASSESSMENT");
            assertThat(event.getEventType()).isEqualTo("RISK_ASSESSED");
        }

        @Test
        void defaultsRiskLevelToLowWhenNull() {
            UUID tenantId = UUID.randomUUID();
            ArgumentCaptor<RiskAssessmentEntity> captor = ArgumentCaptor.forClass(RiskAssessmentEntity.class);

            RiskAssessmentEntity saved = new RiskAssessmentEntity();
            saved.setId(3L);
            saved.setTenantId(tenantId);
            saved.setActorId("admin");
            saved.setContextType("LOGIN");
            saved.setRiskScore(BigDecimal.ZERO);
            saved.setRiskLevel(RiskLevel.LOW);
            saved.setRecommendation(Recommendation.ALLOW);
            saved.setAssessedBy("admin");

            when(riskAssessmentRepository.save(captor.capture())).thenReturn(saved);

            riskAssessmentService.assess(tenantId, "admin", UUID.randomUUID(),
                    "LOGIN", null, null, null, null, null);

            assertThat(captor.getValue().getRiskLevel()).isEqualTo(RiskLevel.LOW);
            assertThat(captor.getValue().getRecommendation()).isEqualTo(Recommendation.ALLOW);
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

            List<RiskAssessmentEntity> result = riskAssessmentService.listAssessments(tenantId);
            assertThat(result).hasSize(2);
        }
    }
}
