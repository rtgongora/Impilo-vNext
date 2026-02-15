package zw.gov.mohcc.impilo.forms.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.FormPublicationResponse;
import zw.gov.mohcc.impilo.forms.domain.FormPublicationEntity;
import zw.gov.mohcc.impilo.forms.domain.FormVersionEntity;
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.forms.repository.FormDefinitionRepository;
import zw.gov.mohcc.impilo.forms.repository.FormPublicationRepository;
import zw.gov.mohcc.impilo.forms.repository.FormVersionRepository;
import zw.gov.mohcc.impilo.forms.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.Map;

@Service
public class FormPublicationService {

    private static final Logger log = LoggerFactory.getLogger(FormPublicationService.class);

    private final FormPublicationRepository publicationRepository;
    private final FormVersionRepository versionRepository;
    private final FormDefinitionRepository formRepository;
    private final FormDefinitionService formDefinitionService;
    private final OutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FormPublicationService(FormPublicationRepository publicationRepository,
                                  FormVersionRepository versionRepository,
                                  FormDefinitionRepository formRepository,
                                  FormDefinitionService formDefinitionService,
                                  OutboxEventRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.publicationRepository = publicationRepository;
        this.versionRepository = versionRepository;
        this.formRepository = formRepository;
        this.formDefinitionService = formDefinitionService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormPublicationResponse publishLatest(String formId, RequestContext ctx) {
        formRepository.findById(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));

        FormVersionEntity latestVersion = versionRepository
                .findTopByFormIdOrderByVersionNumberDesc(formId)
                .orElseThrow(() -> new NoVersionException(formId));

        FormPublicationEntity pub = new FormPublicationEntity();
        pub.setFormId(formId);
        pub.setVersionId(latestVersion.getId());
        pub.setPublishedBy(ctx.requestId());

        FormPublicationEntity saved = publicationRepository.save(pub);

        formDefinitionService.markPublished(formId);

        publishOutbox(ctx, "impilo.forms.form.published.v1", Map.of(
                "formId", formId,
                "versionId", latestVersion.getId(),
                "versionNumber", latestVersion.getVersionNumber()
        ));

        log.info("Form published: formId={}, version={}", formId, latestVersion.getVersionNumber());

        return new FormPublicationResponse(
                saved.getId(),
                saved.getFormId(),
                saved.getVersionId(),
                latestVersion.getVersionNumber(),
                saved.getPublishedBy(),
                saved.getPublishedAt()
        );
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

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
