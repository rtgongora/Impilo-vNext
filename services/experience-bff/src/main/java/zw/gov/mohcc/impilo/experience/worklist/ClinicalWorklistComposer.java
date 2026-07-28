package zw.gov.mohcc.impilo.experience.worklist;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cross-service clinical worklist composition, extracted from
 * {@code ClinicalWorklistController} (Phase E, E1) — byte-identical
 * behaviour, now reusable by the Work Home composition layer (Phase E4)
 * without duplicating six append* methods a second time.
 *
 * <p>Sequential by design in this extraction (unchanged from the original) —
 * making these calls concurrent is {@code ContextPropagatingFanout}'s job
 * (E2), not this class's; extracting first and parallelising second keeps
 * each step behaviour-provable on its own.</p>
 */
@Component
public class ClinicalWorklistComposer {

    private static final Logger log = LoggerFactory.getLogger(ClinicalWorklistComposer.class);

    private final PctServiceClient pctClient;
    private final OrosServiceClient orosClient;
    private final PharmacyServiceClient pharmacyClient;
    private final ObjectMapper objectMapper;

    public ClinicalWorklistComposer(PctServiceClient pctClient,
                                     OrosServiceClient orosClient,
                                     PharmacyServiceClient pharmacyClient,
                                     ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.orosClient = orosClient;
        this.pharmacyClient = pharmacyClient;
        this.objectMapper = objectMapper;
    }

    /** All six item families, in original append order, capped at {@code size}. */
    public List<Map<String, Object>> composeAll(String facilityId, int size) {
        List<Map<String, Object>> items = new ArrayList<>();
        appendQueueItems(items, facilityId, size);
        appendReferralItems(items, facilityId, size);
        appendTaskItems(items, facilityId, size);
        appendOrderItems(items, facilityId, size);
        appendPharmacyItems(items, facilityId, size);
        appendTelemedicineItems(items, facilityId, size);
        return items;
    }

    public void appendQueueItems(List<Map<String, Object>> out, String facilityId, int size) {
        try {
            UUID fid = UUID.fromString(facilityId.trim());
            JsonNode queues = pctClient.listQueues(fid, null);
            for (Map<String, Object> queue : asList(queues)) {
                String queueId = text(queue.get("id"), text(queue.get("queueId")));
                String queueType = text(queue.get("queueType"), text(queue.get("type"), "QUEUE"));
                Number waiting = number(queue.get("waitingCount"), number(queue.get("waiting"), 0));
                if (waiting == null || waiting.intValue() <= 0) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "queue:" + (queueId != null ? queueId : queueType));
                item.put("kind", "QUEUE");
                item.put("source", "pct");
                item.put("title", queueType + " queue backlog");
                item.put("description", waiting + " patient(s) waiting");
                item.put("priority", waiting.intValue() >= 15 ? "URGENT" : waiting.intValue() >= 8 ? "HIGH" : "MEDIUM");
                item.put("status", "WAITING");
                item.put("queue_type", queueType);
                item.put("created_at", Instant.now().toString());
                item.put("href", "/queue/waiting");
                out.add(item);
                if (out.size() >= size) return;
            }
        } catch (Exception e) {
            log.debug("clinical-worklist queue composition failed: {}", e.getMessage());
        }
    }

    public void appendReferralItems(List<Map<String, Object>> out, String facilityId, int size) {
        try {
            JsonNode referrals = pctClient.listIncomingReferrals(facilityId, null, 0, Math.min(size, 60));
            for (Map<String, Object> referral : asList(referrals)) {
                String id = text(referral.get("id"), text(referral.get("referralId")));
                String status = text(referral.get("status"), "PENDING");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "referral:" + (id != null ? id : UUID.randomUUID().toString()));
                item.put("kind", "REFERRAL");
                item.put("source", "pct");
                item.put("title", text(referral.get("specialty"), "Incoming referral"));
                item.put("description", text(referral.get("reason"), "Requires clinical review"));
                item.put("priority", text(referral.get("urgency"), "MEDIUM"));
                item.put("status", status);
                item.put("patient_id", text(referral.get("patientId"), text(referral.get("patient_id"))));
                item.put("created_at", text(referral.get("createdAt"), text(referral.get("created_at"), Instant.now().toString())));
                item.put("href", "/home/referrals");
                out.add(item);
                if (out.size() >= size) return;
            }
        } catch (Exception e) {
            log.debug("clinical-worklist referral composition failed: {}", e.getMessage());
        }
    }

    public void appendTaskItems(List<Map<String, Object>> out, String facilityId, int size) {
        try {
            JsonNode tasks = pctClient.listWorkspaceTasks(UUID.fromString(facilityId.trim()));
            for (Map<String, Object> task : asList(tasks)) {
                String id = text(task.get("id"), text(task.get("taskId")));
                String status = text(task.get("status"), "PENDING");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "task:" + (id != null ? id : UUID.randomUUID().toString()));
                item.put("kind", "TASK");
                item.put("source", "pct");
                item.put("title", text(task.get("title"), text(task.get("name"), "Clinical task")));
                item.put("description", text(task.get("description"), "Task action required"));
                item.put("priority", text(task.get("priority"), "MEDIUM"));
                item.put("status", status);
                item.put("assignee_id", text(task.get("assigneeId"), text(task.get("assignee_id"))));
                item.put("due_at", text(task.get("dueAt"), text(task.get("due_at"))));
                item.put("created_at", text(task.get("createdAt"), text(task.get("created_at"), Instant.now().toString())));
                item.put("href", "/caregiving/tasks");
                out.add(item);
                if (out.size() >= size) return;
            }
        } catch (Exception e) {
            log.debug("clinical-worklist task composition failed: {}", e.getMessage());
        }
    }

    public void appendOrderItems(List<Map<String, Object>> out, String facilityId, int size) {
        try {
            JsonNode orders = orosClient.getWorklist(facilityId, null);
            for (Map<String, Object> order : asList(orders)) {
                String id = text(order.get("id"), text(order.get("orderId")));
                String orderType = text(order.get("orderType"), text(order.get("kind"), "ORDER"));
                String status = text(order.get("status"), "IN_PROGRESS");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "order:" + (id != null ? id : UUID.randomUUID().toString()));
                item.put("kind", "ORDER");
                item.put("source", "oros");
                item.put("title", orderType + " order");
                item.put("description", text(order.get("summary"), text(order.get("clinicalNotes"), "Order in progress")));
                item.put("priority", text(order.get("priority"), "MEDIUM"));
                item.put("status", status);
                item.put("patient_id", text(order.get("patientCpid"), text(order.get("patient_id"))));
                item.put("created_at", text(order.get("createdAt"), text(order.get("created_at"), Instant.now().toString())));
                item.put("href", "/ehr");
                out.add(item);
                if (out.size() >= size) return;
            }
        } catch (Exception e) {
            log.debug("clinical-worklist order composition failed: {}", e.getMessage());
        }
    }

    public void appendPharmacyItems(List<Map<String, Object>> out, String facilityId, int size) {
        try {
            JsonNode rows = pharmacyClient.getWorklist(facilityId, null);
            for (Map<String, Object> row : asList(rows)) {
                String id = text(row.get("id"), text(row.get("dispenseOrderId")));
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "pharmacy:" + (id != null ? id : UUID.randomUUID().toString()));
                item.put("kind", "PHARMACY");
                item.put("source", "pharmacy");
                item.put("title", "Dispense workflow");
                item.put("description", text(row.get("status"), "Prescription awaiting action"));
                item.put("priority", text(row.get("priority"), "MEDIUM"));
                item.put("status", text(row.get("status"), "PENDING"));
                item.put("patient_id", text(row.get("patientId"), text(row.get("patient_id"))));
                item.put("created_at", text(row.get("createdAt"), text(row.get("created_at"), Instant.now().toString())));
                item.put("href", "/pharmacy/dispense");
                out.add(item);
                if (out.size() >= size) return;
            }
        } catch (Exception e) {
            log.debug("clinical-worklist pharmacy composition failed: {}", e.getMessage());
        }
    }

    public void appendTelemedicineItems(List<Map<String, Object>> out, String facilityId, int size) {
        try {
            JsonNode sessions = pctClient.listTelehealthSessions(facilityId, null, 0, Math.min(size, 60));
            for (Map<String, Object> session : asList(sessions)) {
                String id = text(session.get("id"), text(session.get("sessionId")));
                String status = text(session.get("status"), "SCHEDULED");
                if (!"SCHEDULED".equalsIgnoreCase(status) && !"IN_PROGRESS".equalsIgnoreCase(status) && !"WAITING".equalsIgnoreCase(status)) {
                    continue;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", "telemedicine:" + (id != null ? id : UUID.randomUUID().toString()));
                item.put("kind", "TELEMEDICINE");
                item.put("source", "pct");
                item.put("title", "Teleconsult session");
                item.put("description", text(session.get("sessionType"), "Remote care session"));
                item.put("priority", "MEDIUM");
                item.put("status", status);
                item.put("patient_id", text(session.get("patientId"), text(session.get("patient_id"))));
                item.put("created_at", text(session.get("scheduledAt"), text(session.get("scheduled_at"), Instant.now().toString())));
                item.put("href", "/telemedicine");
                out.add(item);
                if (out.size() >= size) return;
            }
        } catch (Exception e) {
            log.debug("clinical-worklist telemedicine composition failed: {}", e.getMessage());
        }
    }

    private List<Map<String, Object>> asList(JsonNode node) {
        if (node == null || node.isNull()) return List.of();
        JsonNode candidate = node;
        if (node.isObject() && node.has("items") && node.get("items").isArray()) {
            candidate = node.get("items");
        } else if (node.isObject() && node.has("data") && node.get("data").isArray()) {
            candidate = node.get("data");
        } else if (node.isObject()) {
            return List.of(objectMapper.convertValue(node, Map.class));
        }
        if (!candidate.isArray()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (JsonNode row : candidate) {
            out.add(objectMapper.convertValue(row, Map.class));
        }
        return out;
    }

    private static String text(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() ? null : s;
    }

    private static String text(Object value, String fallback) {
        String s = text(value);
        return s != null ? s : fallback;
    }

    private static Number number(Object value, Number fallback) {
        if (value == null) return fallback;
        if (value instanceof Number n) return n;
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
