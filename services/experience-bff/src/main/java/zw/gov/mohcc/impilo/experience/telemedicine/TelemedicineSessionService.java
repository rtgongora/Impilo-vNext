package zw.gov.mohcc.impilo.experience.telemedicine;

import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelemedicineSessionService {

    /** Golden-path demo patient — Tatenda Moyo (V4 seed). */
    public static final UUID DEMO_PATIENT_ID = UUID.fromString("a1000000-0000-0000-0000-000000000001");

    /** Dev test facility (V40 seed / FacilityController stub). */
    public static final UUID TEST_FACILITY_ID = UUID.fromString("a1b2c3d4-00ff-4000-8000-000000000099");

    private final Map<String, Map<UUID, Map<String, Object>>> sessionsByTenant = new ConcurrentHashMap<>();

    public Map<String, Object> createSession(String tenantId, Map<String, Object> body) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();

        String sessionType = stringVal(body, "session_type", "VIDEO");
        UUID patientId = parseUuid(stringVal(body, "patient_id", null));
        UUID providerId = parseUuid(stringVal(body, "provider_id", null));
        String providerUserId = stringVal(body, "provider_user_id", null);
        UUID facilityId = parseUuid(stringVal(body, "facility_id", null));
        if (facilityId == null) {
            facilityId = TEST_FACILITY_ID;
        }
        UUID encounterId = parseUuid(stringVal(body, "encounter_id", null));
        UUID referralId = parseUuid(stringVal(body, "referral_id", null));
        String notes = stringVal(body, "notes", null);
        OffsetDateTime scheduled = parseScheduled(stringVal(body, "scheduled_at", null), now.plusHours(1));
        String roomUrl = "session-" + id;

        Map<String, Object> session = toResource(
                id,
                sessionType,
                patientId,
                providerId,
                providerUserId,
                facilityId,
                encounterId,
                referralId,
                scheduled,
                roomUrl,
                notes,
                now,
                "SCHEDULED");

        tenantBucket(normalizeTenant(tenantId)).put(id, session);
        return session;
    }

    public List<Map<String, Object>> listSessions(
            String tenantId,
            String providerId,
            String patientId,
            String facilityId,
            String referralId,
            String status) {

        return tenantBucket(normalizeTenant(tenantId)).values().stream()
                .filter(session -> matchesFilter(session, "provider_id", providerId))
                .filter(session -> matchesFilter(session, "patient_id", patientId))
                .filter(session -> matchesFilter(session, "facility_id", facilityId))
                .filter(session -> matchesFilter(session, "referral_id", referralId))
                .filter(session -> matchesStatus(session, status))
                .sorted(Comparator.comparing(
                        (Map<String, Object> s) -> attributeInstant(s, "created_at"),
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(50)
                .toList();
    }

    public void markJoined(UUID sessionId, String tenantId) {
        Map<String, Object> session = tenantBucket(normalizeTenant(tenantId)).get(sessionId);
        if (session == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) session.get("attributes");
        attributes.put("status", "IN_PROGRESS");
        if (attributes.get("started_at") == null) {
            attributes.put("started_at", OffsetDateTime.now());
        }
    }

    public void markEnded(UUID sessionId, String tenantId, String notes) {
        Map<String, Object> session = tenantBucket(normalizeTenant(tenantId)).get(sessionId);
        if (session == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) session.get("attributes");
        attributes.put("status", "COMPLETED");
        attributes.put("ended_at", OffsetDateTime.now());
        if (notes != null && !notes.isBlank()) {
            attributes.put("notes", notes);
        }
    }

    private Map<UUID, Map<String, Object>> tenantBucket(String tenantId) {
        return sessionsByTenant.computeIfAbsent(tenantId, __ -> new ConcurrentHashMap<>());
    }

    private static boolean matchesFilter(Map<String, Object> session, String key, String value) {
        if (value == null || value.isBlank()) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) session.get("attributes");
        Object actual = attributes.get(key);
        return actual != null && value.trim().equalsIgnoreCase(actual.toString());
    }

    private static boolean matchesStatus(Map<String, Object> session, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) session.get("attributes");
        return status.trim().equalsIgnoreCase(String.valueOf(attributes.get("status")));
    }

    private static OffsetDateTime attributeInstant(Map<String, Object> session, String key) {
        @SuppressWarnings("unchecked")
        Map<String, Object> attributes = (Map<String, Object>) session.get("attributes");
        Object value = attributes.get(key);
        if (value instanceof OffsetDateTime offset) {
            return offset;
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return OffsetDateTime.parse(text);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Map<String, Object> toResource(
            UUID id,
            String sessionType,
            UUID patientId,
            UUID providerId,
            String providerUserId,
            UUID facilityId,
            UUID encounterId,
            UUID referralId,
            OffsetDateTime scheduledAt,
            String roomUrl,
            String notes,
            OffsetDateTime createdAt,
            String status) {

        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("session_type", sessionType);
        attributes.put("status", status);
        attributes.put("patient_id", patientId != null ? patientId.toString() : null);
        attributes.put("provider_id", providerId != null ? providerId.toString() : null);
        attributes.put("provider_user_id", providerUserId);
        attributes.put("facility_id", facilityId != null ? facilityId.toString() : null);
        attributes.put("encounter_id", encounterId != null ? encounterId.toString() : null);
        attributes.put("referral_id", referralId != null ? referralId.toString() : null);
        attributes.put("scheduled_at", scheduledAt);
        attributes.put("room_url", roomUrl);
        attributes.put("notes", notes);
        attributes.put("created_at", createdAt);
        attributes.put("channel", roomUrl);

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("id", id.toString());
        resource.put("type", "TelemedicineSession");
        resource.put("attributes", attributes);
        return resource;
    }

    private static String normalizeTenant(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) {
            return "tenant-moh-zw";
        }
        if ("00000000-0000-4000-8000-000000000001".equals(tenantId) || "moh-zw".equals(tenantId)) {
            return "tenant-moh-zw";
        }
        return tenantId;
    }

    private static OffsetDateTime parseScheduled(String raw, OffsetDateTime fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            return fallback;
        }
    }

    private static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String stringVal(Map<String, Object> body, String key, String defaultValue) {
        Object value = body.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
