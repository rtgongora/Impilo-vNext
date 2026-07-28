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
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class MvumoService {

    // Pipeline §28 demonstration 3: assent is a separate act from consent, so its own vocabulary
    // (V300's chk_consent_assent_outcome) is checked here too, not left solely to the database —
    // a caller gets a coded 400, not an opaque 500 from a CHECK violation.
    private static final Set<String> ASSENT_OUTCOMES = Set.of("GIVEN", "REFUSED", "NOT_APPLICABLE", "UNABLE");

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
    public Map<String, Object> createTemplate(UUID tenantId, Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();
        String templateKey = requireNonBlank(b, "templateKey");
        ConsentTemplateEntity entity = new ConsentTemplateEntity();
        entity.setTenantId(tenantId);
        entity.setTemplateKey(templateKey);
        entity.setLanguage(firstNonBlank(b.get("language"), "en"));
        entity.setConsentType(requireNonBlank(b, "consentType"));
        entity.setRequiredAssurance(firstNonBlank(b.get("requiredAssurance"), "L1_SIMPLE_DIGITAL"));
        entity.setTitle(requireNonBlank(b, "title"));
        entity.setBodyMarkdown(requireNonBlank(b, "bodyMarkdown"));
        entity.setAllowedMethodsJson(jsonStringOrDefault(b.get("allowedMethodsJson"), "[]"));
        entity.setZiboConceptCode(firstNonBlank(b.get("ziboConceptCode")));
        entity.setRemoteAllowed(booleanOrDefault(b.get("remoteAllowed"), true));
        entity.setOfflineAllowed(booleanOrDefault(b.get("offlineAllowed"), true));
        entity.setVersion(resolveCreateVersion(tenantId, templateKey, b.get("version")));
        return toTemplateView(templateRepository.save(entity));
    }

    @Transactional
    public Map<String, Object> updateTemplate(UUID tenantId, UUID id, Map<String, Object> body) {
        ConsentTemplateEntity current = templateRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found for tenant"));
        Map<String, Object> b = body != null ? body : Map.of();

        current.setRetiredAt(Instant.now());
        templateRepository.save(current);

        ConsentTemplateEntity next = new ConsentTemplateEntity();
        next.setTenantId(tenantId);
        next.setTemplateKey(firstNonBlank(b.get("templateKey"), current.getTemplateKey()));
        next.setVersion(current.getVersion() + 1);
        next.setLanguage(firstNonBlank(b.get("language"), current.getLanguage()));
        next.setConsentType(firstNonBlank(b.get("consentType"), current.getConsentType()));
        next.setRequiredAssurance(firstNonBlank(b.get("requiredAssurance"), current.getRequiredAssurance()));
        next.setTitle(firstNonBlank(b.get("title"), current.getTitle()));
        next.setBodyMarkdown(firstNonBlank(b.get("bodyMarkdown"), current.getBodyMarkdown()));
        next.setAllowedMethodsJson(jsonStringOrDefault(b.get("allowedMethodsJson"), current.getAllowedMethodsJson()));
        next.setZiboConceptCode(firstNonBlank(b.get("ziboConceptCode"), current.getZiboConceptCode()));
        next.setRemoteAllowed(booleanOrDefault(b.get("remoteAllowed"), current.isRemoteAllowed()));
        next.setOfflineAllowed(booleanOrDefault(b.get("offlineAllowed"), current.isOfflineAllowed()));
        if (b.get("effectiveFrom") != null && !b.get("effectiveFrom").toString().isBlank()) {
            next.setEffectiveFrom(Instant.parse(b.get("effectiveFrom").toString()));
        }
        return toTemplateView(templateRepository.save(next));
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

    /**
     * Records the decision-maker context and/or child assent for a consent request (pipeline
     * §6, demonstration 3) — a separate act from granting or refusing consent itself, so this is
     * deliberately NOT a state transition through {@link #transition}. A guardian's GRANTED
     * consent and a child's REFUSED assent must be able to coexist on the same request; routing
     * assent through the grant/refuse state machine would make one overwrite the other.
     */
    @Transactional
    public Map<String, Object> recordAssent(UUID tenantId, UUID id, Map<String, Object> body) {
        ConsentRequestEntity e = requestRepository
                .findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        if (body != null) {
            if (body.get("decisionMakerType") != null) {
                e.setDecisionMakerType(body.get("decisionMakerType").toString());
            }
            if (body.get("decisionMakerRef") != null) {
                e.setDecisionMakerRef(body.get("decisionMakerRef").toString());
            }
            if (body.get("decisionMakerBasis") != null) {
                e.setDecisionMakerBasis(body.get("decisionMakerBasis").toString());
            }
            if (body.get("assentSought") != null) {
                e.setAssentSought(Boolean.parseBoolean(body.get("assentSought").toString()));
            }
            if (body.get("assentOutcome") != null) {
                String outcome = body.get("assentOutcome").toString();
                if (!ASSENT_OUTCOMES.contains(outcome)) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "assentOutcome must be one of " + ASSENT_OUTCOMES);
                }
                e.setAssentOutcome(outcome);
            }
            if (body.get("assentNotes") != null) {
                e.setAssentNotes(body.get("assentNotes").toString());
            }
        }
        e = requestRepository.save(e);
        return toRequestView(e);
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
                        HttpStatus.SERVICE_UNAVAILABLE,
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
                        HttpStatus.SERVICE_UNAVAILABLE,
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

    @Transactional
    public Map<String, Object> remoteVerify(
            UUID tenantId,
            UUID sessionId,
            String actorRef,
            String purposeOfUse,
            String correlationId,
            Map<String, Object> body) {
        var session = sessionRepository
                .findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        ConsentRequestEntity request = requestRepository
                .findByIdAndTenantId(session.getConsentRequestId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        ensureNotExpired(session);
        String providedToken = firstNonBlank(body != null ? body.get("token") : null, body != null ? body.get("sessionToken") : null);
        if (providedToken == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token/sessionToken is required");
        }
        if (!sha256Hex(providedToken).equals(session.getTokenHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Remote session token is invalid");
        }
        session.setOpenCount(session.getOpenCount() + 1);
        session.setLastOpenAt(Instant.now());
        session.setState("VERIFIED");
        sessionRepository.save(session);
        Map<String, Object> event = trustEventBody(actorRef, purposeOfUse, correlationId, body, "remote-verify");
        appendEvent(tenantId, request, "REMOTE_SESSION_VERIFIED", event);
        return remoteSessionView(session, request);
    }

    @Transactional
    public Map<String, Object> remoteGrant(
            UUID tenantId,
            UUID sessionId,
            String actorRef,
            String purposeOfUse,
            String correlationId,
            Map<String, Object> body) {
        var session = requiredVerifiedSession(tenantId, sessionId);
        Map<String, Object> command = new HashMap<>(body != null ? body : Map.of());
        command.putIfAbsent("grantedByRef", actorRef);
        command.putIfAbsent("reason", firstNonBlank(body != null ? body.get("reason") : null, "remote-session-grant"));
        ConsentRequestEntity request = requestRepository
                .findByIdAndTenantId(session.getConsentRequestId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        transition(tenantId, request.getId(), ConsentRequestState.GRANTED, command);
        session.setState("GRANTED");
        session.setLastOpenAt(Instant.now());
        sessionRepository.save(session);
        appendEvent(tenantId, request, "REMOTE_SESSION_GRANTED", trustEventBody(actorRef, purposeOfUse, correlationId, body, "remote-grant"));
        return remoteSessionView(session, request);
    }

    @Transactional
    public Map<String, Object> remoteRefuse(
            UUID tenantId,
            UUID sessionId,
            String actorRef,
            String purposeOfUse,
            String correlationId,
            Map<String, Object> body) {
        var session = requiredVerifiedSession(tenantId, sessionId);
        Map<String, Object> command = new HashMap<>(body != null ? body : Map.of());
        command.putIfAbsent("reason", firstNonBlank(body != null ? body.get("reason") : null, "remote-session-refuse"));
        command.putIfAbsent("refusedReason", firstNonBlank(body != null ? body.get("refusedReason") : null, "remote-session-refuse"));
        ConsentRequestEntity request = requestRepository
                .findByIdAndTenantId(session.getConsentRequestId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        transition(tenantId, request.getId(), ConsentRequestState.REFUSED, command);
        session.setState("REFUSED");
        session.setLastOpenAt(Instant.now());
        sessionRepository.save(session);
        appendEvent(tenantId, request, "REMOTE_SESSION_REFUSED", trustEventBody(actorRef, purposeOfUse, correlationId, body, "remote-refuse"));
        return remoteSessionView(session, request);
    }

    @Transactional
    public Map<String, Object> remoteWithdraw(
            UUID tenantId,
            UUID sessionId,
            String actorRef,
            String purposeOfUse,
            String correlationId,
            Map<String, Object> body) {
        var session = requiredVerifiedSession(tenantId, sessionId);
        Map<String, Object> command = new HashMap<>(body != null ? body : Map.of());
        command.putIfAbsent("reason", firstNonBlank(body != null ? body.get("reason") : null, "remote-session-withdraw"));
        ConsentRequestEntity request = requestRepository
                .findByIdAndTenantId(session.getConsentRequestId(), tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Consent request not found"));
        transition(tenantId, request.getId(), ConsentRequestState.WITHDRAWN, command);
        session.setState("WITHDRAWN");
        session.setLastOpenAt(Instant.now());
        sessionRepository.save(session);
        appendEvent(tenantId, request, "REMOTE_SESSION_WITHDRAWN", trustEventBody(actorRef, purposeOfUse, correlationId, body, "remote-withdraw"));
        return remoteSessionView(session, request);
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

    public Map<String, Object> evaluateConsentDecision(Map<String, Object> body) {
        Map<String, Object> b = body != null ? body : Map.of();

        Object tenantRaw = b.get("tenantId");
        UUID tenantId;
        if (tenantRaw == null || tenantRaw.toString().isBlank()) {
            tenantId = TenantIds.PLATFORM;
        } else {
            tenantId = UUID.fromString(tenantRaw.toString());
        }

        String actorId = firstNonBlank(
                b.get("actorId"),
                b.get("actorRef"),
                b.get("actor_id"));
        String subjectRef = firstNonBlank(
                b.get("subjectRef"),
                b.get("subjectPatientRef"),
                b.get("patientRef"),
                b.get("subject_ref"));
        String purpose = firstNonBlank(
                b.get("purpose"),
                b.get("purposeOfUse"),
                b.get("purpose_of_use"));
        String scope = firstNonBlank(
                b.get("scope"),
                b.get("resourceScope"),
                b.get("resource_scope"));

        if (actorId == null || subjectRef == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "actorId/actorRef and subjectRef/subjectPatientRef are required");
        }

        if (purpose == null) {
            purpose = "TREATMENT";
        }
        if (scope == null) {
            scope = "clinical-data";
        }

        try {
            return tshepoConsentClient.evaluateDirective(tenantId, actorId, subjectRef, purpose, scope);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Consent evaluation delegation failed: " + ex.getMessage());
        }
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
                        Map.entry("updatedAt", e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : ""),
                        // §28 demonstration 3 — assent surfaced distinctly from consent state above.
                        Map.entry("decisionMakerType", e.getDecisionMakerType() != null ? e.getDecisionMakerType() : ""),
                        Map.entry("decisionMakerRef", e.getDecisionMakerRef() != null ? e.getDecisionMakerRef() : ""),
                        Map.entry("decisionMakerBasis", e.getDecisionMakerBasis() != null ? e.getDecisionMakerBasis() : ""),
                        Map.entry("assentSought", e.getAssentSought() != null ? e.getAssentSought() : false),
                        Map.entry("assentOutcome", e.getAssentOutcome() != null ? e.getAssentOutcome() : ""),
                        Map.entry("assentNotes", e.getAssentNotes() != null ? e.getAssentNotes() : "")));
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

    private static String firstNonBlank(Object... values) {
        for (Object v : values) {
            if (v == null) {
                continue;
            }
            String s = v.toString().trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
    }

    private int resolveCreateVersion(UUID tenantId, String templateKey, Object rawVersion) {
        if (rawVersion instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return templateRepository
                .findTopByTenantIdAndTemplateKeyOrderByVersionDesc(tenantId, templateKey)
                .map(ConsentTemplateEntity::getVersion)
                .orElse(0) + 1;
    }

    private static String requireNonBlank(Map<String, Object> body, String field) {
        String value = firstNonBlank(body.get(field));
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required");
        }
        return value;
    }

    private static boolean booleanOrDefault(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(raw.toString());
    }

    private static String jsonStringOrDefault(Object raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        String value = raw.toString().trim();
        return value.isEmpty() ? fallback : value;
    }

    private RemoteConsentSessionEntity requiredVerifiedSession(UUID tenantId, UUID sessionId) {
        RemoteConsentSessionEntity session = sessionRepository
                .findByIdAndTenantId(sessionId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        ensureNotExpired(session);
        if (!"VERIFIED".equalsIgnoreCase(session.getState())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Session must be VERIFIED before action");
        }
        return session;
    }

    private static void ensureNotExpired(RemoteConsentSessionEntity session) {
        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Remote session expired");
        }
    }

    private Map<String, Object> remoteSessionView(RemoteConsentSessionEntity session, ConsentRequestEntity request) {
        return Map.of(
                "sessionId", session.getId().toString(),
                "consentRequestId", request.getId().toString(),
                "sessionState", session.getState(),
                "consentState", request.getState(),
                "channel", session.getChannel(),
                "expiresAt", session.getExpiresAt().toString());
    }

    private static Map<String, Object> trustEventBody(
            String actorRef,
            String purposeOfUse,
            String correlationId,
            Map<String, Object> body,
            String operation) {
        Map<String, Object> event = new HashMap<>();
        event.put("operation", operation);
        event.put("actorRef", actorRef);
        event.put("purposeOfUse", purposeOfUse);
        event.put("correlationId", correlationId);
        if (body != null && !body.isEmpty()) {
            event.put("payload", body);
        }
        return event;
    }
}
