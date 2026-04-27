package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Called from Kafka (Mvumo event) or from workers when preference drift would violate policy.
 */
@Service
public class NotificationQueuePreferenceService {

    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public NotificationQueuePreferenceService(
            NotificationRepository notificationRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public int cancelPendingForPatient(String tenantId, String patientRef, String eventType) {
        List<NotificationEntity> list =
                notificationRepository.findByTenantIdAndPatientRefAndStatus(
                        tenantId, patientRef, NotificationStatus.PENDING);
        for (NotificationEntity e : list) {
            e.setStatus(NotificationStatus.CANCELLED);
            e.setLastError("Queue withdrawn after: " + eventType);
            notificationRepository.save(e);
        }
        if (!list.isEmpty()) {
            emitBatchOutbox(tenantId, list.get(0).getPodId(), patientRef, list.size(), eventType);
        }
        return list.size();
    }

    private void emitBatchOutbox(String tenantId, String podId, String patientRef, int count, String causeEvent) {
        OutboxEventEntity out = new OutboxEventEntity();
        out.setTenantId(tenantId);
        out.setPodId(podId);
        out.setCorrelationId(UUID.randomUUID().toString());
        out.setEventType("impilo.notify.queue.cancelled_by_preference.v1");
        out.setSchemaVersion(1);
        out.setOccurredAt(OffsetDateTime.now());
        Map<String, Object> p = new HashMap<>();
        p.put("patientRef", patientRef);
        p.put("cancelledCount", count);
        p.put("causeEventType", causeEvent);
        try {
            out.setPayloadJson(objectMapper.writeValueAsString(p));
        } catch (JsonProcessingException e) {
            return;
        }
        outboxEventRepository.save(out);
    }
}
