package zw.gov.mohcc.impilo.campaigns.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
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
import zw.gov.mohcc.impilo.campaigns.persistence.entity.DeliveryEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EnrollmentEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.DeliveryRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EnrollmentRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class DispatchServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private DeliveryRepository deliveryRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private DispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchService(campaignRepository, enrollmentRepository,
                deliveryRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Dispatch Campaign")
    class Dispatch {

        @Test
        void createsDeliveryForEachEnrollment() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity campaign = new CampaignEntity();
            campaign.setId(10L);
            campaign.setTenantId(tenantId);
            campaign.setName("Test");
            campaign.setChannel("SMS");

            EnrollmentEntity e1 = new EnrollmentEntity();
            e1.setId(1L);
            EnrollmentEntity e2 = new EnrollmentEntity();
            e2.setId(2L);

            when(campaignRepository.findByIdAndTenantId(10L, tenantId))
                    .thenReturn(Optional.of(campaign));
            when(enrollmentRepository.findByCampaignIdAndTenantId(10L, tenantId))
                    .thenReturn(List.of(e1, e2));

            DispatchService.DispatchResult result = dispatchService.dispatch(
                    10L, tenantId, "admin", UUID.randomUUID());

            assertThat(result.deliveriesCreated()).isEqualTo(2);
            verify(deliveryRepository, times(2)).save(any(DeliveryEntity.class));
        }

        @Test
        void returnsZeroDeliveriesWhenNoEnrollments() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity campaign = new CampaignEntity();
            campaign.setId(20L);
            campaign.setTenantId(tenantId);
            campaign.setName("Empty");
            campaign.setChannel("SMS");

            when(campaignRepository.findByIdAndTenantId(20L, tenantId))
                    .thenReturn(Optional.of(campaign));
            when(enrollmentRepository.findByCampaignIdAndTenantId(20L, tenantId))
                    .thenReturn(Collections.emptyList());

            DispatchService.DispatchResult result = dispatchService.dispatch(
                    20L, tenantId, "admin", UUID.randomUUID());

            assertThat(result.deliveriesCreated()).isZero();
        }

        @Test
        void publishesDispatchEvent() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity campaign = new CampaignEntity();
            campaign.setId(30L);
            campaign.setTenantId(tenantId);
            campaign.setName("Dispatch Test");
            campaign.setChannel("WHATSAPP");

            when(campaignRepository.findByIdAndTenantId(30L, tenantId))
                    .thenReturn(Optional.of(campaign));
            when(enrollmentRepository.findByCampaignIdAndTenantId(30L, tenantId))
                    .thenReturn(Collections.emptyList());

            dispatchService.dispatch(30L, tenantId, "admin", UUID.randomUUID());

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("CAMPAIGN_DISPATCHED");
        }

        @Test
        void throwsWhenCampaignNotFound() {
            UUID tenantId = UUID.randomUUID();
            when(campaignRepository.findByIdAndTenantId(999L, tenantId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> dispatchService.dispatch(
                    999L, tenantId, "admin", UUID.randomUUID()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }
}
