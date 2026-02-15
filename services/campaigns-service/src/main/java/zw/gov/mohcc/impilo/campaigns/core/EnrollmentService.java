package zw.gov.mohcc.impilo.campaigns.core;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.CampaignEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EnrollmentEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.CampaignRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EnrollmentRepository;
import zw.gov.mohcc.impilo.campaigns.persistence.repository.EventOutboxRepository;

@Service
public class EnrollmentService {

    private final EnrollmentRepository enrollmentRepository;
    private final CampaignRepository campaignRepository;
    private final EventOutboxRepository outboxRepository;

    public EnrollmentService(EnrollmentRepository enrollmentRepository,
                             CampaignRepository campaignRepository,
                             EventOutboxRepository outboxRepository) {
        this.enrollmentRepository = enrollmentRepository;
        this.campaignRepository = campaignRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public EnrollmentEntity enroll(Long campaignId, UUID tenantId, String actorId,
                                    UUID correlationId, String participantId,
                                    String participantType) {
        CampaignEntity campaign = campaignRepository.findByIdAndTenantId(campaignId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Campaign not found: " + campaignId));

        EnrollmentEntity enrollment = new EnrollmentEntity();
        enrollment.setTenantId(tenantId);
        enrollment.setCampaignId(campaignId);
        enrollment.setParticipantId(participantId);
        enrollment.setParticipantType(participantType != null ? participantType : "PATIENT");
        enrollment.setEnrolledBy(actorId);
        enrollment = enrollmentRepository.save(enrollment);

        publishEvent("ENROLLMENT", enrollment.getId().toString(), "ENROLLMENT_CREATED",
                buildEnrollmentPayload(enrollment, campaign.getName()),
                tenantId.toString(), correlationId);

        return enrollment;
    }

    @Transactional(readOnly = true)
    public List<EnrollmentEntity> listByCampaign(Long campaignId, UUID tenantId) {
        return enrollmentRepository.findByCampaignIdAndTenantId(campaignId, tenantId);
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

    private String buildEnrollmentPayload(EnrollmentEntity e, String campaignName) {
        return "{\"id\":" + e.getId()
                + ",\"campaignId\":" + e.getCampaignId()
                + ",\"campaignName\":\"" + campaignName + "\""
                + ",\"participantId\":\"" + e.getParticipantId() + "\""
                + ",\"participantType\":\"" + e.getParticipantType() + "\""
                + ",\"status\":\"" + e.getStatus() + "\""
                + "}";
    }
}
