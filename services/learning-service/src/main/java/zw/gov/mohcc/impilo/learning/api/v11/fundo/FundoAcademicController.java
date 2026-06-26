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
import zw.gov.mohcc.impilo.learning.fundo.FundoAcademicService;

/** Academic program + term administration (A4). */
@RestController
@RequestMapping("/internal/v1/learning/v11/academic")
public class FundoAcademicController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoAcademicService service;

    public FundoAcademicController(FundoAcademicService service) {
        this.service = service;
    }

    @PostMapping("/programs")
    public ResponseEntity<Map<String, Object>> createProgram(@RequestBody Map<String, Object> body) {
        return ok(service.createProgram(tenantId(), actorId(), body));
    }

    @GetMapping("/programs")
    public ResponseEntity<Map<String, Object>> listPrograms(@RequestParam(defaultValue = "100") int limit) {
        return ok(service.listPrograms(tenantId(), limit));
    }

    @GetMapping("/programs/{programId}")
    public ResponseEntity<Map<String, Object>> getProgram(@PathVariable String programId) {
        return ok(service.getProgram(tenantId(), programId));
    }

    @PostMapping("/terms")
    public ResponseEntity<Map<String, Object>> createTerm(@RequestBody Map<String, Object> body) {
        return ok(service.createTerm(tenantId(), actorId(), body));
    }

    @GetMapping("/terms")
    public ResponseEntity<Map<String, Object>> listTerms(
            @RequestParam(required = false) String programId,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listTerms(tenantId(), programId, limit));
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
        error.put("code", "FUNDO_ACADEMIC_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
