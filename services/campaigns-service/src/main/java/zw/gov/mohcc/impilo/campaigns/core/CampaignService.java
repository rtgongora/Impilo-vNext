package zw.gov.mohcc.impilo.campaigns.core;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.campaigns.integration.LiveSessionIntegration;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final EventOutboxRepository outboxRepository;
    private final LiveSessionIntegration liveSessionIntegration;

    public CampaignService(CampaignRepository campaignRepository,
                           EventOutboxRepository outboxRepository,
                           LiveSessionIntegration liveSessionIntegration) {
        this.campaignRepository = campaignRepository;
        this.outboxRepository = outboxRepository;
        this.liveSessionIntegration = liveSessionIntegration;
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

    /**
     * Schedule a governed Impilo Live public broadcast for this campaign.
     * Public Health owns the campaign; Impilo Live owns broadcast infrastructure.
     */
    @Transactional
    public Map<String, Object> scheduleLiveBroadcast(Long id, UUID tenantId, String actorId,
                                                     UUID correlationId, Map<String, Object> request) {
        CampaignEntity campaign = findById(id, tenantId);
        Map<String, Object> livePayload = liveSessionIntegration.buildPublicBroadcastPayload(
                campaign.getId().toString(),
                stringOrDefault(request, "title", campaign.getName()),
                stringOrDefault(request, "description", campaign.getDescription()),
                request.get("startTime"),
                request.get("endTime"),
                stringList(request.get("officialSpeakerIds")),
                stringList(request.get("moderatorIds")),
                stringOrDefault(request, "audienceScope", "PUBLIC"),
                boolOrDefault(request.get("emergencyBriefing"), false),
                boolOrDefault(request.get("replayAllowed"), true));

        return liveSessionIntegration.schedulePublicBroadcast(livePayload)
                .map(liveEvent -> {
                    String liveEventId = String.valueOf(liveEvent.get("id"));
                    campaign.setLiveEventId(UUID.fromString(liveEventId));
                    campaign.setChannel("LIVE_BROADCAST");
                    CampaignEntity saved = campaignRepository.save(campaign);
                    publishEvent("CAMPAIGN", saved.getId().toString(), "CAMPAIGN_LIVE_BROADCAST_SCHEDULED",
                            buildCampaignPayload(saved), tenantId.toString(), correlationId);
                    return Map.<String, Object>of(
                            "campaignId", saved.getId(),
                            "liveEventId", liveEventId,
                            "impiloLiveJoinPath", "/live/event/" + liveEventId,
                            "impiloLiveJoinPathPublic", "/live/discover",
                            "status", liveEvent.get("status"));
                })
                .orElseThrow(() -> new IllegalStateException(
                        "Impilo Live broadcast scheduling unavailable for campaign " + id));
    }

    private static String stringOrDefault(Map<String, Object> map, String key, String fallback) {
        Object v = map.get(key);
        return v == null || v.toString().isBlank() ? fallback : v.toString();
    }

    private static boolean boolOrDefault(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(value.toString());
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of();
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
