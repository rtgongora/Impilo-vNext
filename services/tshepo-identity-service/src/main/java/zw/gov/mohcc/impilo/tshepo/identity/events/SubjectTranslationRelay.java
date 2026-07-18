package zw.gov.mohcc.impilo.tshepo.identity.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.tshepo.identity.core.IdResolutionService;
import zw.gov.mohcc.impilo.tshepo.identity.persistence.entity.IdMappingEntity;

import java.util.Locale;
import java.util.UUID;

/**
 * The HID→CPID translation choke point between the identity plane and the
 * clinical plane (Identity Contract §7.3).
 *
 * <p>VITO keeps emitting HID-keyed events on its own topics
 * ({@code vito.identity}/{@code impilo.vito.identity},
 * {@code vito.dedup}/{@code impilo.vito.dedup}) — those are identity-plane
 * streams. This relay consumes them, resolves the Health ID to a CPID via the
 * {@code id_mapping} table, strips the Health ID and every other field, and
 * re-emits allowlisted clinical-plane events on {@code impilo.identity.subject}.
 * Clinical services subscribe ONLY to the subject topic.</p>
 *
 * <p>This is deliberately the only component in the estate that sees a Health ID
 * and a CPID at the same time. VITO never learns CPIDs; clinical services never
 * see Health IDs.</p>
 *
 * <p>Merge handling additionally maintains the mapping lifecycle: the merged
 * identity's mapping is RETIRED with a redirect to the survivor CPID (unmerge
 * reverses it), so late resolutions of a merged Health ID land on the survivor.</p>
 */
@Component
public class SubjectTranslationRelay {

    private static final Logger log = LoggerFactory.getLogger(SubjectTranslationRelay.class);

    private final ObjectMapper objectMapper;
    private final IdResolutionService idResolutionService;
    private final SubjectEventPublisher subjectEvents;

    public SubjectTranslationRelay(ObjectMapper objectMapper,
                                   IdResolutionService idResolutionService,
                                   SubjectEventPublisher subjectEvents) {
        this.objectMapper = objectMapper;
        this.idResolutionService = idResolutionService;
        this.subjectEvents = subjectEvents;
    }

    /**
     * Identity lifecycle stream: created / verified / deceased.
     */
    @KafkaListener(
            topics = {"vito.identity", "impilo.vito.identity"},
            groupId = "tshepo-identity-subject-relay"
    )
    public void onVitoIdentity(String message) {
        JsonNode root = parse(message);
        if (root == null) {
            return;
        }
        String eventType = extractEventType(root);
        JsonNode payload = extractPayload(root);
        String correlationId = text(root, "correlation_id", "correlationId");
        if (payload == null) {
            log.warn("Subject relay: identity event without payload, type={}", eventType);
            return;
        }

        UUID healthId = uuid(text(payload, "healthId", "health_id"));
        UUID tenantId = uuid(firstNonBlank(
                text(payload, "tenantId", "tenant_id"), text(root, "tenant_id", "tenantId")));
        if (healthId == null || tenantId == null) {
            log.warn("Subject relay: identity event missing healthId/tenantId, type={}", eventType);
            return;
        }

        String signal = classifyIdentitySignal(eventType, payload);
        switch (signal) {
            case "CREATED" -> {
                IdMappingEntity mapping = idResolutionService.findOrCreateMapping(tenantId, healthId, null);
                String status = text(payload, "status");
                subjectEvents.subjectCreated(tenantId, mapping.getCpid(), status, correlationId);
            }
            case "VERIFIED" -> {
                IdMappingEntity mapping = idResolutionService.findOrCreateMapping(tenantId, healthId, null);
                subjectEvents.subjectVerified(tenantId, mapping.getCpid(), correlationId);
            }
            case "DECEASED" -> idResolutionService.findExistingMapping(tenantId, healthId)
                    .ifPresentOrElse(
                            m -> subjectEvents.subjectDeceased(tenantId, m.getCpid(), correlationId),
                            () -> log.debug("Subject relay: deceased for unmapped identity — no clinical data, skipping"));
            default -> log.debug("Subject relay: ignoring identity event type={}", eventType);
        }
    }

    /**
     * Merge / dedup stream: merge executed and merge reversed.
     */
    @KafkaListener(
            topics = {"vito.dedup", "impilo.vito.dedup"},
            groupId = "tshepo-identity-subject-relay"
    )
    public void onVitoDedup(String message) {
        JsonNode root = parse(message);
        if (root == null) {
            return;
        }
        String eventType = extractEventType(root);
        JsonNode payload = extractPayload(root);
        String correlationId = text(root, "correlation_id", "correlationId");
        if (payload == null) {
            log.warn("Subject relay: dedup event without payload, type={}", eventType);
            return;
        }

        UUID tenantId = uuid(firstNonBlank(
                text(payload, "tenantId", "tenant_id"), text(root, "tenant_id", "tenantId")));
        UUID survivorHealthId = uuid(text(payload, "survivorHealthId", "survivor_health_id"));
        UUID mergedHealthId = uuid(text(payload, "mergedHealthId", "merged_health_id"));
        if (tenantId == null || survivorHealthId == null || mergedHealthId == null) {
            log.debug("Subject relay: dedup event not a merge (missing ids), type={}", eventType);
            return;
        }

        boolean reversed = eventType != null && eventType.toLowerCase(Locale.ROOT).contains("reversed");

        // The merged identity only matters clinically if it was ever mapped; the
        // survivor mapping is created on demand so downstream repoints always have
        // a target.
        IdMappingEntity survivor = idResolutionService.findOrCreateMapping(tenantId, survivorHealthId, null);
        IdMappingEntity merged = idResolutionService.findExistingMapping(tenantId, mergedHealthId).orElse(null);
        if (merged == null) {
            log.info("Subject relay: merge {} for unmapped merged identity — nothing to repoint",
                    reversed ? "reversal" : "event");
            return;
        }
        if (merged.getCpid().equals(survivor.getCpid())) {
            log.debug("Subject relay: merged and survivor share a mapping, skipping");
            return;
        }

        if (reversed) {
            idResolutionService.reactivateMappingAfterUnmerge(tenantId, mergedHealthId);
            subjectEvents.subjectMergeReversed(tenantId, survivor.getCpid(), merged.getCpid(), correlationId);
        } else {
            idResolutionService.retireMappingForMerge(tenantId, mergedHealthId, survivor.getCpid());
            subjectEvents.subjectMerged(tenantId, survivor.getCpid(), merged.getCpid(), correlationId);
        }
    }

    // ── Parsing helpers (tolerant of legacy raw payloads and v1.1 envelopes) ──

    private JsonNode parse(String message) {
        try {
            JsonNode root = objectMapper.readTree(message);
            // VITO's producer runs a JsonSerializer over pre-serialized JSON strings,
            // so the wire value may be a quoted JSON string literal — unwrap it.
            if (root != null && root.isTextual()) {
                root = objectMapper.readTree(root.asText());
            }
            return root;
        } catch (Exception e) {
            log.error("Subject relay: unparseable event, dropping: {}", e.getMessage());
            return null;
        }
    }

    private static String classifyIdentitySignal(String eventType, JsonNode payload) {
        String t = eventType != null ? eventType.toLowerCase(Locale.ROOT) : "";
        if (t.contains("created") || t.contains("identity_created") || t.contains("registered")) {
            return "CREATED";
        }
        if (t.contains("verified")) {
            return "VERIFIED";
        }
        if (t.contains("deceased")) {
            return "DECEASED";
        }
        // Legacy raw payloads (no envelope): infer from payload shape.
        if (payload.has("deceased") || "DECEASED".equals(text(payload, "status"))) {
            return "DECEASED";
        }
        if (payload.has("verifiedBy") || payload.has("verified_by")) {
            return "VERIFIED";
        }
        if (payload.has("did") || "PROVISIONAL".equals(text(payload, "status"))) {
            return "CREATED";
        }
        return "IGNORE";
    }

    private static String extractEventType(JsonNode root) {
        return firstNonBlank(text(root, "event_type", "eventType"));
    }

    private static JsonNode extractPayload(JsonNode root) {
        if (!root.has("payload")) {
            return root;
        }
        JsonNode p = root.get("payload");
        if (p == null || p.isNull()) {
            return null;
        }
        if (p.isTextual()) {
            try {
                return new ObjectMapper().readTree(p.asText());
            } catch (Exception e) {
                return null;
            }
        }
        return p.isObject() ? p : null;
    }

    private static String text(JsonNode node, String... fields) {
        if (node == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    private static UUID uuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
