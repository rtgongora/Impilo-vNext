package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.domain.ConsentRequestState;
import zw.gov.mohcc.impilo.mvumo.engine.ConsentRequirementEngine;
import zw.gov.mohcc.impilo.mvumo.integration.FhirMvumoConsentBuilder;
import zw.gov.mohcc.impilo.mvumo.integration.TshepoConsentClient;
import zw.gov.mohcc.impilo.mvumo.surface.ConsentSummaryDeriver;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentEventRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentRequestRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentTemplateEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.ConsentTemplateRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.RemoteConsentSessionEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.RemoteConsentSessionRepository;
import zw.gov.mohcc.impilo.mvumo.redis.RemoteConsentTokenRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MvumoService {

    private final ConsentTemplateRepository templateRepository;
    private final ConsentRequestRepository requestRepository;
    private final RemoteConsentSessionRepository sessionRepository;
    private final ObjectMapper objectMapper;
    private final ConsentRequirementEngine requirementEngine;
    private final EventOutboxRepository eventOutboxRepository;
    private final ConsentEventRepository consentEventRepository;
    private final TshepoConsentClient tshepoConsentClient;
    private final RemoteConsentTokenRegistry tokenRegistry;
    private final ConsentSummaryDeriver consentSummaryDeriver;
    private final CommunicationPreferenceService communicationPreferenceService;

    public MvumoService(
            ConsentTemplateRepository templateRepository,
            ConsentRequestRepository requestRepository,
            RemoteConsentSessionRepository sessionRepository,
            ObjectMapper objectMapper,
            ConsentRequirementEngine requirementEngine,
            EventOutboxRepository eventOutboxRepository,
            ConsentEventRepository consentEventRepository,
            TshepoConsentClient tshepoConsentClient,
            ObjectProvider<RemoteConsentTokenRegistry> tokenRegistryProvider,
            ConsentSummaryDeriver consentSummaryDeriver,
            CommunicationPreferenceService communicationPreferenceService) {
        this.templateRepository = templateRepository;
        this.requestRepository = requestRepository;
        this.sessionRepository = sessionRepository;
        this.objectMapper = objectMapper;
        this.requirementEngine = requirementEngine;
        this.eventOutboxRepository = eventOutboxRepository;
        this.consentEventRepository = consentEventRepository;
        this.tshepoConsentClient = tshepoConsentClient;
        this.tokenRegistry = tokenRegistryProvider.getIfAvailable();
        this.consentSummaryDeriver = consentSummaryDeriver;
        this.communicationPreferenceService = communicationPreferenceService;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listTemplates(UUID tenantId) {
        return mergeTemplates(
                        templateRepository.findByTenantIdAndRetiredAtIsNullOrderByTemplateKeyAscVersionDesc(tenantId),
                        templateRepository.findByTenantIdAndRetiredAtIsNullOrderByTemplateKeyAscVersionDesc(TenantIds.PLATFORM))
                .stream()
                .map(this::toTemplateView)
                .toList();
    }

    /** Tenant rows override platform defaults for same key+version+language. */
    private List<ConsentTemplateEntity> mergeTemplates(List<ConsentTemplateEntity> tenant, List<ConsentTemplateEntity> platform) {
        var map = new HashMap<String, ConsentTemplateEntity>();
        for (ConsentTemplateEntity t : platform) {
            map.put(key(t), t);
        }
        for (ConsentTemplateEntity t : tenant) {
            map.put(key(t), t);
        }
        return map.values().stream()
                .sorted(Comparator.comparing(ConsentTemplateEntity::getTemplateKey).thenComparing(ConsentTemplateEntity::getVersion))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private static String key(ConsentTemplateEntity t) {
        return t.getTemplateKey() + "|" + t.getVersion() + "|" + t.getLanguage();
    }

    private Map<String, Object> toTemplateView(ConsentTemplateEntity t) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", t.getId().toString());
        m.put("tenantId", t.getTenantId().toString());
        m.put("templateKey", t.getTemplateKey());
        m.put("version", t.getVersion());
        m.put("language", t.getLanguage());
        m.put("consentType", t.getConsentType());
        m.put("requiredAssurance", t.getRequiredAssurance());
        m.put("title", t.getTitle());
        m.put("bodyMarkdown", t.getBodyMarkdown());
        m.put("allowedMethodsJson", t.getAllowedMethodsJson());
        m.put("ziboConceptCode", t.getZiboConceptCode() != null ? t.getZiboConceptCode() : "");
        m.put("remoteAllowed", t.isRemoteAllowed());
        m.put("offlineAllowed", t.isOfflineAllowed());
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getTemplate(UUID tenantId, UUID id) {
        return templateRepository
                .findByIdAndTenantId(id, tenantId)
                .or(() -> templateRepository.findByIdAndTenantId(id, TenantIds.PLATFORM))
                .map(this::toTemplateView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    }

    @Transactional
    public Map<String, Object> createConsentRequest(UUID tenantId, Map<String, Object> body) {
        var e = new ConsentRequestEntity();
        e.setTenantId(tenantId);
        e.setSubjectPatientRef(Objects.requireNonNull((String) body.get("subjectPatientRef"), "subjectPatientRef"));
        e.setConsentType(Objects.requireNonNull((String) body.get("consentType"), "consentType"));
        e.setWorkflowRef((String) body.get("workflowRef"));
        e.setEncounterRef((String) body.get("encounterRef"));
        if (body.get("templateId") != null) {
            UUID tid = UUID.fromString(body.get("templateId").toString());
            e.setTemplateId(tid);
            ConsentTemplateEntity tpl =
                    templateRepository.findByIdAndTenantId(tid, tenantId).or(() -> templateRepository.findByIdAndTenantId(tid, TenantIds.PLATFORM)).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown template"));
            e.setRequiredAssurance(tpl.getRequiredAssurance());
        } else {
            e.setRequiredAssurance(
                    body.getOrDefault("requiredAssurance", "L1_SIMPLE_DIGITAL").toString());
        }
        e.setState(ConsentRequestState.PENDING_EXPLANATION.name());
        try {
            e.setContextJson(objectMapper.writeValueAsString(body.getOrDefault("context", Map.of())));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid context");
        }
        e = requestRepository.save(e);
        return toRequestView(e);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getConsentRequest(UUID tenantId, UUID id) {
        return requestRepository
                .findByIdAndTenantId(id, tenantId)
                .map(this::toRequestView)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForPatient(UUID tenantId, String patientId) {
        return requestRepository.findByTenantIdAndSubjectPatientRefOrderByCreatedAtDesc(tenantId, patientId).stream()
                .map(this::toRequestView)
                .toList();
    }

    /**
     * EHR/Experience “consent surface” for patient summary, banner, and emergency strip — not only history list.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getConsentSummary(UUID tenantId, String patientRef) {
        List<Map<String, Object>> rows = listForPatient(tenantId, patientRef);
        if (rows.isEmpty() && patientRef != null && !patientRef.isBlank() && !patientRef.startsWith("Patient/")) {
            rows = listForPatient(tenantId, "Patient/" + patientRef);
        }
        var summary = new HashMap<String, Object>(consentSummaryDeriver.derive(rows));
        summary.put(
                "communicationPreferenceProfile", communicationPreferenceService.getSummaryForConsentSurface(tenantId, patientRef));
        summary.put("patientRef", patientRef);
        summary.put("generatedAt", Instant.now().toString());
        return summary;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listForEncounter(UUID tenantId, String encounterId) {
        return requestRepository.findByTenantIdAndEncounterRefOrderByCreatedAtDesc(tenantId, encounterId).stream()
                .map(this::toRequestView)
                .toList();
    }

    @Transactional
    public Map<String, Object> transition(
            UUID tenantId, UUID id, ConsentRequestState newState, Map<String, Object> body) {
        ConsentRequestEntity e = requestRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        String previousState = e.getState();
        e.setState(newState.name());
        if (body != null) {
            if (body.get("actualMethod") != null) {
                e.setActualMethod(body.get("actualMethod").toString());
            }
            if (body.get("actualAssurance") != null) {
                e.setActualAssurance(body.get("actualAssurance").toString());
            }
            if (body.get("proofRef") != null) {
                e.setProofRef(body.get("proofRef").toString());
            }
            if (body.get("grantedByRef") != null) {
                e.setGrantedByRef(body.get("grantedByRef").toString());
            }
            if (body.get("refusedReason") != null) {
                e.setRefusedReason(body.get("refusedReason").toString());
            }
        }
        e = requestRepository.save(e);

        if (newState == ConsentRequestState.GRANTED || newState == ConsentRequestState.PARTIALLY_GRANTED) {
            onGrantedOrPartial(tenantId, e, newState, body, previousState);
        } else if (newState == ConsentRequestState.WITHDRAWN || newState == ConsentRequestState.REFUSED) {
            onWithdrawnOrRefused(tenantId, e, newState, body, previousState);
        }

        return toRequestView(e);
    }

    private static boolean alreadyInGrantedFamily(String stateName) {
        return ConsentRequestState.GRANTED.name().equals(stateName)
                || ConsentRequestState.PARTIALLY_GRANTED.name().equals(stateName);
    }

    private static boolean upgradePartialToFull(String previous, ConsentRequestState n) {
        return ConsentRequestState.PARTIALLY_GRANTED.name().equals(previous) && n == ConsentRequestState.GRANTED;
    }

    private void onGrantedOrPartial(
            UUID tenantId,
            ConsentRequestEntity e,
            ConsentRequestState newState,
            Map<String, Object> body,
            String previousState) {
        if (e.getTshepoConsentId() == null) {
            try {
                String patientRef = e.getSubjectPatientRef();
                String grantor = e.getGrantedByRef() != null && !e.getGrantedByRef().isBlank()
                        ? e.getGrantedByRef()
                        : patientRef;
                if (body != null && body.get("grantedByRef") != null) {
                    grantor = body.get("grantedByRef").toString();
                }
                String fhirJson = FhirMvumoConsentBuilder.buildJson(
                        patientRef,
                        grantor,
                        e.getId().toString(),
                        e.getConsentType(),
                        Instant.now());
                var create = buildTshepoCreateMap(e, fhirJson);
                UUID tshepoId = tshepoConsentClient.createDirective(create);
                if (tshepoId != null) {
                    e.setTshepoConsentId(tshepoId);
                    e.setProofRef("tshepo-consent:" + tshepoId);
                    requestRepository.save(e);
                }
            } catch (Exception ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to create consent directive in tshepo-consent-service: " + ex.getMessage());
            }
        }
        boolean emit =
                !alreadyInGrantedFamily(previousState) || upgradePartialToFull(previousState, newState);
        if (emit) {
            appendEvent(tenantId, e, "CONSENT_" + newState.name(), body);
            enqueueOutbox(
                    e,
                    newState == ConsentRequestState.GRANTED
                            ? "impilo.mvumo.consent.granted.v1"
                            : "impilo.mvumo.consent.partially_granted.v1");
        }
    }

    private void onWithdrawnOrRefused(
            UUID tenantId,
            ConsentRequestEntity e,
            ConsentRequestState newState,
            Map<String, Object> body,
            String previousState) {
        if (e.getTshepoConsentId() == null) {
            appendEvent(tenantId, e, "CONSENT_" + newState.name(), body);
            enqueueOutbox(e, "impilo.mvumo.consent." + newState.name().toLowerCase() + ".v1");
            return;
        }
        if (alreadyTerminalNegative(previousState)) {
            return;
        }
        if (e.getTshepoConsentId() != null && tshepoConsentClient.isEnabled()) {
            try {
                String reason = e.getRefusedReason() != null ? e.getRefusedReason() : newState.name();
                if (body != null && body.get("refusedReason") != null) {
                    reason = body.get("refusedReason").toString();
                } else if (body != null && body.get("reason") != null) {
                    reason = body.get("reason").toString();
                }
                tshepoConsentClient.revokeDirective(e.getTshepoConsentId(), reason);
            } catch (Exception ex) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "Failed to revoke consent in tshepo-consent-service: " + ex.getMessage());
            }
        }
        appendEvent(tenantId, e, "CONSENT_" + newState.name(), body);
        enqueueOutbox(e, "impilo.mvumo.consent." + newState.name().toLowerCase() + ".v1");
    }

    private static boolean alreadyTerminalNegative(String previousState) {
        return ConsentRequestState.REFUSED.name().equals(previousState)
                || ConsentRequestState.WITHDRAWN.name().equals(previousState);
    }

    private void appendEvent(UUID tenantId, ConsentRequestEntity e, String type, Map<String, Object> body) {
        var ev = new ConsentEventEntity();
        ev.setTenantId(tenantId);
        ev.setConsentId(e.getId());
        ev.setEventType(type);
        ev.setActorRef(e.getGrantedByRef() != null ? e.getGrantedByRef() : "system");
        try {
            ev.setDetail(objectMapper.writeValueAsString(
                    body != null ? body : Map.of("state", e.getState(), "tshepoConsentId", str(e.getTshepoConsentId()))));
        } catch (JsonProcessingException ex) {
            ev.setDetail("{}");
        }
        consentEventRepository.save(ev);
    }

    private void enqueueOutbox(ConsentRequestEntity e, String eventType) {
        var row = new EventOutboxEntity();
        row.setAggregateType("ConsentRequest");
        row.setAggregateId(e.getId().toString());
        row.setEventType(eventType);
        try {
            row.setPayload(objectMapper.writeValueAsString(
                    Map.of("consentRequestId", e.getId().toString(), "state", e.getState(), "tshepoConsentId", str(e.getTshepoConsentId()))));
        } catch (JsonProcessingException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "outbox payload");
        }
        eventOutboxRepository.save(row);
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private Map<String, Object> buildTshepoCreateMap(ConsentRequestEntity e, String fhirJson) {
        var m = new HashMap<String, Object>();
        m.put("tenantId", e.getTenantId());
        m.put("patientRef", e.getSubjectPatientRef());
        m.put("grantorRef", e.getGrantedByRef() != null && !e.getGrantedByRef().isBlank()
                ? e.getGrantedByRef()
                : e.getSubjectPatientRef());
        m.put("granteeRef", "");
        m.put("scope", "clinical-data");
        m.put("purpose", "TREATMENT");
        m.put("provision", "permit");
        m.put("fhirConsentJson", fhirJson);
        return m;
    }

    @Transactional
    public Map<String, Object> createRemoteSession(UUID tenantId, Map<String, Object> body) {
        UUID requestId = UUID.fromString(body.get("consentRequestId").toString());
        ConsentRequestEntity req = requestRepository
                .findByIdAndTenantId(requestId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        var s = new RemoteConsentSessionEntity();
        s.setTenantId(tenantId);
        s.setConsentRequestId(req.getId());
        s.setChannel(Optional.ofNullable((String) body.get("channel")).orElse("TOKEN_LINK"));
        s.setState("OPEN");
        String raw = UUID.randomUUID().toString() + UUID.randomUUID();
        s.setTokenHash(sha256Hex(raw));
        int ttl = 20;
        if (body.get("ttlMinutes") instanceof Number n) {
            ttl = n.intValue();
        }
        s.setExpiresAt(Instant.now().plus(ttl, ChronoUnit.MINUTES));
        s = sessionRepository.save(s);
        if (tokenRegistry != null) {
            tokenRegistry.register(raw, tenantId, s.getId(), req.getId(), ttl);
        }
        return Map.of(
                "sessionId", s.getId().toString(),
                "consentRequestId", req.getId().toString(),
                "token", raw,
                "tokenHashPreview", s.getTokenHash().substring(0, 12) + "…",
                "expiresAt", s.getExpiresAt().toString(),
                "channel", s.getChannel());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getRemoteSession(UUID tenantId, UUID id) {
        var s = sessionRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        return Map.of(
                "id", s.getId().toString(),
                "consentRequestId", s.getConsentRequestId().toString(),
                "state", s.getState(),
                "channel", s.getChannel(),
                "expiresAt", s.getExpiresAt().toString());
    }

    public Map<String, Object> evaluateRequirements(ConsentRequirementEngine.RequirementEvaluationRequest req) {
        var r = requirementEngine.evaluate(req);
        return Map.of(
                "consentRequired", r.consentRequired(),
                "minimumAssurance", r.minimumAssurance().name(),
                "acceptableMethodCategories", r.acceptableMethodCategories(),
                "guardianRequired", r.guardianRequired(),
                "witnessRequired", r.witnessRequired(),
                "interpreterRequired", r.interpreterRequired(),
                "remoteAllowed", r.remoteAllowed(),
                "offlineAllowed", r.offlineAllowed(),
                "nextAction", r.nextAction(),
                "rationale", r.rationale());
    }

    public Map<String, Object> evaluateConsentDecisionStub(Map<String, Object> body) {
        return Map.of(
                "valid", false,
                "detail", "Stub: delegate to tshepo-consent-service /v1/consent/evaluate for enforcement decisions.");
    }

    @Transactional
    public Map<String, Object> offlineSyncBatch(Map<String, Object> body) {
        return Map.of("accepted", 0, "rejected", 0, "note", "Offline sync batch — implement idempotent reconciliation in next iteration");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> auditForConsent(UUID tenantId, UUID consentId) {
        requestRepository.findByIdAndTenantId(consentId, tenantId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        return consentEventRepository.findByTenantIdAndConsentIdOrderByCreatedAtDesc(tenantId, consentId).stream()
                .map(
                        ev -> {
                            Map<String, Object> m = new HashMap<>();
                            m.put("id", ev.getId());
                            m.put("eventType", ev.getEventType());
                            m.put("actorRef", ev.getActorRef());
                            m.put("detail", ev.getDetail() != null ? ev.getDetail() : "{}");
                            m.put("createdAt", ev.getCreatedAt().toString());
                            return m;
                        })
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProof(UUID tenantId, UUID id) {
        var e = requestRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        return Map.of("consentRequestId", e.getId().toString(), "proofRef", e.getProofRef() != null ? e.getProofRef() : "", "state", e.getState());
    }

    private Map<String, Object> toRequestView(ConsentRequestEntity e) {
        return new HashMap<>(
                Map.ofEntries(
                        Map.entry("id", e.getId().toString()),
                        Map.entry("tenantId", e.getTenantId().toString()),
                        Map.entry("state", e.getState()),
                        Map.entry("subjectPatientRef", e.getSubjectPatientRef()),
                        Map.entry("consentType", e.getConsentType()),
                        Map.entry("templateId", e.getTemplateId() != null ? e.getTemplateId().toString() : ""),
                        Map.entry("requiredAssurance", e.getRequiredAssurance()),
                        Map.entry("contextJson", e.getContextJson()),
                        Map.entry("workflowRef", e.getWorkflowRef() != null ? e.getWorkflowRef() : ""),
                        Map.entry("encounterRef", e.getEncounterRef() != null ? e.getEncounterRef() : ""),
                        Map.entry("tshepoConsentId", e.getTshepoConsentId() != null ? e.getTshepoConsentId().toString() : ""),
                        Map.entry("proofRef", e.getProofRef() != null ? e.getProofRef() : ""),
                        Map.entry("refusedReason", e.getRefusedReason() != null ? e.getRefusedReason() : ""),
                        Map.entry("expiresAt", e.getExpiresAt() != null ? e.getExpiresAt().toString() : ""),
                        Map.entry("createdAt", e.getCreatedAt().toString()),
                        Map.entry("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : "")));
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
