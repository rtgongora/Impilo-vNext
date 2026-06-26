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
import zw.gov.mohcc.impilo.learning.fundo.FundoRegistrationService;
import zw.gov.mohcc.impilo.learning.fundo.FundoStudentService;

/** Pre-service admission + student registry (A5) + registration/placement/graduation (A6). */
@RestController
@RequestMapping("/internal/v1/learning/v11")
public class FundoStudentController {

    private static final UUID DEFAULT_TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final FundoStudentService service;
    private final FundoRegistrationService registrationService;

    public FundoStudentController(FundoStudentService service, FundoRegistrationService registrationService) {
        this.service = service;
        this.registrationService = registrationService;
    }

    @PostMapping("/admissions/applications")
    public ResponseEntity<Map<String, Object>> submitApplication(@RequestBody Map<String, Object> body) {
        return ok(service.submitApplication(tenantId(), body));
    }

    @GetMapping("/admissions/applications")
    public ResponseEntity<Map<String, Object>> listApplications(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String programId,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listApplications(tenantId(), status, programId, limit));
    }

    @PostMapping("/admissions/applications/{applicationId}/decision")
    public ResponseEntity<Map<String, Object>> decide(
            @PathVariable String applicationId, @RequestBody Map<String, Object> body) {
        return ok(service.decideApplication(tenantId(), applicationId, actorId(), body));
    }

    @PostMapping("/students")
    public ResponseEntity<Map<String, Object>> admit(@RequestBody Map<String, Object> body) {
        return ok(service.admitStudent(tenantId(), actorId(), body));
    }

    @GetMapping("/students")
    public ResponseEntity<Map<String, Object>> listStudents(
            @RequestParam(required = false) String programId,
            @RequestParam(defaultValue = "100") int limit) {
        return ok(service.listStudents(tenantId(), programId, limit));
    }

    @GetMapping("/students/{studentId}")
    public ResponseEntity<Map<String, Object>> getStudent(@PathVariable String studentId) {
        return ok(service.getStudent(tenantId(), studentId));
    }

    // ---- A6: registration / placement / graduation ----

    @PostMapping("/students/{studentId}/registrations")
    public ResponseEntity<Map<String, Object>> register(
            @PathVariable String studentId, @RequestBody Map<String, Object> body) {
        return ok(registrationService.registerCourse(tenantId(), studentId, actorId(), body));
    }

    @GetMapping("/students/{studentId}/registrations")
    public ResponseEntity<Map<String, Object>> listRegistrations(@PathVariable String studentId) {
        return ok(registrationService.listRegistrations(tenantId(), studentId));
    }

    @PostMapping("/students/{studentId}/placements")
    public ResponseEntity<Map<String, Object>> createPlacement(
            @PathVariable String studentId, @RequestBody Map<String, Object> body) {
        return ok(registrationService.createPlacement(tenantId(), studentId, actorId(), body));
    }

    @GetMapping("/students/{studentId}/placements")
    public ResponseEntity<Map<String, Object>> listPlacements(@PathVariable String studentId) {
        return ok(registrationService.listPlacements(tenantId(), studentId));
    }

    @PostMapping("/placements/{placementId}/signoff")
    public ResponseEntity<Map<String, Object>> signoffPlacement(
            @PathVariable String placementId, @RequestBody Map<String, Object> body) {
        return ok(registrationService.signoffPlacement(tenantId(), placementId, actorId(), body));
    }

    @PostMapping("/students/{studentId}/graduate")
    public ResponseEntity<Map<String, Object>> graduate(
            @PathVariable String studentId, @RequestBody(required = false) Map<String, Object> body) {
        return ok(registrationService.graduate(tenantId(), studentId, actorId(), body == null ? Map.of() : body));
    }

    @GetMapping("/students/{studentId}/academic-record")
    public ResponseEntity<Map<String, Object>> academicRecord(@PathVariable String studentId) {
        return ok(registrationService.academicRecord(tenantId(), studentId));
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
        error.put("code", "FUNDO_STUDENT_ERROR");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", error));
    }
}
