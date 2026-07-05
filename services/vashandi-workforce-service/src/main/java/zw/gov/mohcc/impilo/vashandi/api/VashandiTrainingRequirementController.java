package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vashandi.core.TrainingRequirementService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.TrainingRequirementEntity;

import java.util.List;
import java.util.UUID;

/**
 * Governance surface for the role-template → required-Fundo-course mapping
 * (PO-20260629-01). Enforcement happens in WorkforceEligibilityService via the
 * learning-service training-gate; this controller only maintains the mapping.
 */
@RestController
@RequestMapping("/v1/internal/vashandi/training-requirements")
public class VashandiTrainingRequirementController {

    private final TrainingRequirementService service;

    public VashandiTrainingRequirementController(TrainingRequirementService service) {
        this.service = service;
    }

    @GetMapping
    public List<TrainingRequirementEntity> list(@RequestParam(required = false) String roleTemplateId) {
        if (roleTemplateId != null && !roleTemplateId.isBlank()) {
            return service.activeForRole(tenantId(), roleTemplateId);
        }
        return service.list(tenantId());
    }

    @PostMapping
    public TrainingRequirementEntity create(@RequestBody VashandiDtos.CreateTrainingRequirementRequest request)
            throws Exception {
        return service.create(tenantId(), request.roleTemplateId(), request.courseCode(),
                request.enforcementLevel(), request.note(), actorId());
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<TrainingRequirementEntity> deactivate(@PathVariable UUID id) throws Exception {
        return ResponseEntity.ok(service.deactivate(tenantId(), id));
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }
}
