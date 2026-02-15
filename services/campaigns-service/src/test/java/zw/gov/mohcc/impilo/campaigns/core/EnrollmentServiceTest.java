package zw.gov.mohcc.impilo.campaigns.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EnrollmentEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EnrollmentRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private CampaignRepository campaignRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private EnrollmentService enrollmentService;

    @BeforeEach
    void setUp() {
        enrollmentService = new EnrollmentService(enrollmentRepository, campaignRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Enroll Participant")
    class Enroll {

        @Test
        void enrollsParticipantInCampaign() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity campaign = new CampaignEntity();
            campaign.setId(10L);
            campaign.setTenantId(tenantId);
            campaign.setName("Flu Vaccine");

            when(campaignRepository.findByIdAndTenantId(10L, tenantId))
                    .thenReturn(Optional.of(campaign));

            EnrollmentEntity saved = new EnrollmentEntity();
            saved.setId(100L);
            saved.setTenantId(tenantId);
            saved.setCampaignId(10L);
            saved.setParticipantId("patient-abc");
            saved.setParticipantType("PATIENT");
            saved.setStatus(EnrollmentStatus.ENROLLED);
            saved.setEnrolledBy("nurse-1");

            when(enrollmentRepository.save(any())).thenReturn(saved);

            EnrollmentEntity result = enrollmentService.enroll(
                    10L, tenantId, "nurse-1", UUID.randomUUID(),
                    "patient-abc", "PATIENT");

            assertThat(result.getParticipantId()).isEqualTo("patient-abc");
            assertThat(result.getStatus()).isEqualTo(EnrollmentStatus.ENROLLED);
        }

        @Test
        void publishesEnrollmentEvent() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity campaign = new CampaignEntity();
            campaign.setId(20L);
            campaign.setTenantId(tenantId);
            campaign.setName("Test Camp");

            when(campaignRepository.findByIdAndTenantId(20L, tenantId))
                    .thenReturn(Optional.of(campaign));

            EnrollmentEntity saved = new EnrollmentEntity();
            saved.setId(200L);
            saved.setCampaignId(20L);
            saved.setParticipantId("p-1");
            saved.setParticipantType("PATIENT");
            saved.setStatus(EnrollmentStatus.ENROLLED);
            when(enrollmentRepository.save(any())).thenReturn(saved);

            enrollmentService.enroll(20L, tenantId, "nurse", UUID.randomUUID(),
                    "p-1", null);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("ENROLLMENT_CREATED");
        }

        @Test
        void throwsWhenCampaignNotFound() {
            UUID tenantId = UUID.randomUUID();
            when(campaignRepository.findByIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> enrollmentService.enroll(
                    999L, tenantId, "nurse", UUID.randomUUID(), "p-1", null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }
}
