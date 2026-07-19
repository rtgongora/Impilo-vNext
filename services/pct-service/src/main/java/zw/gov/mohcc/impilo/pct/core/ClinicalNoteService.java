package zw.gov.mohcc.impilo.pct.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import zw.gov.mohcc.impilo.pct.api.PatientShareProvenanceHeaders;
import zw.gov.mohcc.impilo.pct.persistence.entity.ClinicalNoteEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.ClinicalNoteRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ClinicalNoteService {

    private static final Logger log = LoggerFactory.getLogger(ClinicalNoteService.class);

    private final ClinicalNoteRepository repository;
    private final EncounterRepository encounterRepository;
    private final ObjectMapper objectMapper;

    public ClinicalNoteService(ClinicalNoteRepository repository,
                               EncounterRepository encounterRepository,
                               ObjectMapper objectMapper) {
        this.repository = repository;
        this.encounterRepository = encounterRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Page<ClinicalNoteEntity> list(UUID tenantId, String patientId, int page, int size) {
        return repository.findByTenantIdAndPatientIdOrderByCreatedAtDesc(
                tenantId, patientId, PageRequest.of(page, size));
    }

    /**
     * Resolve the wire {@code encounter_id} to the encounter's UUID handle
     * ({@code encounterRef}) — tolerant of what the caller sends so a consult
     * note is NEVER lost to an id-format mismatch (the previous
     * {@code UUID.fromString} threw whenever the UI sent the numeric encounter
     * id instead of the UUID ref). Accepts a UUID ref as-is, resolves a numeric
     * encounter id via the encounter table, and stores the note unlinked
     * (null) rather than failing when the id is unresolvable.
     */
    private UUID resolveEncounterRef(UUID tenantId, String enc) {
        try {
            return UUID.fromString(enc);
        } catch (IllegalArgumentException notAUuid) {
            // fall through
        }
        try {
            long encId = Long.parseLong(enc);
            return encounterRepository.findById(encId)
                    .filter(e -> tenantId.equals(e.getTenantId()))
                    .map(EncounterEntity::getEncounterRef)
                    .orElse(null);
        } catch (NumberFormatException notNumeric) {
            // fall through
        }
        log.warn("clinical note: unresolvable encounter_id '{}' — storing note without an encounter link", enc);
        return null;
    }

    /**
     * Records a real signature on a note (persisted signed state), replacing the
     * former placebo that always returned signed=false.
     */
    @Transactional
    public ClinicalNoteEntity sign(UUID tenantId, long id, String signerId) {
        ClinicalNoteEntity row = get(tenantId, id);
        row.setSigned(true);
        row.setSignedAt(OffsetDateTime.now());
        row.setSignedBy(signerId);
        ClinicalNoteEntity saved = repository.save(row);
        log.info("pct.clinical_note.signed id={} signedBy={}", saved.getId(), signerId);
        return saved;
    }

    @Transactional(readOnly = true)
    public ClinicalNoteEntity get(UUID tenantId, long id) {
        ClinicalNoteEntity row = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Note not found"));
        if (!row.getTenantId().equals(tenantId)) {
            throw new IllegalArgumentException("Note not found");
        }
        return row;
    }

    @Transactional
    public ClinicalNoteEntity create(Map<String, Object> body) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        ClinicalNoteEntity row = new ClinicalNoteEntity();
        row.setTenantId(tenantId);
        row.setPatientId(requiredString(body, "patient_id"));
        Object ph = body.get("subject_cpid");
        if (ph == null) ph = body.get("patient_health_id"); // actor-plane HID (legacy wire key; value is a CPID)
        if (ph != null && !String.valueOf(ph).isBlank()) {
            row.setSubjectCpid(UUID.fromString(String.valueOf(ph).trim()));
        }
        Object enc = body.get("encounter_id");
        if (enc != null && !String.valueOf(enc).isBlank()) {
            row.setEncounterId(resolveEncounterRef(tenantId, String.valueOf(enc).trim()));
        }
        row.setNoteType(stringOr(body.get("note_type"), "GENERAL"));
        row.setBody(stringOr(body.get("body"), null));
        row.setSubjective(stringOr(body.get("subjective"), null));
        row.setObjective(stringOr(body.get("objective"), null));
        row.setAssessment(stringOr(body.get("assessment"), null));
        row.setPlan(stringOr(body.get("plan"), null));
        row.setAuthorId(requiredString(body, "author_id"));
        row.setAuthorName(stringOr(body.get("author_name"), null));

        Map<String, Object> provenance = buildProvenanceSnapshot(row, ctx);
        mergeBodyProvenance(provenance, body.get("provenance"));
        try {
            row.setProvenance(objectMapper.writeValueAsString(provenance));
        } catch (JsonProcessingException e) {
            row.setProvenance("{}");
        }

        row = repository.save(row);
        log.info(
                "pct.clinical_note.created id={} patientId={} grantId={} contributionId={} correlationId={}",
                row.getId(),
                row.getPatientId(),
                row.getPatientShareGrantId(),
                row.getVitoContributionId(),
                row.getShareCorrelationId() != null ? row.getShareCorrelationId() : ctx.correlationId());
        return row;
    }

    /**
     * Applies patient-share column fields from optional HTTP headers and returns a provenance snapshot
     * (header-sourced fields may be overlaid by {@link #mergeBodyProvenance} for non-authoritative keys).
     */
    private Map<String, Object> buildProvenanceSnapshot(ClinicalNoteEntity row, TrustContext ctx) {
        HttpServletRequest req = currentRequest();
        if (req == null) {
            row.setShareCorrelationId(ctx.correlationId().toString());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("inboundCorrelationId", ctx.correlationId().toString());
            return m;
        }
        Long grantId = parseLongHeader(req, PatientShareProvenanceHeaders.GRANT_ID);
        Long contributionId = parseLongHeader(req, PatientShareProvenanceHeaders.CONTRIBUTION_ID);
        String tempProv = header(req, PatientShareProvenanceHeaders.TEMP_PROVIDER_PUBLIC_ID);
        String shareCorr = header(req, PatientShareProvenanceHeaders.SHARE_CORRELATION_ID);
        String trust = header(req, PatientShareProvenanceHeaders.TRUST_LEVEL);

        row.setPatientShareGrantId(grantId);
        row.setVitoContributionId(contributionId);
        row.setTemporaryProviderPublicId(tempProv);
        row.setTrustLevelAtAuthoring(trust);
        row.setShareCorrelationId(shareCorr != null ? shareCorr : ctx.correlationId().toString());

        Map<String, Object> hdr = new LinkedHashMap<>();
        hdr.put("grantId", grantId);
        hdr.put("contributionId", contributionId);
        hdr.put("temporaryProviderPublicId", tempProv);
        hdr.put("trustLevel", trust);
        hdr.put("shareCorrelationId", row.getShareCorrelationId());
        hdr.put("inboundCorrelationId", ctx.correlationId().toString());
        return hdr;
    }

    private void mergeBodyProvenance(Map<String, Object> into, Object bodyProvenance) {
        if (!(bodyProvenance instanceof Map<?, ?> raw)) {
            return;
        }
        for (Map.Entry<?, ?> e : raw.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("grantId".equals(k)
                    || "contributionId".equals(k)
                    || "temporaryProviderPublicId".equals(k)
                    || "trustLevel".equals(k)
                    || "shareCorrelationId".equals(k)) {
                continue;
            }
            into.put(k, e.getValue());
        }
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return null;
        }
        return sra.getRequest();
    }

    private static String header(HttpServletRequest req, String name) {
        String v = req.getHeader(name);
        if (v == null || v.isBlank()) {
            v = req.getHeader(name.toLowerCase());
        }
        return v != null && !v.isBlank() ? v.trim() : null;
    }

    private static Long parseLongHeader(HttpServletRequest req, String name) {
        String v = header(req, name);
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException("Missing field: " + key);
        }
        return String.valueOf(v).trim();
    }

    private static String stringOr(Object v, String def) {
        if (v == null) {
            return def;
        }
        String s = String.valueOf(v).trim();
        return s.isEmpty() ? def : s;
    }
}
