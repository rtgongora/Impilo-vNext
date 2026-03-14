package zw.gov.mohcc.impilo.forms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.FormSchemaRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormSchemaResponse;
import zw.gov.mohcc.impilo.forms.api.dto.FormVersionRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormVersionResponse;
import zw.gov.mohcc.impilo.forms.api.dto.ValidationRequest;
import zw.gov.mohcc.impilo.forms.api.dto.ValidationResponse;
import zw.gov.mohcc.impilo.forms.domain.FormSchemaEntity;
import zw.gov.mohcc.impilo.forms.domain.FormSchemaVersionEntity;
import zw.gov.mohcc.impilo.forms.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.forms.repository.FormSchemaRepository;
import zw.gov.mohcc.impilo.forms.repository.FormSchemaVersionRepository;
import zw.gov.mohcc.impilo.forms.repository.OutboxEventRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class FormSchemaService {

    private static final Logger log = LoggerFactory.getLogger(FormSchemaService.class);

    private final FormSchemaRepository formSchemaRepository;
    private final FormSchemaVersionRepository formSchemaVersionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final SchemaValidationService schemaValidationService;
    private final ObjectMapper objectMapper;

    public FormSchemaService(FormSchemaRepository formSchemaRepository,
                             FormSchemaVersionRepository formSchemaVersionRepository,
                             OutboxEventRepository outboxEventRepository,
                             SchemaValidationService schemaValidationService,
                             ObjectMapper objectMapper) {
        this.formSchemaRepository = formSchemaRepository;
        this.formSchemaVersionRepository = formSchemaVersionRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.schemaValidationService = schemaValidationService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FormSchemaResponse createSchema(FormSchemaRequest request, RequestContext ctx) {
        FormSchemaEntity schema = new FormSchemaEntity();
        schema.setFormKey(request.formKey());
        schema.setName(request.name());
        schema.setDescription(request.description());
        schema.setStatus("DRAFT");
        schema.setCurrentVersion(1);
        schema.setTenantId(ctx.tenantId());
        schema.setPodId(ctx.podId());

        schema = formSchemaRepository.save(schema);
        log.info("Created form schema id={} formKey={} tenant={}", schema.getId(), schema.getFormKey(), ctx.tenantId());

        // Create initial version
        FormSchemaVersionEntity version = new FormSchemaVersionEntity();
        version.setFormSchemaId(schema.getId());
        version.setVersion(1);
        version.setSchemaJson(request.schemaJson());
        formSchemaVersionRepository.save(version);

        emitOutboxEvent(ctx, "impilo.forms.schema.created.v1", Map.of(
                "schemaId", schema.getId(),
                "formKey", schema.getFormKey(),
                "version", 1
        ));

        return toResponse(schema);
    }

    @Transactional(readOnly = true)
    public Optional<FormSchemaResponse> getSchema(String id, String tenantId) {
        return formSchemaRepository.findByIdAndTenantId(id, tenantId)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<FormSchemaResponse> listSchemas(String tenantId) {
        return formSchemaRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FormVersionResponse createVersion(String schemaId, FormVersionRequest request, RequestContext ctx) {
        FormSchemaEntity schema = formSchemaRepository.findByIdAndTenantId(schemaId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Form schema not found: " + schemaId));

        int newVersion = schema.getCurrentVersion() + 1;
        schema.setCurrentVersion(newVersion);
        formSchemaRepository.save(schema);

        FormSchemaVersionEntity versionEntity = new FormSchemaVersionEntity();
        versionEntity.setFormSchemaId(schema.getId());
        versionEntity.setVersion(newVersion);
        versionEntity.setSchemaJson(request.schemaJson());
        versionEntity.setChangelog(request.changelog());
        versionEntity = formSchemaVersionRepository.save(versionEntity);

        log.info("Created version {} for form schema id={} tenant={}", newVersion, schemaId, ctx.tenantId());

        emitOutboxEvent(ctx, "impilo.forms.schema.version_created.v1", Map.of(
                "schemaId", schema.getId(),
                "formKey", schema.getFormKey(),
                "version", newVersion
        ));

        return toVersionResponse(versionEntity);
    }

    @Transactional
    public FormSchemaResponse publishSchema(String schemaId, RequestContext ctx) {
        FormSchemaEntity schema = formSchemaRepository.findByIdAndTenantId(schemaId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Form schema not found: " + schemaId));

        if (!"DRAFT".equals(schema.getStatus())) {
            throw new IllegalStateException(
                    "Cannot publish schema in status " + schema.getStatus() + "; must be DRAFT");
        }

        schema.setStatus("PUBLISHED");
        schema = formSchemaRepository.save(schema);
        log.info("Published form schema id={} tenant={}", schemaId, ctx.tenantId());

        emitOutboxEvent(ctx, "impilo.forms.schema.published.v1", Map.of(
                "schemaId", schema.getId(),
                "formKey", schema.getFormKey(),
                "version", schema.getCurrentVersion()
        ));

        return toResponse(schema);
    }

    @Transactional
    public FormSchemaResponse retireSchema(String schemaId, RequestContext ctx) {
        FormSchemaEntity schema = formSchemaRepository.findByIdAndTenantId(schemaId, ctx.tenantId())
                .orElseThrow(() -> new IllegalArgumentException("Form schema not found: " + schemaId));

        if (!"PUBLISHED".equals(schema.getStatus())) {
            throw new IllegalStateException(
                    "Cannot retire schema in status " + schema.getStatus() + "; must be PUBLISHED");
        }

        schema.setStatus("RETIRED");
        schema = formSchemaRepository.save(schema);
        log.info("Retired form schema id={} tenant={}", schemaId, ctx.tenantId());

        emitOutboxEvent(ctx, "impilo.forms.schema.retired.v1", Map.of(
                "schemaId", schema.getId(),
                "formKey", schema.getFormKey()
        ));

        return toResponse(schema);
    }

    @Transactional(readOnly = true)
    public ValidationResponse validatePayload(String schemaId, ValidationRequest request, String tenantId) {
        FormSchemaEntity schema = formSchemaRepository.findByIdAndTenantId(schemaId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Form schema not found: " + schemaId));

        // Find the latest published version (use current version of the schema)
        FormSchemaVersionEntity versionEntity = formSchemaVersionRepository
                .findByFormSchemaIdAndVersion(schema.getId(), schema.getCurrentVersion())
                .orElseThrow(() -> new IllegalStateException(
                        "No version found for schema " + schemaId + " version " + schema.getCurrentVersion()));

        List<String> errors = schemaValidationService.validate(versionEntity.getSchemaJson(), request.payload());

        return new ValidationResponse(errors.isEmpty(), errors);
    }

    private void emitOutboxEvent(RequestContext ctx, String eventType, Map<String, Object> payload) {
        OutboxEventEntity outbox = new OutboxEventEntity();
        outbox.setTenantId(ctx.tenantId());
        outbox.setPodId(ctx.podId());
        outbox.setCorrelationId(ctx.correlationId());
        outbox.setEventType(eventType);
        outbox.setSchemaVersion(1);
        outbox.setOccurredAt(OffsetDateTime.now());
        outbox.setPayloadJson(serializePayload(payload));
        outboxEventRepository.save(outbox);
    }

    private FormSchemaResponse toResponse(FormSchemaEntity entity) {
        return new FormSchemaResponse(
                entity.getId(),
                entity.getFormKey(),
                entity.getName(),
                entity.getDescription(),
                entity.getCurrentVersion(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private FormVersionResponse toVersionResponse(FormSchemaVersionEntity entity) {
        return new FormVersionResponse(
                entity.getId(),
                entity.getFormSchemaId(),
                entity.getVersion(),
                entity.getSchemaJson(),
                entity.getChangelog(),
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
