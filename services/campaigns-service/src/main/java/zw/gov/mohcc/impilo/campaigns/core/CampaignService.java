package zw.gov.mohcc.impilo.campaigns.core;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final EventOutboxRepository outboxRepository;

    public CampaignService(CampaignRepository campaignRepository,
                           EventOutboxRepository outboxRepository) {
        this.campaignRepository = campaignRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public CampaignEntity createCampaign(UUID tenantId, String actorId, UUID correlationId,
                                          String name, String description,
                                          String campaignType, String targetGroup,
                                          String messageTemplate, String channel) {
        CampaignEntity campaign = new CampaignEntity();
        campaign.setTenantId(tenantId);
        campaign.setName(name);
        campaign.setDescription(description);
        campaign.setCampaignType(campaignType != null ? campaignType : "OUTREACH");
        campaign.setTargetGroup(targetGroup != null ? targetGroup : "{}");
        campaign.setMessageTemplate(messageTemplate != null ? messageTemplate : "{}");
        campaign.setChannel(channel != null ? channel : "SMS");
        campaign.setCreatedBy(actorId);
        campaign = campaignRepository.save(campaign);

        publishEvent("CAMPAIGN", campaign.getId().toString(), "CAMPAIGN_CREATED",
                buildCampaignPayload(campaign), tenantId.toString(), correlationId);

        return campaign;
    }

    @Transactional(readOnly = true)
    public Page<CampaignEntity> listCampaigns(UUID tenantId, Pageable pageable) {
        return campaignRepository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public CampaignEntity findById(Long id, UUID tenantId) {
        return campaignRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + id));
    }

    @Transactional
    public CampaignEntity completeCampaign(Long id, UUID tenantId, String actorId, UUID correlationId) {
        CampaignEntity campaign = findById(id, tenantId);
        campaign.setStatus(CampaignStatus.COMPLETED);
        campaign = campaignRepository.save(campaign);

        publishEvent("CAMPAIGN", campaign.getId().toString(), "CAMPAIGN_COMPLETED",
                buildCampaignPayload(campaign), tenantId.toString(), correlationId);

        return campaign;
    }

    private void publishEvent(String aggregateType, String aggregateId,
                              String eventType, String payload,
                              String tenantId, UUID correlationId) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setTenantId(tenantId);
        event.setCorrelationId(correlationId != null ? correlationId.toString() : null);
        outboxRepository.save(event);
    }

    private String buildCampaignPayload(CampaignEntity c) {
        return "{\"id\":" + c.getId()
                + ",\"tenantId\":\"" + c.getTenantId() + "\""
                + ",\"name\":\"" + c.getName() + "\""
                + ",\"campaignType\":\"" + c.getCampaignType() + "\""
                + ",\"channel\":\"" + c.getChannel() + "\""
                + ",\"status\":\"" + c.getStatus() + "\""
                + "}";
    }
}
