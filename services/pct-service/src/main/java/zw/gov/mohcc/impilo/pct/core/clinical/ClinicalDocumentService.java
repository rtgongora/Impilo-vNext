package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClinicalDocumentEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ClinicalDocumentRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The clinical document index.
 *
 * <p>PCT owns which document belongs to whose care and under which encounter; document-service owns
 * the object. This service never stores bytes and never duplicates the object's metadata as truth —
 * mime type and size are carried for display only, and the object id is what a reader must use to
 * fetch the content.</p>
 */
@Service
public class ClinicalDocumentService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalDocumentService.class);

    /** Mirrors the CHECK in V102__clinical_documents.sql. */
    static final Set<String> STATUSES = Set.of("ACTIVE", "SUPERSEDED", "WITHDRAWN");

    private final ClinicalDocumentRepository documentRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final ClinicalAccessGuard accessGuard;

    public ClinicalDocumentService(ClinicalDocumentRepository documentRepository,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper,
                                   ClinicalAccessGuard accessGuard) {
        this.documentRepository = documentRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<ClinicalDocumentEntity> list(String subjectCpid, String documentType) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        if (documentType == null || documentType.isBlank()) {
            return documentRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(tenantId, subjectCpid);
        }
        return documentRepository.findByTenantIdAndSubjectCpidAndDocumentTypeOrderByRecordedAtDesc(
                tenantId, subjectCpid, documentType.trim().toUpperCase(Locale.ROOT));
    }

    @Transactional(readOnly = true)
    public ClinicalDocumentEntity get(UUID documentId) {
        return get(documentId, null);
    }

    /**
     * One document, optionally bound to a subject. When {@code subjectCpid} is supplied the row
     * must belong to that subject — the citizen-facing read passes the caller's own CPID, which
     * both authorises the read (a person reading their own record needs no active care context)
     * and scopes it (a mismatch answers "not found" rather than 403, so the id's existence is
     * not disclosed). Without the binding, the provider path keeps the care-relationship guard.
     */
    @Transactional(readOnly = true)
    public ClinicalDocumentEntity get(UUID documentId, String subjectCpid) {
        TrustContext ctx = TrustContextHolder.require();
        ClinicalDocumentEntity d = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found: " + documentId));
        if (!d.getTenantId().equals(ctx.tenantId())) {
            throw new IllegalArgumentException("Document not found: " + documentId);
        }
        if (subjectCpid != null && !subjectCpid.isBlank()) {
            if (!subjectCpid.trim().equals(d.getSubjectCpid())) {
                throw new IllegalArgumentException("Document not found: " + documentId);
            }
            return d;
        }
        accessGuard.requireCareRelationship(ctx, d.getSubjectCpid(), d.getJourneyId(), d.getEncounterId());
        return d;
    }

    @Transactional
    public ClinicalDocumentEntity index(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        ClinicalDocumentEntity d = new ClinicalDocumentEntity();
        d.setTenantId(ctx.tenantId());
        d.setSubjectCpid(required(body, "subject_cpid", "patient_id", "subjectCpid", "patientId"));
        d.setJourneyId(str(body.get("journey_id"), body.get("journeyId")));
        d.setEncounterId(str(body.get("encounter_id"), body.get("encounterId")));
        accessGuard.requireCareRelationship(ctx, d.getSubjectCpid(), d.getJourneyId(), d.getEncounterId());

        d.setDocumentType(upperOr(str(body.get("document_type"), body.get("documentType")), "CLINICAL_NOTE"));
        d.setTitle(required(body, "title", "title", "title", "title"));
        d.setDescription(str(body.get("description")));
        d.setMimeType(str(body.get("mime_type"), body.get("mimeType")));
        d.setStorageKey(str(body.get("storage_key"), body.get("storageKey")));
        d.setStatus(requireStatus(str(body.get("status"))));
        // The author is the authenticated actor. A client-supplied uploader would let a document be
        // attributed to a clinician who never filed it.
        d.setUploadedBy(ctx.actorId());

        String objectId = str(body.get("document_object_id"), body.get("documentObjectId"));
        if (objectId != null) {
            d.setDocumentObjectId(UUID.fromString(objectId));
        }
        String fileSize = str(body.get("file_size"), body.get("fileSize"));
        if (fileSize != null) {
            long parsed = Long.parseLong(fileSize);
            // A zero or negative size is not a smaller file, it is a broken record — and it would
            // violate the CHECK with a message nobody can act on.
            if (parsed <= 0) {
                throw new IllegalArgumentException("file_size must be greater than zero, got " + parsed);
            }
            d.setFileSize(parsed);
        }

        d = documentRepository.save(d);
        emit("CLINICAL_DOCUMENT_INDEXED", d);
        log.info("pct.clinical_document.indexed id={} subject={} type={} by={} correlationId={}",
                d.getDocumentId(), d.getSubjectCpid(), d.getDocumentType(), ctx.actorId(), ctx.correlationId());
        return d;
    }

    private static String requireStatus(String status) {
        if (status == null) return "ACTIVE";
        String normalised = status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalised)) {
            throw new IllegalArgumentException("Unrecognised status: '" + status + "'. Allowed: " + STATUSES);
        }
        return normalised;
    }

    private void emit(String eventType, ClinicalDocumentEntity d) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("documentId", d.getDocumentId().toString());
        payload.put("subjectCpid", d.getSubjectCpid());
        payload.put("encounterId", d.getEncounterId());
        payload.put("documentType", d.getDocumentType());
        payload.put("documentObjectId",
                d.getDocumentObjectId() == null ? null : d.getDocumentObjectId().toString());
        payload.put("actorId", ctx.actorId());

        EventOutboxEntity outbox = new EventOutboxEntity();
        outbox.setAggregateType("CLINICAL_DOCUMENT");
        outbox.setAggregateId(d.getDocumentId().toString());
        outbox.setEventType(eventType);
        outbox.setPayload(toJson(payload));
        outbox.setTenantId(ctx.tenantId());
        outboxRepository.save(outbox);
    }

    private static String required(Map<String, Object> body, String primary, String alt,
                                   String camel, String altCamel) {
        String v = str(body.get(primary), body.get(alt), body.get(camel), body.get(altCamel));
        if (v == null) {
            throw new IllegalArgumentException("Missing field: " + primary);
        }
        return v;
    }

    /** First non-blank candidate, so every key is readable in both spellings. */
    private static String str(Object... candidates) {
        for (Object v : candidates) {
            if (v == null) continue;
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) return s;
        }
        return null;
    }

    private static String upperOr(String v, String def) {
        return v == null ? def : v.toUpperCase(Locale.ROOT);
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialise clinical document payload: {}", e.getMessage());
            return "{}";
        }
    }
}
