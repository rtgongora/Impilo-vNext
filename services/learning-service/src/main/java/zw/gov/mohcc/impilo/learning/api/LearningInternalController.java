package zw.gov.mohcc.impilo.learning.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.learning.core.LearningPlatformFacade;

@RestController
@RequestMapping("/internal/v1/learning")
public class LearningInternalController {

    private final LearningPlatformFacade facade;

    public LearningInternalController(LearningPlatformFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/workflow-context")
    public ResponseEntity<Map<String, Object>> workflowContext(
            @RequestParam String appCode,
            @RequestParam String routeRef,
            @RequestParam(required = false) String workflowCode,
            @RequestParam(required = false) String roles,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<String> roleList =
                roles == null || roles.isBlank()
                        ? List.of()
                        : Arrays.stream(roles.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .collect(Collectors.toList());
        Map<String, Object> data =
                facade.resolveWorkflowSurface(tenantId, appCode, routeRef, workflowCode, roleList, subjectType, subjectId);
        return ResponseEntity.ok(Map.of("data", data));
    }

    @GetMapping("/helpdesk/{issueType}")
    public ResponseEntity<Map<String, Object>> helpdesk(
            @PathVariable String issueType,
            @RequestParam(required = false) String subjectType,
            @RequestParam(required = false) String subjectId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<Map<String, Object>> rows = facade.helpdeskSurface(tenantId, issueType, subjectType, subjectId);
        return ResponseEntity.ok(Map.of("data", Map.of("items", rows)));
    }

    @GetMapping("/search-hits")
    public ResponseEntity<Map<String, Object>> searchHits(
            @RequestParam String q, @RequestParam(defaultValue = "8") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<Map<String, Object>> results = facade.searchIndexHits(tenantId, q, limit);
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("results", results);
        envelope.put("page", 0);
        envelope.put("size", results.size());
        envelope.put("totalElements", results.size());
        envelope.put("totalPages", 1);
        return ResponseEntity.ok(Map.of("data", envelope));
    }

    @PostMapping("/resource-opened")
    public ResponseEntity<Map<String, Object>> resourceOpened(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        UUID resourceId = UUID.fromString(body.get("resourceId").toString());
        String actorId = ctx.principal() != null ? ctx.principal().getName() : "";
        String actorType = "USER";
        String correlationId = ctx.correlationId();
        @SuppressWarnings("unchecked")
        Map<String, Object> wf = (Map<String, Object>) body.get("workflowContext");
        facade.recordResourceOpened(tenantId, actorId, actorType, resourceId, correlationId, wf);
        return ResponseEntity.ok(Map.of("data", Map.of("status", "recorded")));
    }

    @GetMapping("/resources/{resourceId}")
    public ResponseEntity<?> resourceById(@PathVariable UUID resourceId) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        var view = facade.getResourcePublicView(tenantId, resourceId);
        if (view.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", view.get());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/subject-completions")
    public ResponseEntity<Map<String, Object>> subjectCompletions(
            @RequestParam String subjectType,
            @RequestParam String subjectId,
            @RequestParam(defaultValue = "25") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        List<Map<String, Object>> rows = facade.listSubjectCompletions(tenantId, subjectType, subjectId, limit);
        return ResponseEntity.ok(Map.of("data", Map.of("items", rows)));
    }

    @PostMapping("/subject-profile")
    public ResponseEntity<Map<String, Object>> upsertSubjectProfile(@RequestBody Map<String, Object> body) {
        RequestContext ctx = RequestContextHolder.require();
        UUID tenantId = UUID.fromString(ctx.tenantId());
        String subjectType = body.get("subjectType").toString();
        String subjectId = body.get("subjectId").toString();
        boolean patchFundo = body.containsKey("fundoUserRef");
        String fundoUserRef = null;
        if (patchFundo) {
            Object raw = body.get("fundoUserRef");
            fundoUserRef = raw != null ? raw.toString() : null;
        }
        boolean patchMeta = body.containsKey("metadata");
        Map<String, Object> metaPatch = null;
        if (patchMeta) {
            Object m = body.get("metadata");
            if (m instanceof Map<?, ?> rawMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cast = (Map<String, Object>) rawMap;
                metaPatch = new LinkedHashMap<>(cast);
            } else {
                metaPatch = new LinkedHashMap<>();
            }
        }
        Map<String, Object> res = facade.upsertUserLearningProfile(
                tenantId, subjectType, subjectId, patchFundo, fundoUserRef, patchMeta, metaPatch);
        return ResponseEntity.ok(Map.of("data", res));
    }
}
