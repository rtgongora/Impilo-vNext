package zw.gov.mohcc.impilo.forms.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.CreateVersionRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormVersionResponse;
import zw.gov.mohcc.impilo.forms.domain.FormVersionEntity;
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.forms.repository.FormDefinitionRepository;
import zw.gov.mohcc.impilo.forms.repository.FormVersionRepository;
import zw.gov.mohcc.impilo.forms.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

@Service
public class FormVersionService {

    private static final Logger log = LoggerFactory.getLogger(FormVersionService.class);

    private final FormVersionRepository versionRepository;
    private final FormDefinitionRepository formRepository;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FormVersionService(FormVersionRepository versionRepository,
                              FormDefinitionRepository formRepository,
                              OutboxEventRepository outboxRepository,
                              ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.formRepository = formRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormVersionResponse create(String formId, CreateVersionRequest request, RequestContext ctx) {
        formRepository.findById(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));

        validateContentJson(request.contentJson());

        int nextVersion = versionRepository.findTopByFormIdOrderByVersionNumberDesc(formId)
                .map(v -> v.getVersionNumber() + 1)
                .orElse(1);

        FormVersionEntity entity = new FormVersionEntity();
        entity.setFormId(formId);
        entity.setVersionNumber(nextVersion);
        entity.setContentJson(request.contentJson());
        entity.setCreatedBy(ctx.requestId());

        FormVersionEntity saved = versionRepository.save(entity);

        publishOutbox(ctx, "impilo.forms.version.created.v1", Map.of(
                "versionId", saved.getId(),
                "formId", saved.getFormId(),
                "versionNumber", saved.getVersionNumber()
        ));

        log.info("Version created: formId={}, version={}", formId, nextVersion);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<FormVersionResponse> listByForm(String formId) {
        formRepository.findById(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));

        return versionRepository.findByFormIdOrderByVersionNumberDesc(formId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private void validateContentJson(String contentJson) {
        try {
            JsonNode root = objectMapper.readTree(contentJson);
            if (!root.has("fields") || !root.get("fields").isArray()) {
                throw new InvalidContentException("Content JSON must include a \"fields\" array");
            }
        } catch (JsonProcessingException e) {
            throw new InvalidContentException("Content is not valid JSON: " + e.getMessage());
        }
    }

    private void publishOutbox(RequestContext ctx, String eventType, Map<String, Object> payload) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(toJson(payload));
        outboxRepository.save(outbox);
    }

    FormVersionResponse toResponse(FormVersionEntity entity) {
        return new FormVersionResponse(
                entity.getId(),
                entity.getFormId(),
                entity.getVersionNumber(),
                entity.getContentJson(),
                entity.getCreatedBy(),
                entity.getCreatedAt()
        );
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
