package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.DeliveryReceiptRequest;
import zw.gov.mohcc.impilo.notification.api.dto.DeliveryReceiptResponse;
import zw.gov.mohcc.impilo.notification.domain.DeliveryReceiptEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.repository.DeliveryReceiptRepository;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DeliveryReceiptService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryReceiptService.class);

    private final DeliveryReceiptRepository deliveryReceiptRepository;
    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public DeliveryReceiptService(DeliveryReceiptRepository deliveryReceiptRepository,
                                  NotificationRepository notificationRepository,
                                  OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper) {
        this.deliveryReceiptRepository = deliveryReceiptRepository;
        this.notificationRepository = notificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public DeliveryReceiptResponse recordReceipt(DeliveryReceiptRequest request, RequestContext ctx) {
        DeliveryReceiptEntity entity = new DeliveryReceiptEntity();
        entity.setNotificationId(request.notificationId());
        entity.setChannel(request.channel());
        entity.setRecipientAddr(request.recipientAddr());
        entity.setStatus(request.status());
        entity.setProviderRef(request.providerRef());
        entity.setFailureReason(request.failureReason());
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());

        if ("DELIVERED".equals(request.status())) {
            entity.setDeliveredAt(OffsetDateTime.now());
        }

        entity = deliveryReceiptRepository.save(entity);
        log.info("Recorded delivery receipt id={} notificationId={} status={} tenant={}",
                entity.getId(), entity.getNotificationId(), entity.getStatus(), ctx.tenantId());

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType("impilo.notify.receipt.recorded.v1");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("receiptId", entity.getId());
        payload.put("notificationId", entity.getNotificationId());
        payload.put("channel", entity.getChannel());
        payload.put("recipientAddr", entity.getRecipientAddr());
        payload.put("status", entity.getStatus());
        if (entity.getProviderRef() != null) {
            payload.put("providerRef", entity.getProviderRef());
        }
        if (entity.getFailureReason() != null) {
            payload.put("failureReason", entity.getFailureReason());
        }
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);

        reconcileNotificationStatus(entity, ctx);

        return toResponse(entity);
    }

    /**
     * Fold the provider receipt back into the parent notification so status
     * tracking does not stop at SENT. Forward-only: receipts never resurrect
     * PENDING/CANCELLED rows and never downgrade a DELIVERED notification
     * (late or out-of-order failure receipts are recorded but not applied).
     */
    private void reconcileNotificationStatus(DeliveryReceiptEntity receipt, RequestContext ctx) {
        Optional<NotificationEntity> notificationOpt =
                notificationRepository.findById(receipt.getNotificationId());
        if (notificationOpt.isEmpty()) {
            log.warn("Delivery receipt {} references unknown notification {} — receipt stored, no status to reconcile",
                    receipt.getId(), receipt.getNotificationId());
            return;
        }

        NotificationEntity notification = notificationOpt.get();
        if (notification.getStatus() != NotificationStatus.SENT) {
            log.info("Delivery receipt {} for notification {} in status {} — not reconciled (forward-only from SENT)",
                    receipt.getId(), notification.getId(), notification.getStatus());
            return;
        }

        if ("DELIVERED".equals(receipt.getStatus())) {
            notification.setStatus(NotificationStatus.DELIVERED);
            notification.setLastError(null);
            notificationRepository.save(notification);
            emitStatusEvent(notification, "impilo.notify.notification.delivered.v1", receipt, ctx);
        } else if ("FAILED".equals(receipt.getStatus())) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(receipt.getFailureReason() != null
                    ? receipt.getFailureReason()
                    : "Delivery failed (provider receipt)");
            notificationRepository.save(notification);
            emitStatusEvent(notification, "impilo.notify.notification.failed.v1", receipt, ctx);
        }
    }

    private void emitStatusEvent(NotificationEntity notification, String eventType,
                                 DeliveryReceiptEntity receipt, RequestContext ctx) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(notification.getTenantId());
        outbox.setPodId(notification.getPodId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", notification.getId());
        payload.put("channel", notification.getChannel());
        payload.put("to", notification.getToAddr());
        payload.put("status", notification.getStatus().name());
        payload.put("receiptId", receipt.getId());
        if (receipt.getProviderRef() != null) {
            payload.put("providerRef", receipt.getProviderRef());
        }
        if (notification.getLastError() != null) {
            payload.put("lastError", notification.getLastError());
        }
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);
    }

    @Transactional(readOnly = true)
    public List<DeliveryReceiptResponse> getReceipts(String notificationId, String tenantId) {
        return deliveryReceiptRepository.findByNotificationId(notificationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DeliveryReceiptResponse> listReceipts(String tenantId, String status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return deliveryReceiptRepository.findByTenantIdAndStatus(tenantId, status, pageable)
                .map(this::toResponse);
    }

    private DeliveryReceiptResponse toResponse(DeliveryReceiptEntity entity) {
        return new DeliveryReceiptResponse(
                entity.getId(),
                entity.getNotificationId(),
                entity.getChannel(),
                entity.getRecipientAddr(),
                entity.getStatus(),
                entity.getProviderRef(),
                entity.getDeliveredAt(),
                entity.getFailureReason(),
                entity.getCreatedAt()
        );
    }

    private String serializePayload(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload", e);
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }
    }
}
