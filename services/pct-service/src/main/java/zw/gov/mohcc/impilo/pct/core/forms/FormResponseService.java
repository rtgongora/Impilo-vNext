package zw.gov.mohcc.impilo.pct.core.forms;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.core.clinical.ClinicalAccessGuard;
import zw.gov.mohcc.impilo.pct.integration.FormsCatalogIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EncounterEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormAmendmentEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResponseEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormSignatureEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EncounterRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormAmendmentRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormResponseRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.FormSignatureRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Encounter form response lifecycle: draft → in_progress → submitted → amended / voided / superseded, with
 * version-locking at DRAFT and author/countersignature handling. Extraction to clinical resources is wired
 * by {@code FormExtractionService} (see {@link #onSubmitted}).
 */
@Service
public class FormResponseService {

    private static final Logger log = LoggerFactory.getLogger(FormResponseService.class);

    private final FormResponseRepository responseRepository;
    private final FormSignatureRepository signatureRepository;
    private final FormAmendmentRepository amendmentRepository;
    private final FormsCatalogIntegration formsCatalogIntegration;
    private final EncounterRepository encounterRepository;
    private final ClinicalAccessGuard accessGuard;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final FormExtractionHook extractionHook;

    public FormResponseService(FormResponseRepository responseRepository,
                               FormSignatureRepository signatureRepository,
                               FormAmendmentRepository amendmentRepository,
                               FormsCatalogIntegration formsCatalogIntegration,
                               EncounterRepository encounterRepository,
                               ClinicalAccessGuard accessGuard,
                               EventOutboxRepository outboxRepository,
                               ObjectMapper objectMapper,
                               FormExtractionHook extractionHook) {
        this.responseRepository = responseRepository;
        this.signatureRepository = signatureRepository;
        this.amendmentRepository = amendmentRepository;
        this.formsCatalogIntegration = formsCatalogIntegration;
        this.encounterRepository = encounterRepository;
        this.accessGuard = accessGuard;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.extractionHook = extractionHook;
    }

    /** Create a DRAFT response, version-locked against the current published form version. */
    @Transactional
    public FormResponseEntity createDraft(Long encounterId, String formKey, Map<String, Object> answers) {
        TrustContext ctx = TrustContextHolder.require();
        FormCatalogEntry entry = requireCatalogEntry(formKey);

        EncounterEntity enc = encounterId == null ? null
                : encounterRepository.findByTenantIdAndId(ctx.tenantId(), encounterId).orElse(null);
        if (encounterId != null && enc == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Encounter not found: " + encounterId);
        }

        String cpid = enc != null ? enc.getSubjectCpid() : null;
        String journeyId = enc != null ? enc.getJourneyId() : null;
        String encIdStr = encounterId == null ? null : String.valueOf(encounterId);

        // Subject-relationship gate (dimension 6): actor must hold an active care context for this patient.
        accessGuard.requireCareRelationship(ctx, cpid, journeyId, encIdStr);

        FormResponseEntity r = new FormResponseEntity();
        r.setTenantId(ctx.tenantId());
        r.setSubjectCpid(cpid);
        r.setJourneyId(journeyId);
        r.setEncounterId(encIdStr);
        r.setEncounterRef(enc != null ? enc.getEncounterRef() : null);
        r.setButanoEncounterRef(enc != null ? enc.getButanoEncounterRef() : null);
        // Version-lock the definition binding — immutable for the life of the response.
        r.setFormKey(entry.formKey());
        r.setFormSchemaId(entry.formSchemaId());
        r.setFormVersion(entry.version());
        r.setFormSchemaVersionId(entry.formSchemaVersionId());
        r.setFormTitle(entry.name());
        r.setObligation(entry.obligationDefault());
        r.setCountersignRequired(entry.requiresCountersign());
        r.setStatus("DRAFT");
        r.setAnswers(answers == null || answers.isEmpty() ? "{}" : toJson(answers));
        r.setProviderId(ctx.actorId());
        r.setFacilityId(enc != null ? enc.getFacilityId() : ctx.facilityId());
        r.setWorkspaceId(ctx.workspaceId());
        r.setCreatedBy(ctx.actorId());
        r = responseRepository.save(r);

        writeOutbox(ctx.tenantId(), r.getResponseId(), "FORM_RESPONSE_DRAFTED", r);
        log.info("pct.form.response.drafted id={} formKey={} version={} encounter={} by={}",
                r.getResponseId(), r.getFormKey(), r.getFormVersion(), encIdStr, ctx.actorId());
        return r;
    }

    @Transactional
    public FormResponseEntity updateAnswers(UUID responseId, Map<String, Object> answers) {
        TrustContext ctx = TrustContextHolder.require();
        FormResponseEntity r = require(ctx, responseId);
        if (!"DRAFT".equals(r.getStatus()) && !"IN_PROGRESS".equals(r.getStatus())) {
            throw conflict("Cannot edit a response in status " + r.getStatus());
        }
        r.setAnswers(answers == null ? "{}" : toJson(answers));
        r.setStatus("IN_PROGRESS");
        return responseRepository.save(r);
    }

    @Transactional
    public FormResponseEntity submit(UUID responseId, String attestation) {
        TrustContext ctx = TrustContextHolder.require();
        FormResponseEntity r = require(ctx, responseId);
        if (!"DRAFT".equals(r.getStatus()) && !"IN_PROGRESS".equals(r.getStatus())) {
            throw conflict("Cannot submit a response in status " + r.getStatus());
        }
        validateAnswers(r);

        r.setStatus("SUBMITTED");
        r.setSubmittedBy(ctx.actorId());
        r.setSubmittedAt(OffsetDateTime.now());
        r = responseRepository.save(r);

        sign(ctx, r, "AUTHOR", attestation);
        writeOutbox(ctx.tenantId(), r.getResponseId(), "FORM_RESPONSE_SUBMITTED", r);

        // Extraction is deferred when a countersignature is required; otherwise run now.
        if (!r.isCountersignRequired()) {
            onSubmitted(r);
        } else {
            log.info("pct.form.response.submitted id={} extraction DEFERRED pending countersign", r.getResponseId());
        }
        log.info("pct.form.response.submitted id={} formKey={} by={} correlationId={}",
                r.getResponseId(), r.getFormKey(), ctx.actorId(), ctx.correlationId());
        return r;
    }

    @Transactional
    public FormResponseEntity countersign(UUID responseId, String attestation) {
        TrustContext ctx = TrustContextHolder.require();
        FormResponseEntity r = require(ctx, responseId);
        if (!"SUBMITTED".equals(r.getStatus()) && !"AMENDED".equals(r.getStatus())) {
            throw conflict("Cannot countersign a response in status " + r.getStatus());
        }
        if (signatureRepository.existsByResponseIdAndSignatureKind(responseId, "COUNTERSIGN")) {
            throw conflict("Response already countersigned");
        }
        sign(ctx, r, "COUNTERSIGN", attestation);
        writeOutbox(ctx.tenantId(), r.getResponseId(), "FORM_RESPONSE_COUNTERSIGNED", r);

        // Deferred extraction now runs.
        if (r.isCountersignRequired()) {
            onSubmitted(r);
        }
        log.info("pct.form.response.countersigned id={} by={}", responseId, ctx.actorId());
        return r;
    }

    @Transactional
    public FormResponseEntity amend(UUID responseId, Map<String, Object> newAnswers, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amendment reason is required");
        }
        FormResponseEntity r = require(ctx, responseId);
        if (!"SUBMITTED".equals(r.getStatus()) && !"AMENDED".equals(r.getStatus())) {
            throw conflict("Cannot amend a response in status " + r.getStatus());
        }
        String prior = r.getAnswers();
        String updated = newAnswers == null ? "{}" : toJson(newAnswers);

        FormAmendmentEntity a = new FormAmendmentEntity();
        a.setTenantId(ctx.tenantId());
        a.setResponseId(responseId);
        a.setPriorAnswers(prior);
        a.setNewAnswers(updated);
        a.setChangedLinkIds(toJson(changedLinkIds(prior, updated)));
        a.setReason(reason);
        a.setAmendedBy(ctx.actorId());
        amendmentRepository.save(a);

        r.setAnswers(updated);
        r.setStatus("AMENDED");
        r = responseRepository.save(r);
        writeOutbox(ctx.tenantId(), r.getResponseId(), "FORM_RESPONSE_AMENDED", r);
        log.info("pct.form.response.amended id={} by={}", responseId, ctx.actorId());
        return r;
    }

    @Transactional
    public FormResponseEntity voidResponse(UUID responseId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Void reason is required");
        }
        FormResponseEntity r = require(ctx, responseId);
        if ("VOIDED".equals(r.getStatus())) {
            throw conflict("Response already voided");
        }
        r.setStatus("VOIDED");
        r.setVoidedBy(ctx.actorId());
        r.setVoidedAt(OffsetDateTime.now());
        r.setVoidReason(reason);
        r = responseRepository.save(r);
        writeOutbox(ctx.tenantId(), r.getResponseId(), "FORM_RESPONSE_VOIDED", r);
        log.info("pct.form.response.voided id={} by={} reason={}", responseId, ctx.actorId(), reason);
        return r;
    }

    @Transactional(readOnly = true)
    public FormResponseEntity get(UUID responseId) {
        return require(TrustContextHolder.require(), responseId);
    }

    @Transactional(readOnly = true)
    public List<FormResponseEntity> listForEncounter(String encounterId) {
        TrustContext ctx = TrustContextHolder.require();
        return responseRepository.findByTenantIdAndEncounterIdOrderByCreatedAtDesc(ctx.tenantId(), encounterId);
    }

    @Transactional(readOnly = true)
    public List<FormResponseEntity> listForSubject(String cpid) {
        TrustContext ctx = TrustContextHolder.require();
        return responseRepository.findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(ctx.tenantId(), cpid);
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    /** Extraction seam: runs on submit (or countersign when deferred). */
    private void onSubmitted(FormResponseEntity r) {
        try {
            extractionHook.extract(r);
        } catch (RuntimeException e) {
            // Extraction is best-effort and eventually consistent; never fail the submit on it.
            log.warn("pct.form.extraction.failed id={}: {}", r.getResponseId(), e.getMessage());
        }
    }

    private FormCatalogEntry requireCatalogEntry(String formKey) {
        if (formKey == null || formKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "formKey is required");
        }
        return formsCatalogIntegration.fetchCatalog().stream()
                .filter(e -> formKey.equalsIgnoreCase(e.formKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No published clinical form for key: " + formKey));
    }

    private FormResponseEntity require(TrustContext ctx, UUID responseId) {
        Optional<FormResponseEntity> r = responseRepository.findByTenantIdAndResponseId(ctx.tenantId(), responseId);
        return r.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Form response not found: " + responseId));
    }

    private void sign(TrustContext ctx, FormResponseEntity r, String kind, String attestation) {
        if ("AUTHOR".equals(kind) && signatureRepository.existsByResponseIdAndSignatureKind(r.getResponseId(), "AUTHOR")) {
            return;
        }
        FormSignatureEntity sig = new FormSignatureEntity();
        sig.setTenantId(ctx.tenantId());
        sig.setResponseId(r.getResponseId());
        sig.setSignatureKind(kind);
        sig.setSignedBy(ctx.actorId());
        sig.setSignedByRole(ctx.actorType());
        sig.setAttestation(attestation);
        signatureRepository.save(sig);
    }

    private void validateAnswers(FormResponseEntity r) {
        // Minimal structural validation. Full DAK required/range validation against the locked definition
        // is applied in the extraction/validation pass (Wave 4 QuestionnaireResponseMapper).
        try {
            JsonNode node = objectMapper.readTree(r.getAnswers() == null ? "{}" : r.getAnswers());
            if (!node.isObject() || node.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Cannot submit an empty form response");
            }
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Answers is not valid JSON");
        }
    }

    private List<String> changedLinkIds(String priorJson, String newJson) {
        try {
            JsonNode prior = objectMapper.readTree(priorJson == null ? "{}" : priorJson);
            JsonNode now = objectMapper.readTree(newJson == null ? "{}" : newJson);
            java.util.LinkedHashSet<String> changed = new java.util.LinkedHashSet<>();
            now.fieldNames().forEachRemaining(f -> {
                if (!prior.has(f) || !prior.get(f).equals(now.get(f))) {
                    changed.add(f);
                }
            });
            prior.fieldNames().forEachRemaining(f -> {
                if (!now.has(f)) {
                    changed.add(f);
                }
            });
            return List.copyOf(changed);
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private void writeOutbox(UUID tenantId, UUID responseId, String eventType, FormResponseEntity r) {
        try {
            Map<String, Object> payload = Map.of(
                    "responseId", responseId.toString(),
                    "formKey", r.getFormKey(),
                    "formVersion", r.getFormVersion(),
                    "status", r.getStatus(),
                    "encounterId", r.getEncounterId() == null ? "" : r.getEncounterId());
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType("FORM_RESPONSE");
            outbox.setAggregateId(responseId.toString());
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(payload));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            log.warn("Failed to write form response outbox event {}: {}", eventType, e.getMessage());
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid answers payload");
        }
    }

    private ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
