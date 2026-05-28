package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Composed clinician worklist across queue, referrals, orders, pharmacy, telemedicine, and tasks.
 *
 * This endpoint intentionally provides a single actionable inbox abstraction for frontend
 * workflow orchestration while upstream services remain domain-siloed.
 */
@RestController
public class ClinicalWorklistController {

    private static final Logger log = LoggerFactory.getLogger(ClinicalWorklistController.class);

    private final PctServiceClient pctClient;
    private final OrosServiceClient orosClient;
    private final PharmacyServiceClient pharmacyClient;
    private final ObjectMapper objectMapper;

    public ClinicalWorklistController(
            PctServiceClient pctClient,
            OrosServiceClient orosClient,
            PharmacyServiceClient pharmacyClient,
            ObjectMapper objectMapper
    ) {
        this.pctClient = pctClient;
        this.orosClient = orosClient;
        this.pharmacyClient = pharmacyClient;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/internal/v1/clinical-worklist")
    public ResponseEntity<Map<String, Object>> clinicalWorklist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "assignee_id") String assigneeId,
            @RequestParam(defaultValue = "40", name = "size") int size
    ) {
        int safeSize = Math.min(Math.max(size, 5), 200);
        List<Map<String, Object>> items = new ArrayList<>();

        appendQueueItems(items, facilityId, safeSize);
        appendReferralItems(items, facilityId, safeSize);
        appendTaskItems(items, facilityId, safeSize);
        appendOrderItems(items, facilityId, safeSize);
        appendPharmacyItems(items, facilityId, safeSize);
        appendTelemedicineItems(items, facilityId, safeSize);

        if (assigneeId != null && !assigneeId.isBlank()) {
            items = items.stream().filter(it -> {
                String candidate = text(it.get("assignee_id"));
                return candidate == null || candidate.isBlank() || assigneeId.equals(candidate);
            }).toList();
        }

        items.sort((a, b) -> {
            int pa = priorityRank(text(a.get("priority")));
            int pb = priorityRank(text(b.get("priority")));
            if (pa != pb) return Integer.compare(pa, pb);
            long ta = epochMillis(text(a.get("created_at"), text(a.get("due_at"), text(a.get("occurred_at")))));
            long tb = epochMillis(text(b.get("created_at"), text(b.get("due_at"), text(b.get("occurred_at")))));
            return Long.compare(tb, ta);
        });

        if (items.size() > safeSize) {
            items = new ArrayList<>(items.subList(0, safeSize));
        }

        Map<String, Object> summary = summarize(items);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("summary", summary);

        return ResponseEntity.ok(Map.of(
                "data", data,
                "meta", Map.of(
                        "request_id", requestId,
                        "correlation_id", correlationId,
                        "facility_id", facilityId,
                        "generated_at", Instant.now().toString()
                )
        ));
    }

    @GetMapping("/internal/v1/mobile/provider/clinical-worklist")
    public ResponseEntity<Map<String, Object>> mobileClinicalWorklist(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId,
            @RequestParam(required = false, name = "assignee_id") String assigneeId,
            @RequestParam(defaultValue = "40", name = "size") int size
    ) {
        return clinicalWorklist(tenantId, requestId, correlationId, facilityId, assigneeId, size);
    }

    private void appendQueueItems(List<Map<String, Object>> out, String facilityId, int size) {
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

    private void appendReferralItems(List<Map<String, Object>> out, String facilityId, int size) {
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

    private void appendTaskItems(List<Map<String, Object>> out, String facilityId, int size) {
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

    private void appendOrderItems(List<Map<String, Object>> out, String facilityId, int size) {
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

    private void appendPharmacyItems(List<Map<String, Object>> out, String facilityId, int size) {
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

    private void appendTelemedicineItems(List<Map<String, Object>> out, String facilityId, int size) {
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

    private Map<String, Object> summarize(List<Map<String, Object>> items) {
        int urgent = 0;
        int overdue = 0;
        Map<String, Integer> bySource = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String p = text(item.get("priority"), "MEDIUM").toUpperCase(Locale.ROOT);
            if ("URGENT".equals(p) || "STAT".equals(p) || "EMERGENCY".equals(p) || "HIGH".equals(p)) {
                urgent++;
            }
            String due = text(item.get("due_at"));
            if (due != null && !due.isBlank() && epochMillis(due) < System.currentTimeMillis()) {
                overdue++;
            }
            String source = text(item.get("source"), "unknown");
            bySource.put(source, bySource.getOrDefault(source, 0) + 1);
        }
        return Map.of(
                "total", items.size(),
                "urgent", urgent,
                "overdue", overdue,
                "by_source", bySource
        );
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

    private static String text(Object primary, String secondary, String fallback) {
        String a = text(primary);
        if (a != null) return a;
        if (secondary != null && !secondary.isBlank()) return secondary;
        return fallback;
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

    private static int priorityRank(String priority) {
        if (priority == null) return 4;
        return switch (priority.toUpperCase(Locale.ROOT)) {
            case "URGENT", "EMERGENCY", "STAT" -> 0;
            case "HIGH" -> 1;
            case "MEDIUM", "ROUTINE" -> 2;
            case "LOW" -> 3;
            default -> 4;
        };
    }

    private static long epochMillis(String iso) {
        if (iso == null || iso.isBlank()) return 0L;
        try {
            return Instant.parse(iso).toEpochMilli();
        } catch (Exception ignored) {
            return 0L;
        }
    }
}
