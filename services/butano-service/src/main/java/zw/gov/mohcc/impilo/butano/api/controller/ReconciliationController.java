package zw.gov.mohcc.impilo.butano.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.butano.api.dto.ReconcileSubjectRequest;
import zw.gov.mohcc.impilo.butano.core.ReconciliationService;
import zw.gov.mohcc.impilo.butano.persistence.entity.ReconciliationMappingEntity;
import zw.gov.mohcc.impilo.butano.persistence.entity.TenantRegistryEntity;
import zw.gov.mohcc.impilo.butano.persistence.repository.ReconciliationMappingRepository;
import zw.gov.mohcc.impilo.butano.persistence.repository.TenantRegistryRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REST controller for internal SHR reconciliation and diagnostics.
 *
 * Provides endpoints for:
 * - Triggering O-CPID to CPID subject reconciliation
 * - Checking reconciliation status
 * - Listing registered tenants (diagnostic)
 */
@RestController
@RequestMapping("/v1/internal")
public class ReconciliationController {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationController.class);

    private final ReconciliationService reconciliationService;
    private final ReconciliationMappingRepository mappingRepository;
    private final TenantRegistryRepository tenantRegistryRepository;

    public ReconciliationController(ReconciliationService reconciliationService,
                                    ReconciliationMappingRepository mappingRepository,
                                    TenantRegistryRepository tenantRegistryRepository) {
        this.reconciliationService = reconciliationService;
        this.mappingRepository = mappingRepository;
        this.tenantRegistryRepository = tenantRegistryRepository;
    }

    /**
     * Trigger reconciliation of an O-CPID to a permanent CPID.
     *
     * Creates a reconciliation mapping and triggers async rewriting of all
     * FHIR resource subject/patient references from the old CPID to the new one.
     *
     * @param request contains oldCpid, newCpid, and optional reason
     * @return 202 Accepted with mapping ID for status tracking
     */
    @PostMapping("/reconcile-subject")
    public ResponseEntity<ApiResponse<Map<String, Object>>> reconcileSubject(
            @Valid @RequestBody ReconcileSubjectRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Reconciliation requested: O-CPID={} -> CPID={} by actor={} reason={}",
                request.oldCpid(), request.newCpid(), ctx.actorId(), request.reason());

        // Check for existing mapping (idempotency)
        Optional<ReconciliationMappingEntity> existing = mappingRepository
                .findByTenantIdAndOldCpid(ctx.tenantId(), request.oldCpid());

        if (existing.isPresent()) {
            ReconciliationMappingEntity existingMapping = existing.get();
            log.info("Existing reconciliation mapping found: id={} status={}",
                    existingMapping.getId(), existingMapping.getStatus());

            Map<String, Object> data = buildMappingResponse(existingMapping);
            data.put("message", "Reconciliation already exists for this O-CPID");

            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.ok(data, ctx.correlationId().toString()));
        }

        // Create new mapping
        ReconciliationMappingEntity mapping = new ReconciliationMappingEntity();
        mapping.setTenantId(ctx.tenantId());
        mapping.setOldCpid(request.oldCpid());
        mapping.setNewCpid(request.newCpid());
        mapping.setRequestedBy(ctx.actorId());
        mapping.setCorrelationId(ctx.correlationId().toString());
        mapping = mappingRepository.save(mapping);

        // Trigger async reconciliation
        reconciliationService.reconcile(mapping);

        Map<String, Object> data = buildMappingResponse(mapping);
        data.put("message", "Reconciliation initiated");

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    /**
     * Check the status of a reconciliation mapping.
     *
     * @param id the reconciliation mapping ID
     * @return current status, resource count, and timestamps
     */
    @GetMapping("/reconciliation/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getReconciliationStatus(
            @PathVariable Long id) {

        TrustContext ctx = TrustContextHolder.require();

        Optional<ReconciliationMappingEntity> mapping = mappingRepository.findById(id);
        if (mapping.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND",
                            "Reconciliation mapping not found: " + id,
                            HttpStatus.NOT_FOUND.value(),
                            ctx.correlationId().toString()));
        }

        ReconciliationMappingEntity entity = mapping.get();

        // Tenant isolation: only return mappings from the same tenant
        if (!entity.getTenantId().equals(ctx.tenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("NOT_FOUND",
                            "Reconciliation mapping not found: " + id,
                            HttpStatus.NOT_FOUND.value(),
                            ctx.correlationId().toString()));
        }

        Map<String, Object> data = buildMappingResponse(entity);
        return ResponseEntity.ok(ApiResponse.ok(data, ctx.correlationId().toString()));
    }

    /**
     * List all registered tenants. Diagnostic endpoint for ops tooling.
     *
     * @return list of active tenant registrations
     */
    @GetMapping("/health/tenants")
    public ResponseEntity<ApiResponse<List<TenantRegistryEntity>>> listTenants() {
        TrustContext ctx = TrustContextHolder.require();
        log.debug("Tenant list requested by actor={}", ctx.actorId());

        List<TenantRegistryEntity> tenants = tenantRegistryRepository.findAllByActiveTrue();
        return ResponseEntity.ok(
                ApiResponse.ok(tenants, ctx.correlationId().toString()));
    }

    private Map<String, Object> buildMappingResponse(ReconciliationMappingEntity entity) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", entity.getId());
        data.put("oldCpid", entity.getOldCpid());
        data.put("newCpid", entity.getNewCpid());
        data.put("status", entity.getStatus());
        data.put("resourcesUpdated", entity.getResourcesUpdated());
        data.put("requestedBy", entity.getRequestedBy());
        data.put("requestedAt", entity.getRequestedAt());
        data.put("completedAt", entity.getCompletedAt());
        if (entity.getErrorMessage() != null) {
            data.put("errorMessage", entity.getErrorMessage());
        }
        return data;
    }
}
