package zw.gov.mohcc.impilo.tuso.api.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.visibility.AggregateVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityContextHolder;
import zw.gov.mohcc.impilo.tuso.api.policy.FacilityRepresentation;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.CreateFacilityRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityContactDto;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityIdentifierDto;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityListResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityMasterPackMetadata;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySearchRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityStatusSummary;
import zw.gov.mohcc.impilo.tuso.api.dto.UpdateFacilityRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityCompletenessDtos;
import zw.gov.mohcc.impilo.tuso.core.FacilityCompletenessService;
import zw.gov.mohcc.impilo.tuso.core.FacilityProfileCompletionService;
import zw.gov.mohcc.impilo.tuso.core.FacilityService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityGeoEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityReadinessEntity;

import java.util.List;

/**
 * Internal REST API for facility management within the TUSO Facility Registry.
 *
 * All endpoints require a valid trust context provided by TSHEPO ext_authz.
 */
@RestController
@RequestMapping("/v1/internal/facilities")
public class FacilityController {

    private static final Logger log = LoggerFactory.getLogger(FacilityController.class);

    private final FacilityService facilityService;
    private final FacilityCompletenessService completenessService;
    private final FacilityProfileCompletionService profileCompletionService;

    public FacilityController(FacilityService facilityService,
                              FacilityCompletenessService completenessService,
                              FacilityProfileCompletionService profileCompletionService) {
        this.facilityService = facilityService;
        this.completenessService = completenessService;
        this.profileCompletionService = profileCompletionService;
    }

    /**
     * Governed completion of missing facility profile fields (esp. geocodes) from the
     * facility-mode completeness prompt. FAIL-CLOSED: only an ACTIVE facility
     * administrator or the active practitioner-in-charge at this facility may write
     * (403 otherwise, enforced in the service on the TrustContext actor id).
     */
    @PostMapping("/{id}/complete-profile")
    public ResponseEntity<ApiResponse<FacilityCompletenessService.FacilityCompleteness>> completeProfile(
            @PathVariable Long id,
            @RequestBody FacilityCompletenessDtos.CompleteFacilityProfileRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Completing facility profile [id={}, actor={}] correlationId={}",
                id, ctx.actorId(), ctx.correlationId());

        FacilityCompletenessService.FacilityCompleteness completeness =
                profileCompletionService.completeProfile(id, request);
        return ResponseEntity.ok(ApiResponse.ok(completeness, ctx.correlationId().toString()));
    }

    /**
     * Governed profile-completeness verdict for a facility (computed on read; no table).
     * Consumed by the facility-mode cockpit to prompt the facility administrator / PIC
     * to complete missing governed fields, especially geocodes.
     */
    @GetMapping("/{id}/completeness")
    public ResponseEntity<ApiResponse<FacilityCompletenessService.FacilityCompleteness>> getCompleteness(
            @PathVariable Long id) {

        TrustContext ctx = TrustContextHolder.require();
        FacilityCompletenessService.FacilityCompleteness completeness =
                completenessService.computeCompleteness(id);
        return ResponseEntity.ok(ApiResponse.ok(completeness, ctx.correlationId().toString()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FacilityResponse>> createFacility(
            @Valid @RequestBody CreateFacilityRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Creating facility [code={}, name={}] correlationId={}",
                request.facilityCode(), request.name(), ctx.correlationId());

        FacilityEntity entity = facilityService.createFacility(toServiceCreateRequest(request));
        FacilityResponse response = toFacilityResponse(entity);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<FacilityResponse>> getFacility(@PathVariable Long id) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching facility [id={}] correlationId={}", id, ctx.correlationId());

        FacilityService.FacilityDetail detail = facilityService.getFacility(id);
        FacilityResponse response = toDetailResponse(detail);

        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("VISIBILITY_AGGREGATE_ONLY",
                            "Facility detail is not available under aggregate-only visibility.",
                            HttpStatus.FORBIDDEN.value(),
                            ctx.correlationId().toString()));
        }
        response = FacilityRepresentation.apply(response, vis);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Resolve a facility by its canonical cross-service UUID ({@code facility_uuid}).
     * Bridges UUID-keyed consumers (PCT overlay, staff binding, session contract)
     * to the registry; same visibility guard as the bigint detail read.
     */
    @GetMapping("/by-uid/{facilityUuid}")
    public ResponseEntity<ApiResponse<FacilityResponse>> getFacilityByUid(
            @PathVariable java.util.UUID facilityUuid) {

        TrustContext ctx = TrustContextHolder.require();
        FacilityService.FacilityDetail detail = facilityService.getFacilityByUuid(facilityUuid);
        FacilityResponse response = toDetailResponse(detail);

        VisibilityProfile vis = VisibilityContextHolder.current().orElse(null);
        if (AggregateVisibilityGuard.blocksRowLevelDetail(vis)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error("VISIBILITY_AGGREGATE_ONLY",
                            "Facility detail is not available under aggregate-only visibility.",
                            HttpStatus.FORBIDDEN.value(),
                            ctx.correlationId().toString()));
        }
        response = FacilityRepresentation.apply(response, vis);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    /**
     * Lightweight status reference endpoint for cross-service legitimacy checks.
     * Used by VARAPI (PIC assignment) and commerce/booking flows.
     */
    @GetMapping("/{id}/status-summary")
    public ResponseEntity<ApiResponse<FacilityStatusSummary>> getStatusSummary(@PathVariable Long id) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityService.FacilityDetail detail = facilityService.getFacility(id);
        FacilityEntity facility = detail.facility();
        FacilityStatusSummary summary = new FacilityStatusSummary(
                facility.getId(),
                facility.getFacilityCode(),
                facility.getName(),
                facility.getStatus(),
                facility.getOperationalStatus(),
                facility.getRegulatoryStatus()
        );
        return ResponseEntity.ok(ApiResponse.ok(summary, ctx.correlationId().toString()));
    }

    @PostMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<FacilityListResponse.FacilitySummary>>> searchFacilities(
            @Valid @RequestBody FacilitySearchRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Searching facilities [query={}, type={}, province={}, district={}] correlationId={}",
                request.query(), request.facilityType(), request.province(),
                request.district(), ctx.correlationId());

        var filters = new FacilityService.FacilitySearchFilters(
                request.facilityType(), request.status(), request.district(), request.province());
        Page<FacilityEntity> entityPage =
                facilityService.searchFacilities(
                        ctx.tenantId(), request.query(), filters,
                        PageRequest.of(request.page(), request.size()));
        Page<FacilityListResponse.FacilitySummary> page = entityPage.map(this::toFacilitySummary);

        PagedResponse<FacilityListResponse.FacilitySummary> response = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<FacilityResponse>> updateFacility(
            @PathVariable Long id,
            @Valid @RequestBody UpdateFacilityRequest request) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Updating facility [id={}] correlationId={}", id, ctx.correlationId());

        FacilityEntity entity = facilityService.updateFacility(id, toServiceUpdateRequest(request));
        FacilityResponse response = toFacilityResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/merge")
    public ResponseEntity<ApiResponse<FacilityResponse>> mergeFacility(
            @PathVariable Long id,
            @RequestParam Long targetId) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Merging facility [sourceId={}, targetId={}] correlationId={}",
                id, targetId, ctx.correlationId());

        FacilityEntity entity = facilityService.mergeFacility(id, targetId);
        FacilityResponse response = toFacilityResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    @PostMapping("/{id}/close")
    public ResponseEntity<ApiResponse<FacilityResponse>> closeFacility(
            @PathVariable Long id,
            @RequestParam String reason) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Closing facility [id={}, reason={}] correlationId={}",
                id, reason, ctx.correlationId());

        FacilityEntity entity = facilityService.closeFacility(id, reason);
        FacilityResponse response = toFacilityResponse(entity);

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: API DTO → Service DTO ----

    private FacilityService.CreateFacilityRequest toServiceCreateRequest(CreateFacilityRequest api) {
        List<FacilityService.IdentifierData> identifiers = api.identifiers() != null
                ? api.identifiers().stream()
                    .map(i -> new FacilityService.IdentifierData(i.system(), i.value()))
                    .toList()
                : null;

        List<FacilityService.ContactData> contacts = api.contacts() != null
                ? api.contacts().stream()
                    .map(c -> new FacilityService.ContactData(
                            c.contactType(), c.name(), c.phone(), c.email(), c.role()))
                    .toList()
                : null;

        List<FacilityService.CapabilityData> capabilities = api.capabilities() != null
                ? api.capabilities().stream()
                    .map(code -> new FacilityService.CapabilityData(code, null, null, null, null))
                    .toList()
                : null;

        return new FacilityService.CreateFacilityRequest(
                api.facilityCode(),
                api.name(),
                api.facilityType(),
                api.province(),
                api.district(),
                api.latitude(),
                api.longitude(),
                null, // operationalStatus — not in API DTO
                api.ownership(),
                api.level(),
                api.facilityTier(),
                api.deploymentMode(),
                api.continuityClass(),
                api.workflowArchetype(),
                api.description(),
                api.openedDate(),
                api.parentId(),
                identifiers,
                contacts,
                null, // geo — not in API DTO
                capabilities,
                null  // readiness — not in API DTO
        );
    }

    private FacilityService.UpdateFacilityRequest toServiceUpdateRequest(UpdateFacilityRequest api) {
        return new FacilityService.UpdateFacilityRequest(
                api.name(),
                api.facilityType(),
                api.province(),
                api.district(),
                api.latitude(),
                api.longitude(),
                null, // operationalStatus — not in API DTO
                api.ownership(),
                api.level(),
                api.facilityTier(),
                api.deploymentMode(),
                api.continuityClass(),
                api.workflowArchetype(),
                api.description()
        );
    }

    // ---- Mapper: Entity / Detail → Response DTO ----

    private FacilityResponse toFacilityResponse(FacilityEntity entity) {
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
                entity.getOperationalStatus(),
                entity.getOwnership(),
                entity.getLevel(),
                entity.getFacilityCategory(),
                toOperatingModel(entity),
                entity.getParent() != null ? entity.getParent().getId() : null,
                null, // parentName — would require lazy load
                entity.getDescription(),
                entity.getOpenedDate(),
                entity.getClosedDate(),
                entity.getCloseReason(),
                entity.getMergedInto() != null ? entity.getMergedInto().getId() : null,
                entity.getVersion(),
                null, // identifiers — not loaded for single-entity returns
                null, // contacts
                null, // geo
                null, // capabilities
                null, // readiness
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                null, null, null, null, null // HPA disclosure — public-profile only
        );
    }

    private FacilityResponse toDetailResponse(FacilityService.FacilityDetail detail) {
        FacilityEntity entity = detail.facility();

        List<FacilityIdentifierDto> identifiers = detail.identifiers() != null
                ? detail.identifiers().stream()
                    .map(i -> new FacilityIdentifierDto(i.getSystem(), i.getValue()))
                    .toList()
                : List.of();

        List<FacilityContactDto> contacts = detail.contacts() != null
                ? detail.contacts().stream()
                    .map(c -> new FacilityContactDto(
                            c.getContactType(), c.getName(), c.getPhone(),
                            c.getEmail(), c.getRole()))
                    .toList()
                : List.of();

        FacilityResponse.GeoDetail geo = null;
        if (detail.geo() != null) {
            FacilityGeoEntity g = detail.geo();
            geo = new FacilityResponse.GeoDetail(
                    g.getAddressLine1(), g.getAddressLine2(), g.getCity(),
                    g.getProvince(), g.getDistrict(), g.getWard(),
                    g.getPostalCode(), g.getCountry(), g.getAltitudeM(),
                    g.getCatchmentArea());
        }

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

        FacilityResponse.ReadinessDetail readiness = null;
        if (detail.readiness() != null) {
            FacilityReadinessEntity r = detail.readiness();
            readiness = new FacilityResponse.ReadinessDetail(
                    r.getConnectivity(), r.getPowerSource(),
                    r.getPowerBackup() != null && r.getPowerBackup(),
                    r.getDeviceCount() != null ? r.getDeviceCount() : 0,
                    r.getEhrReady() != null && r.getEhrReady(),
                    r.getComplianceFlags(),
                    r.getAssessedAt(), r.getAssessedBy());
        }

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
                entity.getOperationalStatus(),
                entity.getOwnership(),
                entity.getLevel(),
                entity.getFacilityCategory(),
                toOperatingModel(entity),
                entity.getParent() != null ? entity.getParent().getId() : null,
                null, // parentName
                entity.getDescription(),
                entity.getOpenedDate(),
                entity.getClosedDate(),
                entity.getCloseReason(),
                entity.getMergedInto() != null ? entity.getMergedInto().getId() : null,
                entity.getVersion(),
                identifiers,
                contacts,
                geo,
                capabilities,
                readiness,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getCreatedBy(),
                entity.getUpdatedBy(),
                null, null, null, null, null // HPA disclosure — public-profile only
        );
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
