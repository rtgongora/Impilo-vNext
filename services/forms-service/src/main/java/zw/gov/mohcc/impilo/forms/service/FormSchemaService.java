package zw.gov.mohcc.impilo.forms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.forms.api.dto.ClinicalFormUpsertRequest;
import zw.gov.mohcc.impilo.forms.api.dto.FormCatalogEntry;
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
import java.util.ArrayList;
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

    private static final List<String> AVAILABLE_STATUSES = List.of("PUBLISHED", "ACTIVE");

    /**
     * Clinical catalog slice for the PCT resolver: available clinical forms for a tenant, each with its
     * current immutable version token + full DAK definition JSON.
     */
    @Transactional(readOnly = true)
    public List<FormCatalogEntry> catalog(String tenantId) {
        List<FormSchemaEntity> schemas =
                formSchemaRepository.findByTenantIdAndClinicalTrueAndStatusIn(tenantId, AVAILABLE_STATUSES);
        List<FormCatalogEntry> entries = new ArrayList<>(schemas.size());
        for (FormSchemaEntity s : schemas) {
            FormSchemaVersionEntity ver = formSchemaVersionRepository
                    .findByFormSchemaIdAndVersion(s.getId(), s.getCurrentVersion())
                    .orElse(null);
            if (ver == null) {
                continue; // no current version snapshot — skip rather than emit a half form
            }
            entries.add(new FormCatalogEntry(
                    s.getId(), s.getFormKey(), s.getName(), s.getDescription(),
                    ver.getVersion(), ver.getId(), s.getStatus(), s.getFormType(),
                    parseList(s.getCareSettings()), parseList(s.getCareStages()), parseList(s.getSpecialties()),
                    parseList(s.getEncounterTypes()), parseList(s.getEncounterContexts()),
                    s.getRequiredWorkflow(), s.getObligationDefault(), parseList(s.getPermittedCadres()),
                    s.getMinAgeMonths(), s.getMaxAgeMonths(), s.getSexApplicability(), s.getRequirePregnant(),
                    parseList(s.getProgrammeApplicability()), parseList(s.getFacilityLevels()),
                    s.isRequiresCountersign(), s.isOfflineCapable(), s.getSensitivity(),
                    ver.getSchemaJson()));
        }
        return entries;
    }

    /**
     * Idempotent upsert of a clinical (WHO-DAK) encounter form definition, keyed by (tenant, formKey).
     * Creates schema + v1 on first sight; adds a new immutable version when the definition body changes;
     * always refreshes the queryable applicability/governance metadata. Optionally publishes.
     */
    @Transactional
    public FormSchemaResponse upsertClinicalForm(ClinicalFormUpsertRequest req, RequestContext ctx) {
        Optional<FormSchemaEntity> existing =
                formSchemaRepository.findByFormKeyAndTenantId(req.formKey(), ctx.tenantId());

        FormSchemaEntity schema = existing.orElseGet(FormSchemaEntity::new);
        boolean isNew = existing.isEmpty();
        if (isNew) {
            schema.setFormKey(req.formKey());
            schema.setTenantId(ctx.tenantId());
            schema.setPodId(ctx.podId());
            schema.setCurrentVersion(1);
            schema.setStatus("DRAFT");
        }
        schema.setName(req.name());
        schema.setDescription(req.description());
        applyClinicalMetadata(schema, req);
        schema = formSchemaRepository.save(schema);

        // Version handling: create v1 for new; add a new version if the definition body changed.
        Optional<FormSchemaVersionEntity> current = formSchemaVersionRepository
                .findByFormSchemaIdAndVersion(schema.getId(), schema.getCurrentVersion());
        boolean definitionChanged = current.isEmpty()
                || !normalize(current.get().getSchemaJson()).equals(normalize(req.definition()));

        int versionNumber = schema.getCurrentVersion();
        if (!isNew && definitionChanged) {
            versionNumber = schema.getCurrentVersion() + 1;
            schema.setCurrentVersion(versionNumber);
            formSchemaRepository.save(schema);
        }
        if (definitionChanged) {
            FormSchemaVersionEntity version = new FormSchemaVersionEntity();
            version.setFormSchemaId(schema.getId());
            version.setVersion(versionNumber);
            version.setSchemaJson(req.definition());
            version.setChangelog(req.changeReason());
            formSchemaVersionRepository.save(version);
        }

        if (Boolean.TRUE.equals(req.publish()) && !"PUBLISHED".equals(schema.getStatus())
                && !"ACTIVE".equals(schema.getStatus())) {
            schema.setStatus("PUBLISHED");
            schema = formSchemaRepository.save(schema);
        }

        emitOutboxEvent(ctx, "impilo.forms.clinical_form.upserted.v1", Map.of(
                "schemaId", schema.getId(),
                "formKey", schema.getFormKey(),
                "version", versionNumber,
                "clinical", true
        ));
        log.info("Upserted clinical form key={} id={} version={} tenant={}",
                schema.getFormKey(), schema.getId(), versionNumber, ctx.tenantId());
        return toResponse(schema);
    }

    private void applyClinicalMetadata(FormSchemaEntity schema, ClinicalFormUpsertRequest req) {
        schema.setClinical(true);
        schema.setFormType(req.formType());
        schema.setCareSettings(writeList(req.careSettings()));
        schema.setCareStages(writeList(req.careStages()));
        schema.setSpecialties(writeList(req.specialties()));
        schema.setEncounterTypes(writeList(req.encounterTypes()));
        schema.setEncounterContexts(writeList(req.encounterContexts()));
        schema.setRequiredWorkflow(req.requiredWorkflow());
        if (req.obligationDefault() != null) {
            schema.setObligationDefault(req.obligationDefault());
        }
        schema.setPermittedCadres(writeList(req.permittedCadres()));
        schema.setMinAgeMonths(req.minAgeMonths());
        schema.setMaxAgeMonths(req.maxAgeMonths());
        if (req.sexApplicability() != null) {
            schema.setSexApplicability(req.sexApplicability());
        }
        schema.setRequirePregnant(req.requirePregnant());
        schema.setProgrammeApplicability(writeList(req.programmeApplicability()));
        schema.setFacilityLevels(writeList(req.facilityLevels()));
        schema.setEligibilityJson(writeJson(req.eligibility()));
        schema.setTerminologyBindings(writeJson(req.terminologyBindings()));
        schema.setResourceMappings(writeJson(req.resourceMappings()));
        schema.setIndicators(writeJson(req.indicators()));
        if (req.requiresCountersign() != null) {
            schema.setRequiresCountersign(req.requiresCountersign());
        }
        if (req.offlineCapable() != null) {
            schema.setOfflineCapable(req.offlineCapable());
        }
        if (req.sensitivity() != null) {
            schema.setSensitivity(req.sensitivity());
        }
        schema.setGovernanceAuthor(req.governanceAuthor());
        schema.setGovernanceReviewer(req.governanceReviewer());
        schema.setGovernanceApprover(req.governanceApprover());
        schema.setSourceGuideline(req.sourceGuideline());
        schema.setChangeReason(req.changeReason());
    }

    private List<String> parseList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse JSON list, returning empty: {}", e.getMessage());
            return List.of();
        }
    }

    private String writeList(List<String> values) {
        if (values == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize list", e);
        }
    }

    private String writeJson(JsonNode node) {
        return node == null || node.isNull() ? null : node.toString();
    }

    /** Compact JSON for change-detection; falls back to trimmed string on parse failure. */
    private String normalize(String json) {
        if (json == null) {
            return "";
        }
        try {
            return objectMapper.readTree(json).toString();
        } catch (JsonProcessingException e) {
            return json.trim();
        }
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
