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
import zw.gov.mohcc.impilo.learning.fundo.FundoAccreditationService;

/** Learning-provider request → review → accredit → provision workflow (C2). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoAccreditationController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoAccreditationService service;

    public FundoAccreditationController(FundoAccreditationService service) {
        this.service = service;
    }

    @PostMapping("/provider-applications")
    public ResponseEntity<Map<String, Object>> submit(@RequestBody Map<String, Object> body) {
        return ok(service.submit(tenantId(), body));
    }

    @GetMapping("/provider-applications")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listApplications(tenantId(), status, limit));
    }

    @PostMapping("/provider-applications/{applicationId}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @PathVariable String applicationId, @RequestBody Map<String, Object> body) {
        return ok(service.decide(tenantId(), applicationId, actorId(), body));
    }

    @PostMapping("/provider-applications/{applicationId}/accredit")
    public ResponseEntity<Map<String, Object>> accredit(
            @PathVariable String applicationId, @RequestBody(required = false) Map<String, Object> body) {
        return ok(service.accredit(tenantId(), applicationId, actorId(), body == null ? Map.of() : body));
    }

    @GetMapping("/providers/{providerId}/accreditations")
    public ResponseEntity<Map<String, Object>> listAccreditations(@PathVariable String providerId) {
        return ok(service.listAccreditations(tenantId(), providerId));
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
        error.put("code", "FUNDO_ACCREDITATION_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
