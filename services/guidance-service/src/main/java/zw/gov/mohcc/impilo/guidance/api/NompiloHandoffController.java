package zw.gov.mohcc.impilo.guidance.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.guidance.core.NompiloHandoffService;
import zw.gov.mohcc.impilo.guidance.core.NompiloHandoffService.NewHandoff;
import zw.gov.mohcc.impilo.guidance.persistence.entity.NompiloHandoffEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Nompilo AI→human handoff lifecycle API (G-KH-05/06). Internal surface consumed by the
 * Experience BFF (Nompilo handoff) and the operations-desk console.
 */
@RestController
@RequestMapping("/internal/v1/guidance/nompilo/handoffs")
public class NompiloHandoffController {

    private final NompiloHandoffService service;
    private final ObjectMapper objectMapper;

    public NompiloHandoffController(NompiloHandoffService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @PostMapping("")
    public ResponseEntity<Map<String, Object>> request(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        NewHandoff cmd = new NewHandoff(
                str(body, "transactionId"),
                str(body, "subjectId"),
                str(body, "actorId") != null ? str(body, "actorId") : actorId,
                str(body, "reason"),
                str(body, "priority"),
                str(body, "destination"),
                str(body, "flowId"),
                contextJson(body.get("context")));
        NompiloHandoffEntity h = service.request(tenantId, cmd);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", toView(h)));
    }

    @GetMapping("")
    public ResponseEntity<Map<String, Object>> list(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestParam(value = "queue", defaultValue = "false") boolean queueOnly) {
        List<NompiloHandoffEntity> rows = queueOnly ? service.queue(tenantId) : service.list(tenantId);
        return ResponseEntity.ok(Map.of("data", rows.stream().map(this::toView).toList()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(
            @RequestHeader("x-tenant-id") String tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(Map.of("data", toView(service.get(tenantId, id))));
    }

    @PostMapping("/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @RequestHeader("x-tenant-id") String tenantId,
            @RequestHeader(value = "x-actor-id", required = false) String actorId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        String assignedTo = body != null && str(body, "assignedTo") != null ? str(body, "assignedTo") : actorId;
        return ResponseEntity.ok(Map.of("data", toView(service.accept(tenantId, id, assignedTo))));
    }

    @PostMapping("/{id}/escalate")
    public ResponseEntity<Map<String, Object>> escalate(
            @RequestHeader("x-tenant-id") String tenantId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", toView(service.escalate(tenantId, id, str(body, "reason")))));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<Map<String, Object>> close(
            @RequestHeader("x-tenant-id") String tenantId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", toView(service.close(tenantId, id, str(body, "note")))));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancel(
            @RequestHeader("x-tenant-id") String tenantId,
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(Map.of("data", toView(service.cancel(tenantId, id, str(body, "note")))));
    }

    private Map<String, Object> toView(NompiloHandoffEntity h) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", h.getId().toString());
        v.put("transactionId", h.getTransactionId());
        v.put("subjectId", h.getSubjectId());
        v.put("reason", h.getReason());
        v.put("priority", h.getPriority());
        v.put("destination", h.getDestination());
        v.put("flowId", h.getFlowId());
        v.put("status", h.getStatus());
        v.put("assignedTo", h.getAssignedTo());
        v.put("createdAt", h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
        v.put("acceptedAt", h.getAcceptedAt() != null ? h.getAcceptedAt().toString() : null);
        v.put("closedAt", h.getClosedAt() != null ? h.getClosedAt().toString() : null);
        return v;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null) return null;
        Object v = body.get(key);
        return v == null ? null : v.toString();
    }

    private String contextJson(Object context) {
        if (context == null) return null;
        try {
            return objectMapper.writeValueAsString(context);
        } catch (Exception e) {
            return null;
        }
    }
}
