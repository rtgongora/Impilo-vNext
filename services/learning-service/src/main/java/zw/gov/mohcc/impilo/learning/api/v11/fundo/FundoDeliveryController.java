package zw.gov.mohcc.impilo.learning.api.v11.fundo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoDeliveryService;

/** Learning-delivery administration: facilitators, venues, cohort-facilitator links, session delivery (A1). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoDeliveryController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoDeliveryService service;

    public FundoDeliveryController(FundoDeliveryService service) {
        this.service = service;
    }

    @PostMapping("/facilitators")
    public ResponseEntity<Map<String, Object>> createFacilitator(@RequestBody Map<String, Object> body) {
        return ok(service.createFacilitator(tenantId(), actorId(), body));
    }

    @GetMapping("/facilitators")
    public ResponseEntity<Map<String, Object>> listFacilitators(
            @RequestParam(required = false) String kind,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listFacilitators(tenantId(), kind, limit));
    }

    @PostMapping("/facilitators/{id}/status")
    public ResponseEntity<Map<String, Object>> updateFacilitatorStatus(
            @PathVariable String id, @RequestBody Map<String, Object> body) {
        return ok(service.updateFacilitatorStatus(tenantId(), id, String.valueOf(body.getOrDefault("status", "ACTIVE"))));
    }

    @PostMapping("/venues")
    public ResponseEntity<Map<String, Object>> createVenue(@RequestBody Map<String, Object> body) {
        return ok(service.createVenue(tenantId(), actorId(), body));
    }

    @GetMapping("/venues")
    public ResponseEntity<Map<String, Object>> listVenues(@RequestParam(defaultValue = "100") int limit) {
        return ok(service.listVenues(tenantId(), limit));
    }

    @PostMapping("/cohorts/{cohortId}/facilitators")
    public ResponseEntity<Map<String, Object>> assignCohortFacilitator(
            @PathVariable String cohortId, @RequestBody Map<String, Object> body) {
        return ok(service.assignCohortFacilitator(tenantId(), cohortId, actorId(), body));
    }

    @GetMapping("/cohorts/{cohortId}/facilitators")
    public ResponseEntity<Map<String, Object>> listCohortFacilitators(@PathVariable String cohortId) {
        return ok(service.listCohortFacilitators(tenantId(), cohortId));
    }

    @PostMapping("/sessions/{sessionId}/delivery")
    public ResponseEntity<Map<String, Object>> assignSessionDelivery(
            @PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        return ok(service.assignSessionDelivery(tenantId(), sessionId, body));
    }

    private ResponseEntity<Map<String, Object>> ok(Object data) {
        return ResponseEntity.ok(Map.of("data", data == null ? Map.of() : data));
    }

    private UUID tenantId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null) return DEFAULT_TENANT;
        try {
            return UUID.fromString(ctx.tenantId());
        } catch (Exception ex) {
            return DEFAULT_TENANT;
        }
    }

    private String actorId() {
        RequestContext ctx = RequestContextHolder.get();
        if (ctx == null || ctx.principal() == null || ctx.principal().getName() == null) {
            return "system";
        }
        return ctx.principal().getName();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handle(Exception ex) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", "FUNDO_DELIVERY_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
