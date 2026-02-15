package zw.gov.mohcc.impilo.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.notification.api.dto.WorkerResponse;
import zw.gov.mohcc.impilo.notification.domain.NotificationEntity;
import zw.gov.mohcc.impilo.notification.domain.NotificationStatus;
import zw.gov.mohcc.impilo.notification.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.notification.domain.TemplateEntity;
import zw.gov.mohcc.impilo.notification.provider.NotificationProvider;
import zw.gov.mohcc.impilo.notification.provider.ProviderRegistry;
import zw.gov.mohcc.impilo.notification.repository.NotificationRepository;
import zw.gov.mohcc.impilo.notification.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.notification.repository.TemplateRepository;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WorkerService {

    private static final Logger log = LoggerFactory.getLogger(WorkerService.class);

    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ProviderRegistry providerRegistry;
    private final TemplateService templateService;
    private final ObjectMapper objectMapper;

    public WorkerService(NotificationRepository notificationRepository,
                         TemplateRepository templateRepository,
                         OutboxEventRepository outboxEventRepository,
                         ProviderRegistry providerRegistry,
                         TemplateService templateService,
                         ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.providerRegistry = providerRegistry;
        this.templateService = templateService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WorkerResponse processPending(int limit) {
        List<NotificationEntity> pending = notificationRepository
                .findByStatusOrderByCreatedAtAsc(NotificationStatus.PENDING);

        int toProcess = Math.min(pending.size(), limit);
        int sent = 0;
        int failed = 0;

        for (int i = 0; i < toProcess; i++) {
            NotificationEntity notification = pending.get(i);
            notification.setAttempts(notification.getAttempts() + 1);
            try {
                processOne(notification);
                sent++;
            } catch (Exception e) {
                markFailed(notification, e.getMessage());
                failed++;
            }
        }

        log.info("Worker run complete: processed={} sent={} failed={}", toProcess, sent, failed);
        return new WorkerResponse(toProcess, sent, failed);
    }

    private void processOne(NotificationEntity notification) {
        String body = null;
        String subject = null;

        if (notification.getTemplateKey() != null) {
            Optional<TemplateEntity> templateOpt = templateRepository.findByKey(notification.getTemplateKey());
            if (templateOpt.isPresent()) {
                TemplateEntity template = templateOpt.get();
                Map<String, String> vars = deserializeVariables(notification.getVarsJson());
                body = templateService.renderBody(template, vars);
                subject = templateService.renderSubject(template, vars);
            } else {
                throw new RuntimeException("Template not found: " + notification.getTemplateKey());
            }
        }

        NotificationProvider provider = providerRegistry.resolve(notification.getChannel());
        provider.send(notification.getChannel(), notification.getToAddr(), subject, body);

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(OffsetDateTime.now());
        notification.setLastError(null);
        notificationRepository.save(notification);

        emitOutboxEvent(notification, "impilo.notify.sent.v1");
    }

    private void markFailed(NotificationEntity notification, String error) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setLastError(error);
        notificationRepository.save(notification);

        emitOutboxEvent(notification, "impilo.notify.failed.v1");
    }

    private void emitOutboxEvent(NotificationEntity notification, String eventType) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(notification.getTenantId());
        outbox.setPodId(notification.getPodId());
        outbox.setCorrelationId(notification.getId());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());

        Map<String, Object> payload = new HashMap<>();
        payload.put("notificationId", notification.getId());
        payload.put("channel", notification.getChannel());
        payload.put("to", notification.getToAddr());
        payload.put("status", notification.getStatus().name());
        payload.put("attempts", notification.getAttempts());
        if (notification.getLastError() != null) {
            payload.put("lastError", notification.getLastError());
        }
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);
    }

    private Map<String, String> deserializeVariables(String varsJson) {
        if (varsJson == null || varsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(varsJson, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize variables: {}", e.getMessage());
            return Map.of();
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
