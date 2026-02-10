package zw.gov.mohcc.impilo.vito.events;

import zw.gov.mohcc.impilo.sharedkernel.events.DualEmitPolicy;
import zw.gov.mohcc.impilo.sharedkernel.events.EmitMode;
import zw.gov.mohcc.impilo.sharedkernel.events.EventEnvelope;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * VITO-specific event mapping layer.
 *
 * Does NOT re-implement the canonical {@link EventEnvelope}, {@link EmitMode},
 * or {@link DualEmitPolicy} from shared-kernel-java. Instead delegates to those
 * types and adds only VITO-specific topic routing and envelope construction.
 *
 * <h3>Emit mode precedence</h3>
 * <ol>
 *   <li>{@code EMIT_MODE} system property (e.g. {@code -DEMIT_MODE=V1_1_ONLY})</li>
 *   <li>{@code EMIT_MODE} environment variable</li>
 *   <li>{@code vito.v11.emit-mode} from application.yml (Spring-resolved)</li>
 *   <li>Default: {@link EmitMode#DUAL}</li>
 * </ol>
 *
 * {@link DualEmitPolicy#mode()} handles steps 1-2. If neither is set (returns DUAL
 * as default), we check the Spring-injected yml value. This allows operators to
 * configure via K8s env var OR Helm values.yml while respecting the canonical
 * shared-kernel contract.
 */
public final class VitoEventMapper {

    private static final String PRODUCER = "vito";
    private static final DualEmitPolicy SHARED_POLICY = new DualEmitPolicy();

    private VitoEventMapper() {
    }

    // ---- Emit mode resolution ----

    /**
     * Resolve the effective emit mode.
     *
     * @param ymlFallback the value from application.yml (vito.v11.emit-mode), may be null
     * @return the resolved EmitMode
     */
    public static EmitMode resolveEmitMode(String ymlFallback) {
        // Step 1-2: shared-kernel DualEmitPolicy checks System.getProperty/getenv("EMIT_MODE")
        String envProp = System.getProperty("EMIT_MODE");
        if (envProp == null || envProp.isBlank()) {
            envProp = System.getenv("EMIT_MODE");
        }
        if (envProp != null && !envProp.isBlank()) {
            // EMIT_MODE env/prop is explicitly set — delegate to shared-kernel
            return SHARED_POLICY.mode();
        }
        // Step 3: fall back to application.yml value
        if (ymlFallback != null && !ymlFallback.isBlank()) {
            try {
                return EmitMode.valueOf(ymlFallback.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // invalid yml value — fall through to default
            }
        }
        // Step 4: default
        return EmitMode.DUAL;
    }

    /**
     * Should the publisher emit legacy-format events for the given mode?
     */
    public static boolean emitLegacy(EmitMode mode) {
        return mode == EmitMode.LEGACY_ONLY || mode == EmitMode.DUAL;
    }

    /**
     * Should the publisher emit v1.1-format events for the given mode?
     */
    public static boolean emitV11(EmitMode mode) {
        return mode == EmitMode.V1_1_ONLY || mode == EmitMode.DUAL;
    }

    // ---- Legacy topic routing ----

    public static String resolveLegacyTopic(String aggregateType) {
        return switch (aggregateType) {
            case "CLIENT" -> "vito.identity";
            case "ALIAS" -> "vito.alias";
            case "SMART_CARD" -> "vito.cards";
            case "WALLET" -> "vito.wallet";
            case "ISSUANCE" -> "vito.issuance";
            case "MERGE", "DEDUP" -> "vito.dedup";
            case "PROVISIONAL" -> "vito.offline";
            case "PICKUP" -> "vito.pickup";
            case "PRINT_JOB" -> "vito.print";
            default -> "vito.events";
        };
    }

    // ---- V1.1 topic routing ----

    public static String resolveV11Topic(String aggregateType) {
        return switch (aggregateType) {
            case "CLIENT" -> "impilo.vito.identity";
            case "ALIAS" -> "impilo.vito.alias";
            case "SMART_CARD" -> "impilo.vito.cards";
            case "WALLET" -> "impilo.vito.wallet";
            case "ISSUANCE" -> "impilo.vito.issuance";
            case "MERGE", "DEDUP" -> "impilo.vito.dedup";
            case "PROVISIONAL" -> "impilo.vito.offline";
            case "PICKUP" -> "impilo.vito.pickup";
            case "PRINT_JOB" -> "impilo.vito.print";
            default -> "impilo.vito.events";
        };
    }

    /**
     * Build v1.1 event type: "impilo.vito.&lt;aggregate&gt;.&lt;suffix&gt;.v1"
     */
    public static String resolveV11EventType(String aggregateType, String eventType) {
        String aggLower = aggregateType.toLowerCase().replace("_", ".");
        String suffix = extractEventSuffix(eventType);
        return "impilo.vito." + aggLower + "." + suffix + ".v1";
    }

    public static String resolveSubjectType(String aggregateType) {
        return switch (aggregateType) {
            case "CLIENT" -> "Patient";
            case "ALIAS" -> "Alias";
            case "SMART_CARD" -> "SmartCard";
            case "WALLET" -> "Wallet";
            case "ISSUANCE" -> "IssuanceRequest";
            case "MERGE" -> "MergeHistory";
            case "DEDUP" -> "DedupCase";
            case "PROVISIONAL" -> "ProvisionalId";
            case "PICKUP" -> "DelegatedPickup";
            case "PRINT_JOB" -> "PrintJob";
            default -> aggregateType;
        };
    }

    // ---- EventEnvelope construction ----

    /**
     * Build a v1.1 EventEnvelope from outbox event fields.
     * Uses the canonical shared-kernel Builder — does NOT re-implement the envelope.
     */
    public static EventEnvelope buildEnvelope(
            String aggregateType, String aggregateId, String eventType,
            Map<String, Object> payloadMap, String tenantId, String podId,
            String correlationId, String idempotencyKey, Long outboxId,
            OffsetDateTime occurredAt) {

        OffsetDateTime now = OffsetDateTime.now();
        return EventEnvelope.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(resolveV11EventType(aggregateType, eventType))
                .schemaVersion(1)
                .correlationId(correlationId != null ? correlationId : UUID.randomUUID().toString())
                .causationId(outboxId != null ? outboxId.toString() : UUID.randomUUID().toString())
                .idempotencyKey(idempotencyKey != null ? idempotencyKey : String.valueOf(outboxId))
                .producer(PRODUCER)
                .tenantId(tenantId != null ? tenantId : "unknown")
                .podId(podId != null ? podId : "unknown")
                .occurredAt(occurredAt != null ? occurredAt : now)
                .emittedAt(now)
                .subjectType(resolveSubjectType(aggregateType))
                .subjectId(aggregateId)
                .payload(payloadMap)
                .meta(Map.of("partition_key", aggregateId))
                .build();
    }

    // ---- Internal helpers ----

    private static String extractEventSuffix(String eventType) {
        if (eventType == null) return "unknown";
        int lastDot = eventType.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < eventType.length() - 1) {
            return eventType.substring(lastDot + 1).toLowerCase();
        }
        int lastUnderscore = eventType.lastIndexOf('_');
        if (lastUnderscore >= 0 && lastUnderscore < eventType.length() - 1) {
            return eventType.substring(lastUnderscore + 1).toLowerCase();
        }
        return eventType.toLowerCase();
    }
}
