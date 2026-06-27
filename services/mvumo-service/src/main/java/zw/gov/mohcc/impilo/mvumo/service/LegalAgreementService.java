package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.LegalAgreementEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.LegalAgreementRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates legal/platform agreement acceptance (Privacy Policy, Terms of Use) — the
 * real, audited home for what the BFF previously only logged. Reuses the Mvumo consent-event
 * audit log + transactional outbox, but never touches tshepo-consent (legal ≠ FHIR clinical
 * consent). Enforcement is a session-admission gate via {@link #evaluate}, not per-resource.
 */
@Service
public class LegalAgreementService {

    private static final Set<String> DOCUMENT_TYPES = Set.of("PRIVACY_POLICY", "TERMS_OF_USE");

    private final LegalAgreementRepository repository;
    private final ConsentEventRepository consentEventRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public LegalAgreementService(LegalAgreementRepository repository,
                                 ConsentEventRepository consentEventRepository,
                                 EventOutboxRepository eventOutboxRepository,
                                 ObjectMapper objectMapper) {
        this.repository = repository;
        this.consentEventRepository = consentEventRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public Map<String, Object> accept(UUID tenantId, Map<String, Object> body) {
        String subjectActorId = required(body, "subjectActorId");
        String documentType = normaliseDocumentType(required(body, "documentType"));
        String version = required(body, "version");

        List<LegalAgreementEntity> currentGranted = repository
                .findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                        tenantId, subjectActorId, documentType, LegalAgreementEntity.State.GRANTED.name());

        // Idempotent: re-accepting the same version is a no-op.
        for (LegalAgreementEntity existing : currentGranted) {
            if (existing.getVersion().equals(version)) {
                return toView(existing);
            }
        }
        // A newer acceptance supersedes any prior granted version for this document type.
        Instant now = Instant.now();
        for (LegalAgreementEntity stale : currentGranted) {
            stale.setState(LegalAgreementEntity.State.SUPERSEDED.name());
            stale.setUpdatedAt(now);
            repository.save(stale);
        }

        LegalAgreementEntity e = new LegalAgreementEntity();
        e.setTenantId(tenantId);
        e.setSubjectActorId(subjectActorId);
        e.setDocumentType(documentType);
        e.setVersion(version);
        e.setState(LegalAgreementEntity.State.GRANTED.name());
        e.setChannel(str(body.getOrDefault("channel", "WEB")).toUpperCase(java.util.Locale.ROOT));
        e.setAssurance(opt(body, "assurance"));
        e.setActorRef(opt(body, "actorRef"));
        e.setActorRelationship(body.get("actorRelationship") == null ? "self" : str(body.get("actorRelationship")));
        e.setProofRef(opt(body, "proofRef"));
        e.setDeviceMetadata(writeJson(body.get("deviceMetadata")));
        e.setAcceptedAt(now);
        e = repository.save(e);

        appendEvent(e, "LEGAL_AGREEMENT_ACCEPTED");
        enqueueOutbox(e, "impilo.mvumo.legal_agreement.accepted.v1");
        return toView(e);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForSubject(UUID tenantId, String subjectActorId) {
        return repository.findByTenantIdAndSubjectActorIdOrderByCreatedAtDesc(tenantId, subjectActorId)
                .stream().map(this::toView).toList();
    }

    @Transactional
    public Map<String, Object> withdraw(UUID tenantId, UUID id, String actorRef) {
        LegalAgreementEntity e = repository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Legal agreement not found"));
        if (LegalAgreementEntity.State.WITHDRAWN.name().equals(e.getState())) {
            return toView(e); // idempotent
        }
        e.setState(LegalAgreementEntity.State.WITHDRAWN.name());
        e.setWithdrawnAt(Instant.now());
        e.setUpdatedAt(Instant.now());
        if (actorRef != null && !actorRef.isBlank()) {
            e.setActorRef(actorRef);
        }
        e = repository.save(e);
        appendEvent(e, "LEGAL_AGREEMENT_WITHDRAWN");
        enqueueOutbox(e, "impilo.mvumo.legal_agreement.withdrawn.v1");
        return toView(e);
    }

    /**
     * Session-admission evaluation: given the required {documentType: version} set, return which
     * documents the subject has currently accepted, which are missing, and which are accepted at a
     * stale (superseded) version. Drives the login interstitial.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> evaluate(UUID tenantId, String subjectActorId,
                                        Map<String, String> requiredVersions) {
        List<String> missing = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (Map.Entry<String, String> req : requiredVersions.entrySet()) {
            String documentType = normaliseDocumentType(req.getKey());
            String requiredVersion = req.getValue();
            List<LegalAgreementEntity> granted = repository
                    .findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
                            tenantId, subjectActorId, documentType, LegalAgreementEntity.State.GRANTED.name());
            if (granted.isEmpty()) {
                missing.add(documentType);
            } else if (granted.stream().noneMatch(g -> g.getVersion().equals(requiredVersion))) {
                stale.add(documentType);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentlyAccepted", missing.isEmpty() && stale.isEmpty());
        result.put("missing", missing);
        result.put("staleVersions", stale);
        return result;
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void appendEvent(LegalAgreementEntity e, String eventType) {
        ConsentEventEntity ev = new ConsentEventEntity();
        ev.setTenantId(e.getTenantId());
        ev.setConsentId(e.getId());
        ev.setEventType(eventType);
        ev.setActorRef(e.getActorRef() != null ? e.getActorRef() : "self");
        ev.setDetail(writeJson(Map.of(
                "documentType", e.getDocumentType(), "version", e.getVersion(),
                "state", e.getState(), "channel", e.getChannel())));
        consentEventRepository.save(ev);
    }

    private void enqueueOutbox(LegalAgreementEntity e, String eventType) {
        EventOutboxEntity row = new EventOutboxEntity();
        row.setAggregateType("LegalAgreement");
        row.setAggregateId(e.getId().toString());
        row.setEventType(eventType);
        row.setPayload(writeJson(Map.of(
                "legalAgreementId", e.getId().toString(), "subjectActorId", e.getSubjectActorId(),
                "documentType", e.getDocumentType(), "version", e.getVersion(), "state", e.getState())));
        eventOutboxRepository.save(row);
    }

    private Map<String, Object> toView(LegalAgreementEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId().toString());
        m.put("subjectActorId", e.getSubjectActorId());
        m.put("documentType", e.getDocumentType());
        m.put("version", e.getVersion());
        m.put("state", e.getState());
        m.put("channel", e.getChannel());
        m.put("assurance", e.getAssurance());
        m.put("actorRelationship", e.getActorRelationship());
        m.put("acceptedAt", e.getAcceptedAt() != null ? e.getAcceptedAt().toString() : null);
        m.put("withdrawnAt", e.getWithdrawnAt() != null ? e.getWithdrawnAt().toString() : null);
        return m;
    }

    private String normaliseDocumentType(String raw) {
        String v = raw == null ? "" : raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (!DOCUMENT_TYPES.contains(v)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "documentType must be one of " + DOCUMENT_TYPES);
        }
        return v;
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || v.toString().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " is required");
        }
        return v.toString();
    }

    private static String opt(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private static String str(Object o) {
        return Objects.toString(o, "");
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "json serialisation");
        }
    }
}
