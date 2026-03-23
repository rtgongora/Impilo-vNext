package zw.gov.mohcc.impilo.schemaregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.contract.schema.CompatibilityResult;
import zw.gov.mohcc.impilo.contract.schema.SchemaCompatibilityValidator;
import zw.gov.mohcc.impilo.schemaregistry.api.dto.RegisterSchemaRequest;
import zw.gov.mohcc.impilo.schemaregistry.domain.OutboxEventEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SchemaVersionEntity;
import zw.gov.mohcc.impilo.schemaregistry.domain.SubjectEntity;
import zw.gov.mohcc.impilo.schemaregistry.repository.OutboxEventRepository;
import zw.gov.mohcc.impilo.schemaregistry.repository.SchemaVersionRepository;
import zw.gov.mohcc.impilo.schemaregistry.repository.SubjectRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class SchemaRegistryService {

    private final SubjectRepository subjectRepo;
    private final SchemaVersionRepository versionRepo;
    private final OutboxEventRepository outboxRepo;
    private final ObjectMapper objectMapper;

    public SchemaRegistryService(SubjectRepository subjectRepo, SchemaVersionRepository versionRepo,
                                  OutboxEventRepository outboxRepo, ObjectMapper objectMapper) {
        this.subjectRepo = subjectRepo;
        this.versionRepo = versionRepo;
        this.outboxRepo = outboxRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> registerSchema(UUID tenantId, String correlationId,
                                               String idempotencyKey, RegisterSchemaRequest request) {
        // Find or create subject
        SubjectEntity subject = subjectRepo.findBySubjectName(request.subject())
                .orElseGet(() -> {
                    SubjectEntity s = new SubjectEntity();
                    s.setSubjectName(request.subject());
                    s.setDescription(request.description());
                    return subjectRepo.save(s);
                });

        // Check compatibility with latest version
        Optional<SchemaVersionEntity> latestOpt = versionRepo.findFirstBySubjectIdOrderByVersionDesc(subject.getId());

        if (latestOpt.isPresent()) {
            SchemaVersionEntity latest = latestOpt.get();
            CompatibilityResult compat = SchemaCompatibilityValidator.checkBackwardCompatibility(
                    latest.getSchemaJson(), request.schema());
            if (!compat.compatible()) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("compatible", false);
                error.put("violations", compat.violations());
                error.put("message", "Schema is not backward-compatible with version " + latest.getVersion());
                return error;
            }
        }

        // Compute fingerprint
        String fingerprint = sha256(request.schema());

        // Determine next version
        int nextVersion = latestOpt.map(v -> v.getVersion() + 1).orElse(1);

        // Store schema version
        SchemaVersionEntity version = new SchemaVersionEntity();
        version.setSubjectId(subject.getId());
        version.setVersion(nextVersion);
        version.setSchemaJson(request.schema());
        version.setFingerprint(fingerprint);
        version = versionRepo.save(version);

        // Emit outbox event
        emitOutboxEvent("Schema", version.getId().toString(),
                "impilo.schema-registry.schema.registered.v1",
                tenantId, correlationId, idempotencyKey,
                subject.getSubjectName(), "Schema",
                Map.of("subject", subject.getSubjectName(),
                        "version", nextVersion,
                        "fingerprint", fingerprint));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", version.getId());
        response.put("subject", subject.getSubjectName());
        response.put("version", nextVersion);
        response.put("compatible", true);
        response.put("fingerprint", fingerprint);
        return response;
    }

    public List<SubjectEntity> listSubjects() {
        return subjectRepo.findAll();
    }

    public Optional<SubjectEntity> getSubject(String subjectName) {
        return subjectRepo.findBySubjectName(subjectName);
    }

    public List<SchemaVersionEntity> listVersions(UUID subjectId) {
        return versionRepo.findBySubjectIdOrderByVersionDesc(subjectId);
    }

    public Optional<SchemaVersionEntity> getVersion(UUID subjectId, int version) {
        return versionRepo.findBySubjectIdAndVersion(subjectId, version);
    }

    public Optional<SchemaVersionEntity> getLatestVersion(UUID subjectId) {
        return versionRepo.findFirstBySubjectIdOrderByVersionDesc(subjectId);
    }

    /**
     * Check compatibility of a proposed schema against the latest registered version.
     */
    public CompatibilityResult checkCompatibility(String subjectName, String proposedSchema) {
        Optional<SubjectEntity> subjectOpt = subjectRepo.findBySubjectName(subjectName);
        if (subjectOpt.isEmpty()) {
            return CompatibilityResult.ofCompatible(); // No existing schema = always compatible
        }

        Optional<SchemaVersionEntity> latestOpt =
                versionRepo.findFirstBySubjectIdOrderByVersionDesc(subjectOpt.get().getId());
        if (latestOpt.isEmpty()) {
            return CompatibilityResult.ofCompatible();
        }

        return SchemaCompatibilityValidator.checkBackwardCompatibility(
                latestOpt.get().getSchemaJson(), proposedSchema);
    }

    private void emitOutboxEvent(String aggregateType, String aggregateId, String eventType,
                                  UUID tenantId, String correlationId, String idempotencyKey,
                                  String subjectId, String subjectType,
                                  Map<String, Object> payload) {
        OutboxEventEntity event = new OutboxEventEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setOccurredAt(OffsetDateTime.now());
        if (tenantId != null) event.setTenantId(tenantId);
        if (correlationId != null) {
            try { event.setCorrelationId(UUID.fromString(correlationId)); }
            catch (Exception ignored) {}
        }
        event.setIdempotencyKey(idempotencyKey);
        event.setSubjectId(subjectId);
        event.setSubjectType(subjectType);
        try { event.setPayloadJson(objectMapper.writeValueAsString(payload)); }
        catch (Exception ignored) {}
        outboxRepo.save(event);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) { hex.append(String.format("%02x", b)); }
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
