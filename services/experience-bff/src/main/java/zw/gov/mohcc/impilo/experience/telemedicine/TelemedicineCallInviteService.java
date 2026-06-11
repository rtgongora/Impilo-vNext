package zw.gov.mohcc.impilo.experience.telemedicine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TelemedicineCallInviteService {

    /** Golden-path demo patient (Tatenda Moyo). */
    public static final String DEMO_PATIENT_ID = "a1000000-0000-0000-0000-000000000001";
    public static final String DEMO_CPID = "CPID-ZW-00001";

    private final TelemedicinePresenceRoom presenceRoom;
    private final ObjectMapper objectMapper;
    private final Map<String, Map<String, Object>> pendingByPatient = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Object>> pendingByUser = new ConcurrentHashMap<>();

    public TelemedicineCallInviteService(TelemedicinePresenceRoom presenceRoom, ObjectMapper objectMapper) {
        this.presenceRoom = presenceRoom;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> resolveCitizenProfile(String email) {
        if (email == null) {
            return Map.of();
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.equals("tatenda.moyo@example.com") || normalized.equals("citizen.moyo")) {
            return Map.of(
                    "patientId", DEMO_PATIENT_ID,
                    "cpid", DEMO_CPID,
                    "displayName", "Tatenda Moyo"
            );
        }
        return Map.of();
    }

    public Map<String, Object> sendInvite(
            String sessionId,
            String patientId,
            String callerId,
            String callerName,
            String callType) throws Exception {
        return sendInviteInternal(sessionId, patientId, null, callerId, callerName, callType);
    }

    public Map<String, Object> sendInviteToUser(
            String sessionId,
            String targetUserId,
            String callerId,
            String callerName,
            String callType) throws Exception {
        return sendInviteInternal(sessionId, null, targetUserId, callerId, callerName, callType);
    }

    private Map<String, Object> sendInviteInternal(
            String sessionId,
            String patientId,
            String targetUserId,
            String callerId,
            String callerName,
            String callType) throws Exception {

        Map<String, Object> invite = new LinkedHashMap<>();
        invite.put("sessionId", sessionId);
        if (patientId != null) {
            invite.put("patientId", patientId);
        }
        if (targetUserId != null) {
            invite.put("targetUserId", targetUserId);
        }
        invite.put("callerId", callerId);
        invite.put("callerName", callerName != null ? callerName : "Care team");
        invite.put("callType", callType != null ? callType : "audio");
        invite.put("createdAt", OffsetDateTime.now().toString());

        if (targetUserId != null && !targetUserId.isBlank()) {
            pendingByUser.put(targetUserId, invite);
        } else if (patientId != null) {
            pendingByPatient.put(patientId, invite);
        }

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("type", "call-invite");
        payload.put("sessionId", sessionId);
        if (patientId != null) {
            payload.put("patientId", patientId);
        }
        if (targetUserId != null) {
            payload.put("targetUserId", targetUserId);
        }
        payload.put("from", callerId);
        payload.put("callerName", String.valueOf(invite.get("callerName")));
        payload.put("callType", String.valueOf(invite.get("callType")));
        payload.put("createdAt", String.valueOf(invite.get("createdAt")));

        List<String> keys = new java.util.ArrayList<>();
        if (targetUserId != null && !targetUserId.isBlank()) {
            keys.add("user:" + targetUserId);
        }
        if (patientId != null && !patientId.isBlank()) {
            keys.add("patient:" + patientId);
            if (DEMO_PATIENT_ID.equals(patientId) || "pat-001".equals(patientId)) {
                keys.add("patient:" + DEMO_PATIENT_ID);
                keys.add("cpid:" + DEMO_CPID);
            }
        }
        presenceRoom.notifyKeys(keys, payload.toString());
        return invite;
    }

    public Map<String, Object> pollPending(String patientId) {
        return pendingByPatient.get(patientId);
    }

    public Map<String, Object> pollPendingForUser(String userId) {
        return pendingByUser.get(userId);
    }

    public void clearPending(String sessionId, String patientId) {
        Map<String, Object> pending = pendingByPatient.get(patientId);
        if (pending != null && sessionId.equals(pending.get("sessionId"))) {
            pendingByPatient.remove(patientId);
        }
    }

    public void clearPendingForUser(String sessionId, String userId) {
        Map<String, Object> pending = pendingByUser.get(userId);
        if (pending != null && sessionId.equals(pending.get("sessionId"))) {
            pendingByUser.remove(userId);
        }
    }
}
