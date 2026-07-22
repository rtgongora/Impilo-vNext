package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.notification.api.dto.NotificationResponse;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyRequest;
import zw.gov.mohcc.impilo.notification.api.dto.NotifyResponse;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.integration.MvumoPreferenceClient;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final NotificationRepository notificationRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final MvumoPreferenceClient mvumoPreferenceClient;
    private final NotificationRecipientResolver recipientResolver;

    public NotifyService(
            NotificationRepository notificationRepository,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper,
            MvumoPreferenceClient mvumoPreferenceClient,
            NotificationRecipientResolver recipientResolver) {
        this.notificationRepository = notificationRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.mvumoPreferenceClient = mvumoPreferenceClient;
        this.recipientResolver = recipientResolver;
    }

    @Transactional
    public NotifyResponse enqueue(NotifyRequest request, RequestContext ctx) {
        List<String> inboxRecipients = resolveInboxRecipients(request);
        if (inboxRecipients.isEmpty()) {
            inboxRecipients = List.of(request.recipient());
        }

        NotifyResponse last = null;
        for (String inboxRecipient : inboxRecipients) {
            last = enqueueOne(request, ctx, inboxRecipient);
        }
        return last != null ? last : enqueueOne(request, ctx, request.recipient());
    }

    private NotifyResponse enqueueOne(NotifyRequest request, RequestContext ctx, String inboxRecipient) {
        NotificationEntity entity = new NotificationEntity();
        entity.setTemplateKey(request.templateKey());
        entity.setChannel(request.channel());
        entity.setToAddr(request.recipient());
        entity.setInboxRecipient(inboxRecipient);
        entity.setVarsJson(serializeVariables(request.variables()));
        entity.setTenantId(ctx.tenantId());
        entity.setPodId(ctx.podId());
        entity.setPatientRef(request.patientRef());
        entity.setNotBefore(request.notBefore());

        java.util.Optional<Boolean> allow =
                mvumoPreferenceClient.evaluateAllowed(
                        ctx.tenantId(), request.patientRef(), request.channel(), request.messageKind());
        if (allow.isPresent() && !allow.get()) {
            entity.setStatus(NotificationStatus.CANCELLED);
            entity.setLastError("Blocked by Mvumo communication preferences");
        } else {
            entity.setStatus(NotificationStatus.PENDING);
        }

        entity = notificationRepository.save(entity);
        log.info(
                "Enqueued notification id={} channel={} to={} inbox={} tenant={} status={}",
                entity.getId(), entity.getChannel(), entity.getToAddr(), entity.getInboxRecipient(),
                ctx.tenantId(), entity.getStatus());

        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        if (entity.getStatus() == NotificationStatus.CANCELLED) {
            outbox.setEventType("impilo.notify.notification.preference_blocked.v1");
        } else {
            outbox.setEventType("impilo.notify.notification.enqueued.v1");
        }
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", entity.getId());
        payload.put("channel", entity.getChannel());
        payload.put("to", entity.getToAddr());
        payload.put("inboxRecipient", entity.getInboxRecipient());
        payload.put("templateKey", entity.getTemplateKey());
        payload.put("status", entity.getStatus().name());
        if (entity.getPatientRef() != null) {
            payload.put("patientRef", entity.getPatientRef());
        }
        if (entity.getStatus() == NotificationStatus.CANCELLED) {
            payload.put("reason", "preference_gate");
        }
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);

        String detail = entity.getStatus() == NotificationStatus.CANCELLED ? entity.getLastError() : null;
        return new NotifyResponse(entity.getId(), entity.getStatus().name(), entity.getChannel(), entity.getToAddr(), detail);
    }

    private List<String> resolveInboxRecipients(NotifyRequest request) {
        if (request.inboxRecipient() != null && !request.inboxRecipient().isBlank()) {
            return List.of(request.inboxRecipient().trim());
        }
        return recipientResolver.resolveInboxRecipients(request.recipient());
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> listNotifications(String tenantId, String recipientId, Pageable pageable) {
        if (recipientId == null || recipientId.isBlank()) {
            return notificationRepository.findByTenantId(tenantId, pageable)
                    .map(this::toResponse);
        }
        List<String> aliases = inboxAliases(recipientId);
        if (aliases.size() == 1) {
            return notificationRepository.findByTenantIdAndInboxRecipient(tenantId, aliases.get(0), pageable)
                    .map(this::toResponse);
        }
        return notificationRepository.findByTenantIdAndInboxRecipientIn(tenantId, aliases, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public Optional<NotificationResponse> markAsRead(String tenantId, String podId, String correlationId, String notificationId) {
        Optional<NotificationEntity> maybeEntity = notificationRepository.findByIdAndTenantId(notificationId, tenantId);
        if (maybeEntity.isEmpty()) {
            return Optional.empty();
        }
        NotificationEntity entity = maybeEntity.get();
        if (entity.getReadAt() == null) {
            entity.setReadAt(OffsetDateTime.now());
            notificationRepository.save(entity);
            emitReadOutboxEvent(entity, tenantId, podId, correlationId);
        }
        return Optional.of(toResponse(entity));
    }

    @Transactional
    public int markAllAsRead(String tenantId, String recipientId) {
        int updated = 0;
        Page<NotificationEntity> page = recipientId == null || recipientId.isBlank()
                ? notificationRepository.findByTenantId(tenantId, Pageable.unpaged())
                : notificationRepository.findByTenantIdAndInboxRecipientIn(
                        tenantId, inboxAliases(recipientId), Pageable.unpaged());
        for (NotificationEntity entity : page.getContent()) {
            if (entity.getReadAt() == null) {
                entity.setReadAt(OffsetDateTime.now());
                updated++;
            }
        }
        if (updated > 0) {
            notificationRepository.saveAll(page.getContent());
        }
        return updated;
    }

    @Transactional(readOnly = true)
    public long unreadCount(String tenantId, String recipientId) {
        if (recipientId == null || recipientId.isBlank()) {
            return notificationRepository.countByTenantIdAndReadAtIsNull(tenantId);
        }
        List<String> aliases = inboxAliases(recipientId);
        if (aliases.size() == 1) {
            return notificationRepository.countByTenantIdAndInboxRecipientAndReadAtIsNull(tenantId, aliases.get(0));
        }
        return notificationRepository.countByTenantIdAndInboxRecipientInAndReadAtIsNull(tenantId, aliases);
    }

    private List<String> inboxAliases(String recipientId) {
        List<String> resolved = new ArrayList<>(recipientResolver.resolveInboxRecipients(recipientId));
        if (!resolved.contains(recipientId)) {
            resolved.add(recipientId);
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public Optional<Map<String, Object>> getPreferences(String tenantId, String patientRef) {
        if (patientRef == null || patientRef.isBlank()) {
            return Optional.empty();
        }
        return mvumoPreferenceClient.getCommunicationPreferences(tenantId, patientRef)
                .map(node -> objectMapper.convertValue(node, Map.class));
    }

    @Transactional
    public Optional<Map<String, Object>> updatePreferences(String tenantId, String patientRef, Map<String, Object> body) {
        if (patientRef == null || patientRef.isBlank()) {
            return Optional.empty();
        }
        return mvumoPreferenceClient.putCommunicationPreferences(tenantId, patientRef, body)
                .map(node -> objectMapper.convertValue(node, Map.class));
    }

    private NotificationResponse toResponse(NotificationEntity entity) {
        return new NotificationResponse(
                entity.getId(),
                entity.getTemplateKey(),
                entity.getChannel(),
                entity.getToAddr(),
                entity.getStatus().name(),
                entity.getAttempts(),
                entity.getLastError(),
                entity.getCreatedAt(),
                entity.getSentAt(),
                entity.getReadAt()
        );
    }

    private void emitReadOutboxEvent(NotificationEntity entity, String tenantId, String podId, String correlationId) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(tenantId);
        outbox.setPodId(podId);
        outbox.setCorrelationId(correlationId != null ? correlationId : "unknown");
        outbox.setEventType("impilo.notify.notification.read.v1");
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(serializePayload(Map.of(
                "notificationId", entity.getId(),
                "channel", entity.getChannel(),
                "status", entity.getStatus().name()
        )));
        outboxEventRepository.save(outbox);
    }

    private String serializeVariables(Map<String, String> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification variables", e);
            throw new RuntimeException("Failed to serialize notification variables", e);
        }
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
