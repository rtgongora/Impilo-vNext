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
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.fundo.FundoAttendanceService;

/** Session attendance + check-in endpoints (A2). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoAttendanceController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoAttendanceService service;

    public FundoAttendanceController(FundoAttendanceService service) {
        this.service = service;
    }

    @PostMapping("/sessions/{sessionId}/checkin-tokens")
    public ResponseEntity<Map<String, Object>> createToken(
            @PathVariable String sessionId, @RequestBody(required = false) Map<String, Object> body) {
        return ok(service.createCheckinToken(tenantId(), sessionId, actorId(), body == null ? Map.of() : body));
    }

    @PostMapping("/sessions/{sessionId}/attendance")
    public ResponseEntity<Map<String, Object>> markAttendance(
            @PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        return ok(service.markAttendance(tenantId(), sessionId, actorId(), body));
    }

    @PostMapping("/sessions/{sessionId}/checkin")
    public ResponseEntity<Map<String, Object>> selfCheckin(
            @PathVariable String sessionId, @RequestBody Map<String, Object> body) {
        return ok(service.selfCheckin(tenantId(), sessionId, body));
    }

    @GetMapping("/sessions/{sessionId}/attendance")
    public ResponseEntity<Map<String, Object>> listAttendance(@PathVariable String sessionId) {
        return ok(service.listAttendance(tenantId(), sessionId));
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
        error.put("code", "FUNDO_ATTENDANCE_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
