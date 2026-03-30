package zw.gov.mohcc.impilo.tshepo.authz.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthorityUpdateRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.FederationPodRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.FederationPodResponse;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.FederationPodEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.FederationPodRepository;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class FederationControlService {

    private static final Logger log = LoggerFactory.getLogger(FederationControlService.class);

    public static final String TOPIC_POD_REGISTERED = "trust.federation.pod_registered";

    private final FederationPodRepository federationPodRepository;
    private final EventOutboxRepository outboxRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public FederationControlService(FederationPodRepository federationPodRepository,
                                    EventOutboxRepository outboxRepository,
                                    KafkaTemplate<String, Object> kafkaTemplate,
                                    ObjectMapper objectMapper) {
        this.federationPodRepository = federationPodRepository;
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FederationPodResponse registerPod(FederationPodRequest request) {
        if (federationPodRepository.existsByPodId(request.podId())) {
            throw new IllegalArgumentException("Federation pod already registered: " + request.podId());
        }

        FederationPodEntity entity = new FederationPodEntity();
        entity.setPodId(request.podId());
        entity.setPodName(request.podName());
        entity.setPodType(request.podType());
        entity.setStatus("ACTIVE");
        entity.setAuthority(toJson(request.authority()));
        entity.setObligations(toJson(request.obligations()));
        entity.setRoutingConfig(toJson(request.routingConfig()));

        entity = federationPodRepository.save(entity);

        queuePodRegisteredEvent(entity);

        log.info("Federation pod registered: podId={}, type={}", entity.getPodId(), entity.getPodType());

        publishPodRegisteredEvent(entity);

        return toResponse(entity);
    }

    public List<FederationPodResponse> listPods() {
        return federationPodRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FederationPodResponse updateAuthority(String podId, AuthorityUpdateRequest request) {
        FederationPodEntity entity = federationPodRepository.findByPodId(podId)
                .orElseThrow(() -> new IllegalArgumentException("Federation pod not found: " + podId));

        entity.setAuthority(toJson(request.authority()));
        entity = federationPodRepository.save(entity);

        log.info("Federation pod authority updated: podId={}", podId);

        return toResponse(entity);
    }

    public Map<String, Object> getObligations(String podId) {
        FederationPodEntity entity = federationPodRepository.findByPodId(podId)
                .orElseThrow(() -> new IllegalArgumentException("Federation pod not found: " + podId));

        return fromJson(entity.getObligations());
    }

    private void queuePodRegisteredEvent(FederationPodEntity entity) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "FEDERATION_POD_REGISTERED");
        payload.put("podId", entity.getPodId());
        payload.put("podName", entity.getPodName());
        payload.put("podType", entity.getPodType());
        payload.put("status", entity.getStatus());
        payload.put("registeredAt", entity.getRegisteredAt().toString());
        payload.put("timestamp", Instant.now().toString());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("FederationPod");
        outbox.setAggregateId(entity.getPodId());
        outbox.setEventType("FEDERATION_POD_REGISTERED");
        outbox.setPayload(toJson(payload));

        outboxRepository.save(outbox);
    }

    private void publishPodRegisteredEvent(FederationPodEntity entity) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event_type", "FEDERATION_POD_REGISTERED");
        event.put("event_id", UUID.randomUUID().toString());
        event.put("pod_id", entity.getPodId());
        event.put("pod_name", entity.getPodName());
        event.put("pod_type", entity.getPodType());
        event.put("status", entity.getStatus());
        event.put("registered_at", entity.getRegisteredAt().toString());
        event.put("timestamp", Instant.now().toString());

        try {
            kafkaTemplate.send(TOPIC_POD_REGISTERED, entity.getPodId(), event)
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            log.error("Failed to publish FederationPodRegisteredEvent to {}: {}",
                                    TOPIC_POD_REGISTERED, ex.getMessage(), ex);
                        } else {
                            log.info("Published FederationPodRegisteredEvent to {} [partition={}, offset={}]",
                                    TOPIC_POD_REGISTERED,
                                    result.getRecordMetadata().partition(),
                                    result.getRecordMetadata().offset());
                        }
                    });
        } catch (Exception e) {
            log.error("Failed to send FederationPodRegisteredEvent to Kafka: {}", e.getMessage(), e);
        }
    }

    private FederationPodResponse toResponse(FederationPodEntity entity) {
        return new FederationPodResponse(
                entity.getId(),
                entity.getPodId(),
                entity.getPodName(),
                entity.getPodType(),
                entity.getStatus(),
                fromJson(entity.getAuthority()),
                fromJson(entity.getObligations()),
                fromJson(entity.getRoutingConfig()),
                entity.getRegisteredAt(),
                entity.getUpdatedAt()
        );
    }

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize value to JSON: {}", e.getMessage());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize JSON: {}", e.getMessage());
            return null;
        }
    }
}
