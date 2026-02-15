package zw.gov.mohcc.impilo.campaigns.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@ExtendWith(MockitoExtension.class)
class CampaignServiceTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private CampaignService campaignService;

    @BeforeEach
    void setUp() {
        campaignService = new CampaignService(campaignRepository, outboxRepository);
    }

    @Nested
    @DisplayName("Create Campaign")
    class CreateCampaign {

        @Test
        void createsWithCorrectFields() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity saved = new CampaignEntity();
            saved.setId(1L);
            saved.setTenantId(tenantId);
            saved.setName("Measles Vaccination");
            saved.setCampaignType("VACCINATION");
            saved.setChannel("SMS");
            saved.setStatus(CampaignStatus.DRAFT);
            saved.setCreatedBy("admin");

            when(campaignRepository.save(any())).thenReturn(saved);

            CampaignEntity result = campaignService.createCampaign(
                    tenantId, "admin", UUID.randomUUID(),
                    "Measles Vaccination", "Annual measles campaign",
                    "VACCINATION", "{\"ageGroup\":\"0-5\"}", "{\"body\":\"Visit clinic\"}", "SMS");

            assertThat(result.getName()).isEqualTo("Measles Vaccination");
            assertThat(result.getStatus()).isEqualTo(CampaignStatus.DRAFT);
        }

        @Test
        void publishesOutboxEvent() {
            UUID tenantId = UUID.randomUUID();
            CampaignEntity saved = new CampaignEntity();
            saved.setId(2L);
            saved.setTenantId(tenantId);
            saved.setName("Test");
            saved.setCampaignType("OUTREACH");
            saved.setChannel("SMS");
            saved.setStatus(CampaignStatus.DRAFT);
            saved.setCreatedBy("admin");

            when(campaignRepository.save(any())).thenReturn(saved);

            campaignService.createCampaign(tenantId, "admin", UUID.randomUUID(),
                    "Test", null, null, null, null, null);

            ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
            verify(outboxRepository).save(captor.capture());
            assertThat(captor.getValue().getEventType()).isEqualTo("CAMPAIGN_CREATED");
        }

        @Test
        void defaultsCampaignTypeToOutreach() {
            UUID tenantId = UUID.randomUUID();
            ArgumentCaptor<CampaignEntity> captor = ArgumentCaptor.forClass(CampaignEntity.class);

            CampaignEntity saved = new CampaignEntity();
            saved.setId(3L);
            saved.setTenantId(tenantId);
            saved.setName("Default");
            saved.setCampaignType("OUTREACH");
            saved.setChannel("SMS");
            saved.setStatus(CampaignStatus.DRAFT);
            saved.setCreatedBy("admin");

            when(campaignRepository.save(captor.capture())).thenReturn(saved);

            campaignService.createCampaign(tenantId, "admin", UUID.randomUUID(),
                    "Default", null, null, null, null, null);

            assertThat(captor.getValue().getCampaignType()).isEqualTo("OUTREACH");
            assertThat(captor.getValue().getChannel()).isEqualTo("SMS");
        }
    }
}
