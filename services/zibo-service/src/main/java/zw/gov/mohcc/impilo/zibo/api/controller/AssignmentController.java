package zw.gov.mohcc.impilo.zibo.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.zibo.api.dto.AssignPackRequest;
import zw.gov.mohcc.impilo.zibo.core.GovernanceService;
import zw.gov.mohcc.impilo.zibo.domain.PolicyMode;
import zw.gov.mohcc.impilo.zibo.domain.ScopeType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.AssignmentEntity;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing terminology pack assignments.
 *
 * <p>Assignments bind terminology packs to scopes (tenant, facility,
 * workspace) with an associated policy mode for enforcement.</p>
 */
@RestController
@RequestMapping("/v1/assignments")
public class AssignmentController {

    private static final Logger log = LoggerFactory.getLogger(AssignmentController.class);

    private final GovernanceService governanceService;

    public AssignmentController(GovernanceService governanceService) {
        this.governanceService = governanceService;
    }

    /**
     * Assign a terminology pack to a scope.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<AssignmentEntity>> assignPack(
            @Valid @RequestBody AssignPackRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        PolicyMode mode = request.policyMode() != null ? request.policyMode() : PolicyMode.LENIENT;

        AssignmentEntity assignment = governanceService.assignPack(
                request.scopeType(),
                request.scopeId(),
                request.packId(),
                request.packVersion(),
                mode);

        return ResponseEntity.ok(ApiResponse.ok(assignment, correlationId));
    }

    /**
     * Remove (deactivate) a pack assignment.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<String>> unassignPack(@PathVariable UUID id) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        governanceService.unassignPack(id);

        return ResponseEntity.ok(ApiResponse.ok("Assignment deactivated", correlationId));
    }

    /**
     * Get all assignments for a given scope.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<AssignmentEntity>>> getAssignments(
            @RequestParam ScopeType scopeType,
            @RequestParam UUID scopeId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();

        List<AssignmentEntity> assignments = governanceService.getAssignments(scopeType, scopeId);

        return ResponseEntity.ok(ApiResponse.ok(assignments, correlationId));
    }
}
