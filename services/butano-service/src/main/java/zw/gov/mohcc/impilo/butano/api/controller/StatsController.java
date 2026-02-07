package zw.gov.mohcc.impilo.butano.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.butano.core.ResourceStatsService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.Map;

/**
 * REST controller for SHR resource statistics.
 *
 * Returns resource counts per FHIR resource type for the current tenant.
 * Used by administrative dashboards and monitoring tooling.
 */
@RestController
@RequestMapping("/v1/internal")
public class StatsController {

    private static final Logger log = LoggerFactory.getLogger(StatsController.class);

    private final ResourceStatsService resourceStatsService;

    public StatsController(ResourceStatsService resourceStatsService) {
        this.resourceStatsService = resourceStatsService;
    }

    /**
     * Get resource counts for key FHIR resource types.
     *
     * Returns a map of { resourceType: count } for Patient, Encounter,
     * Condition, Observation, MedicationRequest, Immunization, DiagnosticReport,
     * Procedure, AllergyIntolerance, DocumentReference, ServiceRequest, CarePlan.
     *
     * @return resource counts scoped to the current tenant
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getResourceStats() {
        TrustContext ctx = TrustContextHolder.require();
        log.info("Resource stats requested by actor={} for tenant={}", ctx.actorId(), ctx.tenantId());

        Map<String, Long> counts = resourceStatsService.getResourceCounts();

        return ResponseEntity.ok(
                ApiResponse.ok(counts, ctx.correlationId().toString()));
    }
}
