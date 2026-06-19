package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.WorkforceAssignmentService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/internal/vashandi/assignments")
public class VashandiAssignmentController {

    private final WorkforceAssignmentService assignmentService;

    public VashandiAssignmentController(WorkforceAssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<WorkforceAssignmentEntity> list() {
        return assignmentService.list(tenantId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkforceAssignmentEntity> get(@PathVariable UUID id) {
        return assignmentService.get(tenantId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public WorkforceAssignmentEntity create(@RequestBody VashandiDtos.CreateAssignmentRequest request) throws Exception {
        return assignmentService.create(tenantId(), request);
    }

    @PatchMapping("/{id}")
    public WorkforceAssignmentEntity update(@PathVariable UUID id,
                                            @RequestBody VashandiDtos.UpdateAssignmentRequest request) {
        return assignmentService.update(tenantId(), id, request);
    }

    @PostMapping("/{id}/precheck")
    public VashandiDtos.AssignmentActionResponse precheck(@PathVariable UUID id,
                                                          @RequestBody(required = false) VashandiDtos.PrecheckAssignmentRequest request)
            throws Exception {
        String opaDecisionId = request != null ? request.opaDecisionId() : null;
        return assignmentService.precheck(tenantId(), id, opaDecisionId);
    }

    @PostMapping("/{id}/approve")
    public VashandiDtos.AssignmentActionResponse approve(@PathVariable UUID id) throws Exception {
        return assignmentService.approve(tenantId(), id);
    }

    @PostMapping("/{id}/activate")
    public VashandiDtos.AssignmentActionResponse activate(@PathVariable UUID id,
                                                          @RequestBody(required = false) VashandiDtos.PrecheckAssignmentRequest request)
            throws Exception {
        String opaDecisionId = request != null ? request.opaDecisionId() : null;
        return assignmentService.activate(tenantId(), id, opaDecisionId);
    }

    @PostMapping("/{id}/suspend")
    public VashandiDtos.AssignmentActionResponse suspend(@PathVariable UUID id) throws Exception {
        return assignmentService.suspend(tenantId(), id);
    }

    @PostMapping("/{id}/end")
    public VashandiDtos.AssignmentActionResponse end(@PathVariable UUID id) throws Exception {
        return assignmentService.end(tenantId(), id);
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
