package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.PatientDocumentEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.PatientDocumentRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * System of record for the patient document index — the clinical fact that a stored document
 * (a referral letter, a discharge summary, a lab report PDF) belongs to a person's record.
 *
 * <p>The bytes are document-service's; the clinical anchoring is pct's. A row references the
 * binary by {@code storage_key} (and {@code document_object_id} when the caller registered one)
 * and asserts subject, care context, kind and provenance. Same write discipline as the
 * observation spine: the subject-relationship dimension is bound through
 * {@link ClinicalAccessGuard} before anything is written, and every write leaves an event on the
 * outbox.</p>
 */
@Service
public class PatientDocumentService {

    private static final Logger log = LoggerFactory.getLogger(PatientDocumentService.class);

    private final PatientDocumentRepository documentRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public PatientDocumentService(PatientDocumentRepository documentRepository,
                                  EventOutboxRepository outboxRepository,
                                  ObjectMapper objectMapper,
                                  ClinicalAccessGuard accessGuard) {
        this.documentRepository = documentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    /** Documents for a subject, newest first, optionally narrowed by type. Tenant-scoped. */
    @Transactional(readOnly = true)
    public List<PatientDocumentEntity> list(String subjectCpid, String documentType) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (subjectCpid == null || subjectCpid.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        if (documentType != null && !documentType.isBlank()) {
            return documentRepository.findByTenantIdAndSubjectCpidAndDocumentTypeOrderByRecordedAtDesc(
                    tenantId, subjectCpid.trim(), documentType.trim().toUpperCase(Locale.ROOT));
        }
        return documentRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(
                tenantId, subjectCpid.trim());
    }

    /**
     * One document by id, tenant-scoped. When {@code subjectCpid} is supplied the row must also
     * belong to that subject — the citizen-facing read binds the caller to their own record this
     * way, and a mismatch answers 404 rather than 403 so the id's existence is not disclosed.
     */
    @Transactional(readOnly = true)
    public PatientDocumentEntity get(String documentId, String subjectCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        UUID id;
        try {
            id = UUID.fromString(documentId == null ? "" : documentId.trim());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "document id must be a UUID");
        }
        PatientDocumentEntity row = documentRepository.findByTenantIdAndDocumentId(tenantId, id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found"));
        if (subjectCpid != null && !subjectCpid.isBlank()
                && !subjectCpid.trim().equals(row.getSubjectCpid())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "document not found");
        }
        return row;
    }

    /**
     * The single write path. Accepts snake_case or camelCase keys, the same dual dialect as the
     * observation spine, because the BFF and the mobile pack speak different spellings of the
     * same contract.
     */
    @Transactional
    public PatientDocumentEntity create(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a document body is required");
        }
        String subject = str(body.get("subject_cpid"), body.get("subjectCpid"),
                body.get("patient_id"), body.get("patientId"));
        if (subject == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patient_id is required");
        }
        String journeyId = str(body.get("journey_id"), body.get("journeyId"));
        String encounterId = str(body.get("encounter_id"), body.get("encounterId"));

        // Dimension 6 of the access model — subject relationship. Nothing is written before this.
        accessGuard.requireCareRelationship(ctx, subject, journeyId, encounterId);

        String documentType = str(body.get("document_type"), body.get("documentType"));
        String title = str(body.get("title"));
        String storageKey = str(body.get("storage_key"), body.get("storageKey"));
        if (documentType == null || title == null || storageKey == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "document_type, title and storage_key are required: an index entry that cannot "
                            + "say what the document is or where its bytes live indexes nothing");
        }

        PatientDocumentEntity row = new PatientDocumentEntity();
        row.setDocumentId(UUID.randomUUID());
        row.setTenantId(ctx.tenantId());
        row.setSubjectCpid(subject);
        row.setJourneyId(journeyId);
        row.setEncounterId(encounterId);
        row.setFacilityId(ctx.facilityId());
        row.setDocumentType(documentType.toUpperCase(Locale.ROOT));
        row.setTitle(title);
        row.setDescription(str(body.get("description")));
        row.setMimeType(str(body.get("mime_type"), body.get("mimeType")));
        row.setFileSizeBytes(longValue(body.get("file_size"), body.get("fileSize"),
                body.get("file_size_bytes"), body.get("fileSizeBytes")));
        row.setStorageKey(storageKey);
        row.setDocumentObjectId(uuid(body.get("document_object_id"), body.get("documentObjectId")));
        row.setUploadedBy(orDefault(str(body.get("uploaded_by"), body.get("uploadedBy")), ctx.actorId()));
        row.setRecordedAt(OffsetDateTime.now());
        row.setStatus("ACTIVE");

        PatientDocumentEntity saved = documentRepository.save(row);

        // CPID-only event: the index fact, never the document content or a free-text title.
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("document_id", saved.getDocumentId().toString());
        payload.put("subject_cpid", saved.getSubjectCpid());
        payload.put("tenant_id", saved.getTenantId().toString());
        payload.put("journey_id", saved.getJourneyId());
        payload.put("encounter_id", saved.getEncounterId());
        payload.put("document_type", saved.getDocumentType());
        payload.put("mime_type", saved.getMimeType());
        payload.put("recorded_at", saved.getRecordedAt().toString());
        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("PATIENT_DOCUMENT");
        outbox.setAggregateId(saved.getDocumentId().toString());
        outbox.setEventType("pct.patient-document.recorded");
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);

        log.info("pct.patient-document.recorded id={} subject={} type={} by={} correlationId={}",
                saved.getDocumentId(), saved.getSubjectCpid(), saved.getDocumentType(),
                ctx.actorId(), ctx.correlationId());
        return saved;
    }

    /** Wire shape, snake_case to match the contract the BFF sends. */
    public static Map<String, Object> toMap(PatientDocumentEntity row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("document_id", row.getDocumentId() != null ? row.getDocumentId().toString() : null);
        m.put("patient_id", row.getSubjectCpid());
        m.put("subject_cpid", row.getSubjectCpid());
        m.put("journey_id", row.getJourneyId());
        m.put("encounter_id", row.getEncounterId());
        m.put("facility_id", row.getFacilityId() != null ? row.getFacilityId().toString() : null);
        m.put("document_type", row.getDocumentType());
        m.put("title", row.getTitle());
        m.put("description", row.getDescription());
        m.put("mime_type", row.getMimeType());
        m.put("file_size", row.getFileSizeBytes());
        m.put("storage_key", row.getStorageKey());
        m.put("document_object_id",
                row.getDocumentObjectId() != null ? row.getDocumentObjectId().toString() : null);
        m.put("uploaded_by", row.getUploadedBy());
        m.put("recorded_at", row.getRecordedAt() != null ? row.getRecordedAt().toString() : null);
        m.put("status", row.getStatus());
        return m;
    }

    // --- helpers ---------------------------------------------------------------------------

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise patient-document payload", e);
        }
    }

    private static String str(Object... candidates) {
        for (Object value : candidates) {
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value).trim();
            }
        }
        return null;
    }

    private static Long longValue(Object... candidates) {
        for (Object value : candidates) {
            if (value instanceof Number n) {
                return n.longValue();
            }
            if (value != null && !String.valueOf(value).isBlank()) {
                try {
                    return Long.parseLong(String.valueOf(value).trim());
                } catch (NumberFormatException e) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "file_size must be a number of bytes, got: " + value);
                }
            }
        }
        return null;
    }

    private static UUID uuid(Object... candidates) {
        String text = str(candidates);
        if (text == null) {
            return null;
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }
}
