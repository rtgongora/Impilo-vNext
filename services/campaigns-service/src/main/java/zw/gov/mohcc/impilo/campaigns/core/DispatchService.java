package zw.gov.mohcc.impilo.campaigns.core;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.DeliveryEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EnrollmentEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.DeliveryRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EnrollmentRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

/**
 * Stub dispatch service: creates delivery records for each enrollment
 * and marks them as DISPATCHED. Actual channel delivery is not implemented.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final CampaignRepository campaignRepository;
    private final EnrollmentRepository enrollmentRepository;
    private final DeliveryRepository deliveryRepository;
    private final EventOutboxRepository outboxRepository;

    public DispatchService(CampaignRepository campaignRepository,
                           EnrollmentRepository enrollmentRepository,
                           DeliveryRepository deliveryRepository,
                           EventOutboxRepository outboxRepository) {
        this.campaignRepository = campaignRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.deliveryRepository = deliveryRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public DispatchResult dispatch(Long campaignId, UUID tenantId, String actorId,
                                    UUID correlationId) {
        CampaignEntity campaign = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        List<EnrollmentEntity> enrollments =
                enrollmentRepository.findByCampaignIdAndTenantId(campaignId, tenantId);

        int dispatched = 0;
        for (EnrollmentEntity enrollment : enrollments) {
            DeliveryEntity delivery = new DeliveryEntity();
            delivery.setTenantId(tenantId);
            delivery.setCampaignId(campaignId);
            delivery.setEnrollmentId(enrollment.getId());
            delivery.setChannel(campaign.getChannel());
            delivery.setStatus(DeliveryStatus.DISPATCHED);
            delivery.setDispatchedAt(OffsetDateTime.now());
            deliveryRepository.save(delivery);
            dispatched++;
        }

        publishEvent("CAMPAIGN", campaignId.toString(), "CAMPAIGN_DISPATCHED",
                "{\"campaignId\":" + campaignId
                        + ",\"campaignName\":\"" + campaign.getName() + "\""
                        + ",\"deliveriesCreated\":" + dispatched
                        + ",\"channel\":\"" + campaign.getChannel() + "\""
                        + "}",
                tenantId.toString(), correlationId);

        log.info("Dispatched {} deliveries for campaign #{} '{}'",
                dispatched, campaignId, campaign.getName());

        return new DispatchResult(campaignId, dispatched);
    }

    public record DispatchResult(Long campaignId, int deliveriesCreated) {}

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
}
