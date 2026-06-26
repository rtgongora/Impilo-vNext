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
import zw.gov.mohcc.impilo.learning.fundo.FundoAssignmentService;

/** Assignments / tasks + submissions + marking queue (A3). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoAssignmentController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoAssignmentService service;

    public FundoAssignmentController(FundoAssignmentService service) {
        this.service = service;
    }

    @PostMapping("/assignments")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ok(service.createAssignment(tenantId(), actorId(), body));
    }

    @GetMapping("/assignments")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String courseId,
            @RequestParam(required = false) String cohortId,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listAssignments(tenantId(), courseId, cohortId, limit));
    }

    @GetMapping("/assignments/{assignmentId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String assignmentId) {
        return ok(service.getAssignment(tenantId(), assignmentId));
    }

    @PostMapping("/assignments/{assignmentId}/submissions")
    public ResponseEntity<Map<String, Object>> submit(
            @PathVariable String assignmentId, @RequestBody Map<String, Object> body) {
        return ok(service.submit(tenantId(), assignmentId, body));
    }

    @GetMapping("/assignments/{assignmentId}/submissions")
    public ResponseEntity<Map<String, Object>> listSubmissions(@PathVariable String assignmentId) {
        return ok(service.listSubmissions(tenantId(), assignmentId));
    }

    @GetMapping("/marking-queue")
    public ResponseEntity<Map<String, Object>> markingQueue(
            @RequestParam(required = false) String cohortId,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.markingQueue(tenantId(), cohortId, limit));
    }

    @PostMapping("/submissions/{submissionId}/mark")
    public ResponseEntity<Map<String, Object>> mark(
            @PathVariable String submissionId, @RequestBody Map<String, Object> body) {
        return ok(service.mark(tenantId(), submissionId, actorId(), body));
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
        error.put("code", "FUNDO_ASSIGNMENT_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
