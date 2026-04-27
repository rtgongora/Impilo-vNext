package zw.gov.mohcc.impilo.mvumo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.mvumo.persistence.CommunicationPreferenceCurrentEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.CommunicationPreferenceCurrentRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.CommunicationPreferenceRevisionEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.CommunicationPreferenceRevisionRepository;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxEntity;
import zw.gov.mohcc.impilo.mvumo.persistence.EventOutboxRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Versioned patient communication / notification / follow-up preferences.
 * Every change is a new {@link CommunicationPreferenceRevisionEntity}; history is never overwritten.
 */
@Service
public class CommunicationPreferenceService {

    public static final String STATE_ACTIVE = "active";
    public static final String STATE_DRAFT = "draft";
    public static final String STATE_OPTED_IN = "opted_in";
    public static final String STATE_OPTED_OUT = "opted_out";
    public static final String STATE_WITHDRAWN = "withdrawn";
    public static final String STATE_LIMITED = "limited";
    public static final String STATE_EMERGENCY_ONLY = "emergency_only";
    public static final String STATE_PENDING_VERIFICATION = "pending_verification";
    public static final String STATE_REQUIRES_REVIEW = "requires_review";
    public static final String STATE_OFFLINE_PENDING_SYNC = "offline_pending_sync";
    public static final String STATE_SYNC_CONFLICT = "sync_conflict";
    public static final String STATE_SUPERSEDED = "superseded";
    public static final String STATE_INACTIVE = "inactive";

    private final CommunicationPreferenceRevisionRepository revisionRepository;
    private final CommunicationPreferenceCurrentRepository currentRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    public CommunicationPreferenceService(
            CommunicationPreferenceRevisionRepository revisionRepository,
            CommunicationPreferenceCurrentRepository currentRepository,
            EventOutboxRepository eventOutboxRepository,
            ObjectMapper objectMapper) {
        this.revisionRepository = revisionRepository;
        this.currentRepository = currentRepository;
        this.eventOutboxRepository = eventOutboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getCurrent(UUID tenantId, String patientRef) {
        var cur = currentRepository.findByTenantIdAndPatientRef(tenantId, normalizeRef(patientRef));
        if (cur.isEmpty()) {
            return Map.of("patientRef", normalizeRef(patientRef), "exists", false);
        }
        var rev = revisionRepository.findById(cur.get().getCurrentRevisionId()).orElseThrow();
        return toView(rev, true);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getHistory(UUID tenantId, String patientRef, int limit) {
        String ref = normalizeRef(patientRef);
        var rows = revisionRepository.findByTenantIdAndPatientRefOrderByBundleVersionDesc(tenantId, ref);
        List<Map<String, Object>> out = new ArrayList<>();
        int i = 0;
        for (CommunicationPreferenceRevisionEntity r : rows) {
            if (i++ >= limit) {
                break;
            }
            out.add(toView(r, true));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> put(
            UUID tenantId,
            String patientRef,
            Map<String, Object> body,
            String actorRef,
            String actorRelationship,
            String sourceChannel,
            String assuranceMethod,
            String outboxEventTypeOrNull) {
        String ref = normalizeRef(patientRef);
        if (ref.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "patientRef required");
        }
        JsonNode payload = objectMapper.valueToTree(body != null ? body : Map.of());
        cleanMetaKeys((ObjectNode) payload);
        if (!payload.has("schemaVersion")) {
            ((ObjectNode) payload).put("schemaVersion", 1);
        }
        Integer ifMatch = null;
        if (body != null && body.get("ifMatchBundleVersion") instanceof Number n) {
            ifMatch = n.intValue();
        }
        Optional<CommunicationPreferenceCurrentEntity> cur = currentRepository.findByTenantIdAndPatientRef(tenantId, ref);
        int nextVersion = revisionRepository.maxBundleVersion(tenantId, ref) + 1;
        UUID previousId = null;
        if (cur.isPresent()) {
            var latest = revisionRepository
                    .findById(cur.get().getCurrentRevisionId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "dangling current pointer"));
            previousId = latest.getId();
            if (ifMatch != null && ifMatch != latest.getBundleVersion()) {
                throw new ResponseStatusException(
                        HttpStatus.PRECONDITION_FAILED, "ifMatchBundleVersion mismatch — refresh and retry");
            }
        }
        String lifecycle = STATE_ACTIVE;
        if (body != null && body.get("lifecycleOverride") instanceof String s && !s.isBlank()) {
            lifecycle = s;
        } else if (body != null && body.get("lifecycleState") instanceof String s && !s.isBlank()) {
            lifecycle = s;
        }

        var e = new CommunicationPreferenceRevisionEntity();
        e.setTenantId(tenantId);
        e.setPatientRef(ref);
        e.setBundleVersion(nextVersion);
        e.setLifecycleState(lifecycle);
        try {
            e.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid JSON payload");
        }
        e.setPreviousRevisionId(previousId);
        e.setActorRef(actorRef);
        e.setActorRelationship(actorRelationship != null ? actorRelationship : "unknown");
        e.setSourceChannel(sourceChannel != null ? sourceChannel : "api");
        e.setReasonText(text(body, "reason"));
        e.setAssuranceMethod(assuranceMethod != null ? assuranceMethod : text(body, "assuranceMethod"));
        e.setDeviceMetadata(jsonOrEmpty(body, "deviceMetadata"));
        e.setAuditRef("MPR-" + UUID.randomUUID().toString().substring(0, 12));
        e.setEffectiveAt(parseInstant(body, "effectiveAt", Instant.now()));
        e.setExpiresAt(parseInstantOrNull(body, "expiresAt"));
        e.setReviewDueAt(parseInstantOrNull(body, "reviewDueAt"));
        e = revisionRepository.save(e);
        upsertCurrent(tenantId, ref, e.getId());
        String evt =
                outboxEventTypeOrNull != null
                        ? outboxEventTypeOrNull
                        : "impilo.mvumo.communication_preference.updated.v1";
        enqueuePreferenceEvent(tenantId, e, evt);
        return toView(e, true);
    }

    /** Replace full preference bundle; emits {@code impilo.mvumo.communication_preference.updated.v1} by default. */
    @Transactional
    public Map<String, Object> put(
            UUID tenantId,
            String patientRef,
            Map<String, Object> body,
            String actorRef,
            String actorRelationship,
            String sourceChannel,
            String assuranceMethod) {
        return put(
                tenantId, patientRef, body, actorRef, actorRelationship, sourceChannel, assuranceMethod, null);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> patch(
            UUID tenantId,
            String patientRef,
            UUID revisionId,
            Map<String, Object> body,
            String actorRef,
            String sourceChannel) {
        String ref = normalizeRef(patientRef);
        var currentEnt = currentRepository
                .findByTenantIdAndPatientRef(tenantId, ref)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No preferences"));
        if (!currentEnt.getCurrentRevisionId().equals(revisionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "revision is not current — use GET then PUT");
        }
        var latest = revisionRepository
                .findByIdAndTenantId(revisionId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revision"));
        JsonNode base;
        try {
            base = objectMapper.readTree(latest.getPayloadJson());
        } catch (Exception e) {
            base = objectMapper.createObjectNode();
        }
        Map<String, Object> patchMap = objectMapper.convertValue(
                objectMapper.valueToTree(body != null ? body : Map.of()), Map.class);
        // Drop meta-only fields from merge target payload
        patchMap.remove("ifMatchBundleVersion");
        patchMap.remove("lifecycleOverride");
        JsonNode patch = objectMapper.valueToTree(patchMap);
        JsonNode merged = deepMerge(base, patch);
        Map<String, Object> putBody = new HashMap<>(objectMapper.convertValue(merged, Map.class));
        putBody.put("ifMatchBundleVersion", latest.getBundleVersion());
        return put(
                tenantId,
                patientRef,
                putBody,
                actorRef,
                "self",
                sourceChannel != null ? sourceChannel : "patch",
                null,
                null);
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> withdraw(
            UUID tenantId, String patientRef, UUID revisionId, Map<String, Object> body, String actorRef, String channel) {
        var currentEnt = currentRepository
                .findByTenantIdAndPatientRef(tenantId, normalizeRef(patientRef))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No preferences"));
        if (!currentEnt.getCurrentRevisionId().equals(revisionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "not current revision");
        }
        var latest = revisionRepository
                .findById(revisionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revision"));
        Map<String, Object> m = new HashMap<>();
        try {
            m.putAll(objectMapper.readValue(latest.getPayloadJson(), Map.class));
        } catch (Exception e) {
            m = new HashMap<>();
        }
        m.put("withdrawal", Map.of("at", Instant.now().toString(), "reason", text(body, "reason")));
        m.put("ifMatchBundleVersion", latest.getBundleVersion());
        m.put("lifecycleOverride", STATE_WITHDRAWN);
        return put(
                tenantId,
                patientRef,
                m,
                actorRef,
                "subject",
                channel != null ? channel : "withdraw",
                text(body, "assuranceMethod"),
                "impilo.mvumo.communication_preference.withdrawn.v1");
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> reenable(
            UUID tenantId, String patientRef, UUID revisionId, Map<String, Object> body, String actorRef, String channel) {
        var currentEnt = currentRepository
                .findByTenantIdAndPatientRef(tenantId, normalizeRef(patientRef))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No preferences"));
        if (!currentEnt.getCurrentRevisionId().equals(revisionId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "not current revision");
        }
        var latest = revisionRepository
                .findByIdAndTenantId(revisionId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "revision"));
        Map<String, Object> m = new HashMap<>();
        try {
            m.putAll(objectMapper.readValue(latest.getPayloadJson(), Map.class));
        } catch (Exception e) {
            m = new HashMap<>();
        }
        m.remove("withdrawal");
        m.put("ifMatchBundleVersion", latest.getBundleVersion());
        m.put("lifecycleOverride", STATE_OPTED_IN);
        String rel = "subject";
        if (body != null && body.get("actorRelationship") != null) {
            rel = body.get("actorRelationship").toString();
        }
        return put(
                tenantId,
                patientRef,
                m,
                actorRef,
                rel,
                channel != null ? channel : "reenable",
                text(body, "assuranceMethod"),
                "impilo.mvumo.communication_preference.reenabled.v1");
    }

    /**
     * Policy gate for outbound comms / notifications. Load current revision (if any) and apply lifecycle + payload
     * channel matrix. Callers should pass {@code tenantId} (set by API from {@code X-Tenant-ID} when missing).
     */
    @Transactional(readOnly = true)
    public Map<String, Object> evaluate(Map<String, Object> body) {
        UUID tenantId = parseTenantFromBody(body);
        String patientRef = normalizeRef(str(body.get("patientRef")));
        String proposedRaw = str(body.get("proposedChannel"));
        String proposed = normalizeEvaluateChannel(proposedRaw);
        boolean emergency = Boolean.TRUE.equals(body.get("emergencyOverride"));
        String messageKind = str(body.get("messageKind"));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("patientRef", patientRef);
        res.put("proposedChannel", proposedRaw);
        res.put("normalizedChannel", proposed);

        if (patientRef.isEmpty()) {
            res.put("allowed", false);
            res.put("requiresReview", true);
            res.put("useGenericWording", true);
            res.put("rationale", "patientRef is required for preference evaluation");
            return res;
        }

        Map<String, Object> profile = getSummaryForConsentSurface(tenantId, patientRef);
        if (!Boolean.TRUE.equals(profile.get("hasProfile"))) {
            res.put("allowed", true);
            res.put("requiresReview", false);
            res.put("useGenericWording", false);
            res.put("rationale", "No profile — allow (default) until the client registers preferences");
            return res;
        }

        String lifecycle = str(profile.get("lifecycleState"));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload =
                profile.get("payload") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();

        if (STATE_REQUIRES_REVIEW.equals(lifecycle) || STATE_SYNC_CONFLICT.equals(lifecycle)) {
            res.put("allowed", emergency);
            res.put("requiresReview", !emergency);
            res.put("useGenericWording", true);
            res.put("rationale", "Preference record is in " + lifecycle + " — hold non-emergency sends");
            res.put("lifecycleState", lifecycle);
            return res;
        }

        if (STATE_WITHDRAWN.equals(lifecycle)
                || STATE_OPTED_OUT.equals(lifecycle)
                || STATE_INACTIVE.equals(lifecycle)) {
            res.put("allowed", emergency);
            res.put("requiresReview", !emergency);
            res.put("useGenericWording", true);
            res.put("rationale", "Lifecycle " + lifecycle + " — only emergency override may allow");
            res.put("lifecycleState", lifecycle);
            return res;
        }

        boolean channelAllowed = isChannelAllowedInPayload(payload, proposed, messageKind);
        boolean sensitive = "SENSITIVE".equalsIgnoreCase(messageKind) || "CLINICAL_DETAIL".equalsIgnoreCase(messageKind);
        boolean genericWording = false;
        Object sc = payload.get("sensitiveCommunication");
        if (sensitive && sc instanceof Map<?, ?> scm) {
            genericWording = truthy(safeStringMap(scm), "useGenericWording");
        }

        res.put("allowed", channelAllowed);
        res.put("requiresReview", !channelAllowed && !emergency);
        res.put("useGenericWording", genericWording || !channelAllowed);
        res.put("rationale", channelAllowed ? "Channel allowed by current profile" : "Channel disabled in patient channels map");
        res.put("lifecycleState", lifecycle);
        res.put("bundleVersion", profile.get("bundleVersion"));
        return res;
    }

    private static UUID parseTenantFromBody(Map<String, Object> body) {
        if (body != null && body.get("tenantId") != null) {
            try {
                return UUID.fromString(body.get("tenantId").toString());
            } catch (Exception ignored) {
                return TenantIds.PLATFORM;
            }
        }
        return TenantIds.PLATFORM;
    }

    /** Normalise notification / comms channel hints to payload keys under {@code channels.*}. */
    private static String normalizeEvaluateChannel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        String s = raw.trim().toLowerCase();
        if (s.contains("sms") || s.equals("text")) {
            return "sms";
        }
        if (s.contains("email") || s.contains("mail")) {
            return "email";
        }
        if (s.contains("push") || s.equals("app")) {
            return "app_push";
        }
        if (s.contains("phone") || s.contains("voice") || s.contains("call")) {
            return "phone";
        }
        if (s.contains("ussd")) {
            return "ussd";
        }
        if (s.contains("letter") || s.contains("post")) {
            return "letter";
        }
        if (s.contains("chw") || s.contains("community")) {
            return "chw";
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private static boolean isChannelAllowedInPayload(
            Map<String, Object> payload, String normalizedChannel, String messageKind) {
        if (payload == null) {
            return true;
        }
        Object chRoot = payload.get("channels");
        if (!(chRoot instanceof Map<?, ?> m) || m.isEmpty()) {
            return true;
        }
        Object v = ((Map<String, Object>) m).get(normalizedChannel);
        if (v == null) {
            return true;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Map<?, ?> mm) {
            return Boolean.TRUE.equals(mm.get("enabled"));
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> safeStringMap(Map<?, ?> m) {
        return (Map<String, Object>) m;
    }

    private static boolean truthy(Map<String, Object> m, String key) {
        if (m == null) {
            return false;
        }
        Object o = m.get(key);
        return Boolean.TRUE.equals(o) || "true".equalsIgnoreCase(String.valueOf(o));
    }

    @SuppressWarnings("unchecked")
    @Transactional
    public Map<String, Object> offlineSync(Map<String, Object> body) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        if (items == null || items.isEmpty()) {
            return Map.of("accepted", 0, "rejected", 0, "conflicts", List.of(), "results", List.of());
        }
        List<Map<String, Object>> conflicts = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();
        int accepted = 0;
        for (Map<String, Object> it : items) {
            String patientRef = normalizeRef(str(it.get("patientRef")));
            if (patientRef.isEmpty()) {
                conflicts.add(Map.of("code", "missing_patient_ref", "item", it));
                continue;
            }
            UUID tenantId = TenantIds.PLATFORM;
            if (it.get("tenantId") != null) {
                try {
                    tenantId = UUID.fromString(it.get("tenantId").toString());
                } catch (Exception ignored) {
                    // keep platform
                }
            }
            Integer clientBase = null;
            if (it.get("baseBundleVersion") instanceof Number n) {
                clientBase = n.intValue();
            }
            int serverMax = revisionRepository.maxBundleVersion(tenantId, patientRef);
            if (clientBase != null && clientBase < serverMax) {
                conflicts.add(new LinkedHashMap<>(
                        Map.of(
                                "code",
                                "sync_conflict",
                                "patientRef",
                                patientRef,
                                "serverBundleVersion",
                                serverMax,
                                "clientBaseBundleVersion",
                                clientBase)));
                continue;
            }
            Object payloadObj = it.get("payload");
            if (!(payloadObj instanceof Map)) {
                conflicts.add(Map.of("code", "missing_payload", "patientRef", patientRef));
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = new LinkedHashMap<>((Map<String, Object>) payloadObj);
            payload.put("ifMatchBundleVersion", clientBase != null ? clientBase : serverMax);
            payload.put("sourceChannel", str(it.get("sourceChannel")) != null ? str(it.get("sourceChannel")) : "offline_sync");
            try {
                Map<String, Object> saved =
                        put(
                                tenantId,
                                patientRef,
                                payload,
                                str(it.get("actorRef")) != null ? str(it.get("actorRef")) : "offline-client",
                                str(it.get("actorRelationship")) != null ? str(it.get("actorRelationship")) : "self",
                                str(it.get("sourceChannel")) != null ? str(it.get("sourceChannel")) : "offline_sync",
                                str(it.get("assuranceMethod")),
                                "impilo.mvumo.communication_preference.offline_applied.v1");
                accepted++;
                results.add(Map.of("patientRef", patientRef, "revisionId", saved.get("revisionId"), "ok", true));
            } catch (ResponseStatusException ex) {
                conflicts.add(Map.of("code", "apply_failed", "patientRef", patientRef, "status", "" + ex.getStatusCode()));
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("accepted", accepted);
        out.put("rejected", conflicts.size());
        out.put("conflicts", conflicts);
        out.put("results", results);
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSummaryForConsentSurface(UUID tenantId, String patientRef) {
        var cur = currentRepository.findByTenantIdAndPatientRef(tenantId, normalizeRef(patientRef));
        if (cur.isEmpty() && patientRef != null && !patientRef.isBlank() && !patientRef.startsWith("Patient/")) {
            cur = currentRepository.findByTenantIdAndPatientRef(tenantId, "Patient/" + patientRef);
        }
        if (cur.isEmpty()) {
            return Map.of("hasProfile", false);
        }
        var rev = revisionRepository.findById(cur.get().getCurrentRevisionId()).orElse(null);
        if (rev == null) {
            return Map.of("hasProfile", false);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hasProfile", true);
        m.put("lifecycleState", rev.getLifecycleState());
        m.put("bundleVersion", rev.getBundleVersion());
        m.put("revisionId", rev.getId().toString());
        m.put("updatedAt", rev.getCreatedAt() != null ? rev.getCreatedAt().toString() : null);
        m.put("sourceChannel", rev.getSourceChannel());
        m.put("auditRef", rev.getAuditRef());
        try {
            m.put("payload", objectMapper.readValue(rev.getPayloadJson(), Map.class));
        } catch (Exception e) {
            m.put("payload", Map.of());
        }
        return m;
    }

    private void upsertCurrent(UUID tenantId, String patientRef, UUID revisionId) {
        var c = currentRepository.findByTenantIdAndPatientRef(tenantId, patientRef).orElse(new CommunicationPreferenceCurrentEntity());
        c.setTenantId(tenantId);
        c.setPatientRef(patientRef);
        c.setCurrentRevisionId(revisionId);
        c.setUpdatedAt(Instant.now());
        currentRepository.save(c);
    }

    private void enqueuePreferenceEvent(UUID tenantId, CommunicationPreferenceRevisionEntity e, String eventType) {
        var row = new EventOutboxEntity();
        row.setAggregateType("CommunicationPreference");
        row.setAggregateId(e.getId().toString());
        row.setEventType(eventType);
        try {
            row.setPayload(objectMapper.writeValueAsString(
                    Map.of(
                            "tenantId",
                            tenantId.toString(),
                            "patientRef",
                            e.getPatientRef(),
                            "revisionId",
                            e.getId().toString(),
                            "bundleVersion",
                            e.getBundleVersion(),
                            "lifecycleState",
                            e.getLifecycleState())));
        } catch (Exception ex) {
            return;
        }
        eventOutboxRepository.save(row);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toView(CommunicationPreferenceRevisionEntity e, boolean includePayload) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revisionId", e.getId().toString());
        m.put("patientRef", e.getPatientRef());
        m.put("bundleVersion", e.getBundleVersion());
        m.put("lifecycleState", e.getLifecycleState());
        m.put("actorRef", e.getActorRef());
        m.put("actorRelationship", e.getActorRelationship());
        m.put("sourceChannel", e.getSourceChannel());
        m.put("previousRevisionId", e.getPreviousRevisionId() != null ? e.getPreviousRevisionId().toString() : null);
        m.put("effectiveAt", e.getEffectiveAt() != null ? e.getEffectiveAt().toString() : null);
        m.put("expiresAt", e.getExpiresAt() != null ? e.getExpiresAt().toString() : null);
        m.put("reviewDueAt", e.getReviewDueAt() != null ? e.getReviewDueAt().toString() : null);
        m.put("auditRef", e.getAuditRef());
        m.put("createdAt", e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        m.put("reasonText", e.getReasonText());
        m.put("assuranceMethod", e.getAssuranceMethod());
        if (includePayload) {
            try {
                m.put("payload", objectMapper.readValue(e.getPayloadJson(), Map.class));
            } catch (Exception ex) {
                m.put("payload", Map.of());
            }
        }
        m.put("exists", true);
        return m;
    }

    private void cleanMetaKeys(ObjectNode payload) {
        payload.remove("ifMatchBundleVersion");
        payload.remove("lifecycleOverride");
    }

    private static String normalizeRef(String patientRef) {
        if (patientRef == null) {
            return "";
        }
        return patientRef.trim();
    }

    private static String text(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        return body.get(key).toString();
    }

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private String jsonOrEmpty(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return "{}";
        }
        try {
            if (body.get(key) instanceof String s) {
                return s;
            }
            return objectMapper.writeValueAsString(body.get(key));
        } catch (Exception e) {
            return "{}";
        }
    }

    private static Instant parseInstant(Map<String, Object> body, String key, Instant def) {
        if (body == null || body.get(key) == null) {
            return def;
        }
        try {
            return Instant.parse(body.get(key).toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static Instant parseInstantOrNull(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        try {
            return Instant.parse(body.get(key).toString());
        } catch (Exception e) {
            return null;
        }
    }

    private JsonNode deepMerge(JsonNode base, JsonNode patch) {
        if (!base.isObject() || !patch.isObject()) {
            return patch;
        }
        ObjectNode out = ((ObjectNode) base).deepCopy();
        patch.fields()
                .forEachRemaining(
                        e -> {
                            if (e.getValue().isObject() && out.has(e.getKey()) && out.get(e.getKey()).isObject()) {
                                out.set(e.getKey(), deepMerge(out.get(e.getKey()), e.getValue()));
                            } else {
                                out.set(e.getKey(), e.getValue());
                            }
                        });
        return out;
    }
}
