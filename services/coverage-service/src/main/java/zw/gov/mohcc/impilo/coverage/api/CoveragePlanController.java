package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.api.dto.CoveragePlanResponse;
import zw.gov.mohcc.impilo.coverage.api.dto.CreateCoveragePlanRequest;
import zw.gov.mohcc.impilo.coverage.domain.CoveragePlanEntity;
import zw.gov.mohcc.impilo.coverage.repository.CoveragePlanRepository;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/coverage")
public class CoveragePlanController {

    private final CoveragePlanRepository planRepository;

    public CoveragePlanController(CoveragePlanRepository planRepository) {
        this.planRepository = planRepository;
    }

    @GetMapping
    public ResponseEntity<List<CoveragePlanResponse>> listPlans(
            @RequestHeader("X-Tenant-ID") String tenantId) {
        List<CoveragePlanResponse> plans = planRepository
                .findByTenantIdAndStatus(UUID.fromString(tenantId), "ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CoveragePlanResponse> getPlan(@PathVariable UUID id) {
        return planRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<CoveragePlanResponse> createPlan(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @Valid @RequestBody CreateCoveragePlanRequest request) {
        CoveragePlanEntity plan = new CoveragePlanEntity(
                UUID.fromString(tenantId), podId,
                request.planCode(), request.planName(),
                request.payerId(), request.planType(),
                request.effectiveFrom());
        planRepository.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(plan));
    }

    private CoveragePlanResponse toResponse(CoveragePlanEntity entity) {
        return new CoveragePlanResponse(
                entity.getId(),
                entity.getPlanCode(),
                entity.getPlanName(),
                entity.getPayerId(),
                entity.getPlanType(),
                entity.getStatus(),
                entity.getEffectiveFrom(),
                entity.getEffectiveTo()
        );
    }
}
