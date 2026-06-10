package zw.gov.mohcc.impilo.experience.admingovernance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Derives honest invitation lifecycle states for admin governance surfaces.
 */
public final class InvitationLifecycle {

    public static final List<String> AUDIT_EVENT_TYPES = List.of(
            "invitation.sent",
            "invitation.pending_backend",
            "invitation.failed",
            "invitation.expired",
            "invitation.revoked",
            "invitation.resent",
            "invitation.accepted"
    );

    private InvitationLifecycle() {}

    public record InvitationView(
            String status,
            String invitationId,
            String keycloakUserId,
            String auditStatus,
            String expiresAt,
            boolean expired,
            boolean canResend,
            boolean canRevoke,
            boolean canRetry,
            List<Map<String, Object>> auditTrail
    ) {}

    public static InvitationView fromRepresentative(Map<String, Object> rep, ObjectMapper objectMapper) {
        Map<String, Object> meta = readMeta(str(rep.get("auditMetadata")), objectMapper);
        String repStatus = str(rep.get("status"));
        String invitationStatus = str(meta.get("invitationStatus"));
        String auditStatus = str(meta.get("invitationAuditStatus"));
        String expiresAt = str(meta.get("expiresAt"));
        String invitationId = str(meta.get("invitationId"));
        boolean expired = isExpired(expiresAt);

        String status = deriveStatus(
                repStatus,
                invitationStatus,
                auditStatus,
                str(meta.get("outcome")),
                invitationId,
                expired);

        boolean revoked = "revoked".equals(status) || "REVOKED".equalsIgnoreCase(repStatus);
        boolean activated = "activated".equals(status) || "ACTIVE".equalsIgnoreCase(repStatus);
        boolean canResend = !revoked && !activated && ("expired".equals(status) || "failed".equals(status) || "pending_backend".equals(status));
        boolean canRevoke = !revoked && !activated && ("sent".equals(status) || "pending_backend".equals(status) || "expired".equals(status));
        boolean canRetry = canResend;

        List<Map<String, Object>> auditTrail = buildAuditTrail(meta, status, expiresAt, invitationId);
        return new InvitationView(status, blankToNull(invitationId), blankToNull(str(meta.get("keycloakUserId"))),
                blankToNull(auditStatus), blankToNull(expiresAt), expired, canResend, canRevoke, canRetry, auditTrail);
    }

    public static InvitationView fromImportRow(JsonNode row, ObjectMapper objectMapper) {
        String outcome = row.path("outcome").asText("");
        String invitationId = row.path("invitationId").asText("");
        Map<String, Object> payload = readMeta(row.path("normalizedPayloadJson").asText(null), objectMapper);
        String expiresAt = str(payload.get("invitation_expires_at"));
        String auditStatus = str(payload.get("invitation_audit_status"));
        boolean expired = isExpired(expiresAt);
        String status = deriveStatus(
                "",
                str(payload.get("invitation_status")),
                auditStatus,
                outcome,
                invitationId,
                expired);
        boolean revoked = "revoked".equals(status);
        boolean activated = "activated".equals(status);
        boolean canResend = !revoked && !activated && ("expired".equals(status) || "failed".equals(status) || "pending_backend".equals(status) || "not_sent".equals(status));
        boolean canRevoke = !revoked && !activated && ("sent".equals(status) || "pending_backend".equals(status) || "expired".equals(status));
        List<Map<String, Object>> auditTrail = buildAuditTrail(payload, status, expiresAt, invitationId);
        return new InvitationView(status, blankToNull(invitationId), blankToNull(str(payload.get("keycloak_user_id"))),
                blankToNull(auditStatus), blankToNull(expiresAt), expired, canResend, canRevoke, canResend, auditTrail);
    }

    public static Map<String, Object> toMap(InvitationView view) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", view.status());
        map.put("invitationId", view.invitationId());
        map.put("keycloakUserId", view.keycloakUserId());
        map.put("auditStatus", view.auditStatus());
        map.put("expiresAt", view.expiresAt());
        map.put("expired", view.expired());
        map.put("canResend", view.canResend());
        map.put("canRevoke", view.canRevoke());
        map.put("canRetry", view.canRetry());
        map.put("auditTrail", view.auditTrail());
        return map;
    }

    static String deriveStatus(
            String repStatus,
            String invitationStatus,
            String auditStatus,
            String outcome,
            String invitationId,
            boolean expired) {
        if ("REVOKED".equalsIgnoreCase(repStatus) || "revoked".equalsIgnoreCase(invitationStatus) || "invitation_revoked".equals(outcome)) {
            return "revoked";
        }
        if ("ACTIVE".equalsIgnoreCase(repStatus) || "activated".equalsIgnoreCase(invitationStatus) || "activated".equals(outcome)) {
            return "activated";
        }
        if (expired) {
            return "expired";
        }
        if ("invitation_failed".equals(outcome) || "failed".equals(invitationStatus) || "failed_non_blocking".equals(auditStatus)) {
            return "failed";
        }
        if ("pending_backend".equals(auditStatus) || "invitations_pending".equals(outcome)) {
            return "pending_backend";
        }
        if ("invitation_sent".equals(outcome) || "INVITATION_SENT".equalsIgnoreCase(repStatus) || "sent".equals(invitationStatus)) {
            return invitationId.isBlank() ? "pending_backend" : "sent";
        }
        if ("ready_to_invite".equals(outcome) || "requires_approval".equals(outcome) || "PENDING".equalsIgnoreCase(repStatus)) {
            return "not_sent";
        }
        return invitationId.isBlank() ? "not_sent" : "sent";
    }

    static boolean isExpired(String expiresAt) {
        if (expiresAt == null || expiresAt.isBlank()) return false;
        try {
            return OffsetDateTime.parse(expiresAt).isBefore(OffsetDateTime.now());
        } catch (Exception e) {
            return false;
        }
    }

    static List<Map<String, Object>> buildAuditTrail(Map<String, Object> meta, String status, String expiresAt, String invitationId) {
        List<Map<String, Object>> trail = new ArrayList<>();
        if (!invitationId.isBlank()) {
            trail.add(event("invitation.sent", invitationId, expiresAt));
        }
        if ("pending_backend".equals(status)) {
            trail.add(event("invitation.pending_backend", invitationId, expiresAt));
        }
        if ("failed".equals(status)) {
            trail.add(event("invitation.failed", invitationId, expiresAt));
        }
        if ("expired".equals(status)) {
            trail.add(event("invitation.expired", invitationId, expiresAt));
        }
        if ("revoked".equals(status)) {
            trail.add(event("invitation.revoked", invitationId, expiresAt));
        }
        if ("activated".equals(status)) {
            trail.add(event("invitation.accepted", invitationId, expiresAt));
        }
        Object history = meta.get("invitationAuditTrail");
        if (history instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    map.forEach((k, v) -> copy.put(String.valueOf(k), v));
                    trail.add(copy);
                }
            }
        }
        return trail;
    }

    private static Map<String, Object> event(String type, String invitationId, String expiresAt) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("eventType", type);
        event.put("invitationId", blankToNull(invitationId));
        event.put("expiresAt", blankToNull(expiresAt));
        event.put("occurredAt", OffsetDateTime.now().toString());
        return event;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readMeta(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
