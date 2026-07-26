package zw.gov.mohcc.impilo.tuso.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityListResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterPackMetadata;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse;
import zw.gov.mohcc.impilo.tuso.core.FacilityService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;

import java.util.List;

/**
 * Public-facing REST API for facility information.
 *
 * Returns a redacted view of facility data suitable for external consumers:
 * name, type, district, province, status, and capabilities only.
 * Contacts, internal identifiers, and sensitive operational details are excluded.
 */
@RestController
@RequestMapping("/v1/public/facilities")
public class PublicFacilityController {
    /** HAR S5 — hard cap on the anonymous public lane (register is ~7,285 rows). */
    private static final int MAX_PUBLIC_PAGE_SIZE = 100;


    private static final Logger log = LoggerFactory.getLogger(PublicFacilityController.class);

    private final FacilityService facilityService;
    private final zw.gov.mohcc.impilo.tuso.integration.RitoClient ritoClient;

    public PublicFacilityController(FacilityService facilityService,
                                    zw.gov.mohcc.impilo.tuso.integration.RitoClient ritoClient) {
        this.facilityService = facilityService;
        this.ritoClient = ritoClient;
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<FacilityResponse>> getPublicProfile(@PathVariable Long id) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching public facility profile [id={}] correlationId={}", id, ctx.correlationId());

        FacilityService.FacilityDetail detail = facilityService.getFacility(id);
        // PROVISIONAL facilities (FJ2 registrations pending verification) are not
        // publicly discoverable — profile behaves as if the record did not exist,
        // so a registrant cannot use public search to learn their case's path.
        if (zw.gov.mohcc.impilo.tuso.core.FacilityRegistrationService.STATUS_PROVISIONAL
                .equalsIgnoreCase(detail.facility().getStatus())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Facility not found: " + id);
        }
        FacilityResponse response = toPublicFacilityResponse(detail, ctx.tenantId().toString());

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<FacilityListResponse.FacilitySummary>>> publicSearch(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String facilityType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String district,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String service,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        // HAR S5 — the public lane is anonymous and the register is now ~7,285 rows; an uncapped
        // size is a cheap memory-pressure vector. Mirrors the BFF's own MAX_PAGE_SIZE discipline.
        size = Math.min(Math.max(size, 1), MAX_PUBLIC_PAGE_SIZE);
        log.info("Public facility search [query={}, type={}, province={}, district={}, service={}] correlationId={}",
                query, facilityType, province, district, service, ctx.correlationId());

        var filters = new FacilityService.FacilitySearchFilters(facilityType, status, district, province);
        // Service-aware facet: when a capability token is supplied, only facilities that carry an
        // ACTIVE matching capability are returned (registry truth). Otherwise, plain directory search.
        Page<FacilityEntity> entityPage = (service != null && !service.isBlank())
                ? facilityService.searchFacilitiesByService(
                        ctx.tenantId(), query, service, filters, PageRequest.of(page, size))
                : facilityService.searchFacilities(
                        ctx.tenantId(), query, filters, PageRequest.of(page, size));
        // PROVISIONAL rows never reach the public directory (FJ2, D-L5) — even
        // when the caller filters by status or omits it entirely.
        List<FacilityListResponse.FacilitySummary> visible = entityPage.getContent().stream()
                .filter(f -> !zw.gov.mohcc.impilo.tuso.core.FacilityRegistrationService.STATUS_PROVISIONAL
                        .equalsIgnoreCase(f.getStatus()))
                .map(this::toFacilitySummary)
                .toList();

        PagedResponse<FacilityListResponse.FacilitySummary> response = PagedResponse.of(
                visible, entityPage.getNumber(), entityPage.getSize(), entityPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: redacted public view ----

    /**
     * Maps a FacilityDetail to a redacted FacilityResponse suitable for public consumers.
     * Only includes: name, code, type, district, province, status, level, lat/lon, and capabilities.
     * Contacts, identifiers, internal operational details, and audit fields are excluded.
     */
    private FacilityResponse toPublicFacilityResponse(FacilityService.FacilityDetail detail, String tenantId) {
        FacilityEntity entity = detail.facility();

        List<FacilityResponse.CapabilityDetail> capabilities = detail.capabilities() != null
                ? detail.capabilities().stream()
                    .map(c -> new FacilityResponse.CapabilityDetail(
                            c.getId(), c.getCapabilityCode(), c.getCapabilityType(),
                            c.getName(),
                            c.getZiboValidated() != null && c.getZiboValidated(),
                            c.getActive() != null && c.getActive(),
                            c.getOperatingHours(), c.getMetadata()))
                    .toList()
                : List.of();

        return new FacilityResponse(
                entity.getId(),
                entity.getFacilityUuid() != null ? entity.getFacilityUuid().toString() : null,
                entity.getName(),
                entity.getFacilityCode(),
                entity.getFacilityType(),
                entity.getProvince(),
                entity.getDistrict(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getStatus(),
                null, // operationalStatus — redacted
                null, // ownership — redacted
                entity.getLevel(),
                entity.getFacilityCategory(),
                toOperatingModel(entity),
                null, // parentId — redacted
                null, // parentName — redacted
                null, // description — redacted
                null, // openedDate — redacted
                null, // closedDate — redacted
                null, // closeReason — redacted
                null, // mergedIntoId — redacted
                null, // version — redacted
                null, // identifiers — redacted
                null, // contacts — redacted
                null, // geo — redacted
                capabilities,
                null, // readiness — redacted
                null, // createdAt — redacted
                null, // updatedAt — redacted
                null, // createdBy — redacted
                null, // updatedBy — redacted
                // HPA enrichment disclosure — honest source/verification/completeness (never implies open).
                metaString(entity, "source_system"),
                metaString(entity, "source_effective_date"),
                metaString(entity, "verification_status"),
                metaBool(entity, "geospatial_incomplete") || metaBool(entity, "missing_operating_status"),
                metaBool(entity, "site_resolution_unresolved"),
                // Read-only, Rito-sourced verified facility-experience summary (gated, fail-open).
                entity.getFacilityUuid() != null
                        ? ritoClient.getFacilityExperience(entity.getFacilityUuid().toString(), tenantId)
                        : null
        );
    }

    private static String metaString(FacilityEntity entity, String key) {
        java.util.Map<String, Object> m = entity.getMetadata();
        Object v = m == null ? null : m.get(key);
        return v == null ? null : String.valueOf(v);
    }

    private static Boolean metaBool(FacilityEntity entity, String key) {
        java.util.Map<String, Object> m = entity.getMetadata();
        Object v = m == null ? null : m.get(key);
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }

    private FacilityResponse.OperatingModelDetail toOperatingModel(FacilityEntity entity) {
        if (entity.getFacilityTier() == null
                && entity.getDeploymentMode() == null
                && entity.getContinuityClass() == null
                && entity.getWorkflowArchetype() == null) {
            return null;
        }

        return new FacilityResponse.OperatingModelDetail(
                entity.getFacilityTier(),
                entity.getDeploymentMode(),
                entity.getContinuityClass(),
                entity.getWorkflowArchetype()
        );
    }

    private FacilityListResponse.FacilitySummary toFacilitySummary(FacilityEntity entity) {
        FacilityMasterPackMetadata.Flags pack = FacilityMasterPackMetadata.from(entity);
        return new FacilityListResponse.FacilitySummary(
                entity.getId(),
                entity.getFacilityUuid() != null ? entity.getFacilityUuid().toString() : null,
                entity.getName(),
                entity.getFacilityCode(),
                entity.getFacilityType(),
                entity.getStatus(),
                entity.getDistrict(),
                entity.getProvince(),
                entity.getLatitude(),
                entity.getLongitude(),
                entity.getOwnership(),
                entity.getLevel(),
                entity.getOperationalStatus(),
                pack.facilityUid(),
                pack.hasValidCoordinates(),
                pack.missingFacilityCode(),
                pack.locationContext(),
                pack.bedCapacity()
        );
    }
}
