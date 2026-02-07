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
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.CreateFacilityRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityContactDto;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityIdentifierDto;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityListResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilitySearchRequest;
import zw.gov.mohcc.impilo.tuso.api.dto.UpdateFacilityRequest;
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

    public FacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
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

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
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

        PagedResponse<FacilityListResponse.FacilitySummary> response = PagedResponse.from(page);

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
                api.description()
        );
    }

    // ---- Mapper: Entity / Detail → Response DTO ----

    private FacilityResponse toFacilityResponse(FacilityEntity entity) {
        return new FacilityResponse(
                entity.getId(),
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
                entity.getUpdatedBy()
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
                entity.getUpdatedBy()
        );
    }

    private FacilityListResponse.FacilitySummary toFacilitySummary(FacilityEntity entity) {
        return new FacilityListResponse.FacilitySummary(
                entity.getId(),
                entity.getName(),
                entity.getFacilityCode(),
                entity.getFacilityType(),
                entity.getStatus(),
                entity.getDistrict(),
                entity.getProvince()
        );
    }
}
