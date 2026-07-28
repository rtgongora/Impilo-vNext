package zw.gov.mohcc.impilo.support.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.support.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.support.domain.SupportElevatedAccessGrantEntity;
import zw.gov.mohcc.impilo.support.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.support.repository.SupportElevatedAccessGrantRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Auto-expires elevated support-access grants whose expiry has passed
 * (A3: "maximum elevation duration" is enforced, not advisory — an ACTIVE
 * grant never outlives its expires_at). ACTIVE + expires_at &lt; now → EXPIRED,
 * with an outbox event so the policy plane and the ticket timeline observe
 * the loss of authority. Mirrors the tuso FacilityAdminAppointmentExpirySweep
 * pattern.
 */
@Component
public class SupportElevationExpirySweep {

    public static final String EVENT_EXPIRED = "impilo.support.elevation.expired.v1";

    private static final Logger log = LoggerFactory.getLogger(SupportElevationExpirySweep.class);

    private final SupportElevatedAccessGrantRepository grantRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public SupportElevationExpirySweep(SupportElevatedAccessGrantRepository grantRepository,
                                        OutboxEventRepository outboxRepository,
                                        ObjectMapper objectMapper) {
        this.grantRepository = grantRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${support.elevation-sweep.interval-ms:60000}")
    @Transactional
    public void sweep() {
        int expired = expireLapsed(OffsetDateTime.now());
        if (expired > 0) {
            log.info("Support elevation sweep: expired {} grant(s)", expired);
        }
    }

    int expireLapsed(OffsetDateTime now) {
        List<SupportElevatedAccessGrantEntity> lapsed = grantRepository.findByStatusAndExpiresAtBefore("ACTIVE", now);
        for (SupportElevatedAccessGrantEntity grant : lapsed) {
            grant.setStatus("EXPIRED");
            grantRepository.save(grant);

            OutboxEventEntity outbox = new OutboxEventEntity();
            outbox.setAggregateType("SupportElevatedAccessGrant");
            outbox.setAggregateId(grant.getGrantId().toString());
            outbox.setEventType(EVENT_EXPIRED);
            outbox.setTenantId(grant.getTenantId());
            outbox.setSubjectId(grant.getGrantId().toString());
            outbox.setSubjectType("SupportElevatedAccessGrant");
            outbox.setOccurredAt(now);
            outbox.setPayloadJson(toJson(Map.of(
                    "grantId", grant.getGrantId().toString(),
                    "ticketId", grant.getTicketId().toString(),
                    "supportAssignmentId", grant.getSupportAssignmentId().toString())));
            outbox.setPartitionKey(grant.getGrantId().toString());
            outboxRepository.save(outbox);
        }
        return lapsed.size();
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{}";
        }
    }
}
