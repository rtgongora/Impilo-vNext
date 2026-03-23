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

    private static final Logger log = LoggerFactory.getLogger(PublicFacilityController.class);

    private final FacilityService facilityService;

    public PublicFacilityController(FacilityService facilityService) {
        this.facilityService = facilityService;
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<ApiResponse<FacilityResponse>> getPublicProfile(@PathVariable Long id) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Fetching public facility profile [id={}] correlationId={}", id, ctx.correlationId());

        FacilityService.FacilityDetail detail = facilityService.getFacility(id);
        FacilityResponse response = toPublicFacilityResponse(detail);

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        TrustContext ctx = TrustContextHolder.require();
        log.info("Public facility search [query={}, type={}, province={}, district={}] correlationId={}",
                query, facilityType, province, district, ctx.correlationId());

        var filters = new FacilityService.FacilitySearchFilters(facilityType, status, district, province);
        Page<FacilityEntity> entityPage = facilityService.searchFacilities(
                ctx.tenantId(), query, filters, PageRequest.of(page, size));
        Page<FacilityListResponse.FacilitySummary> resultPage = entityPage.map(this::toFacilitySummary);

        PagedResponse<FacilityListResponse.FacilitySummary> response = PagedResponse.of(
                resultPage.getContent(), resultPage.getNumber(), resultPage.getSize(), resultPage.getTotalElements());

        return ResponseEntity.ok(ApiResponse.ok(response, ctx.correlationId().toString()));
    }

    // ---- Mapper: redacted public view ----

    /**
     * Maps a FacilityDetail to a redacted FacilityResponse suitable for public consumers.
     * Only includes: name, code, type, district, province, status, level, lat/lon, and capabilities.
     * Contacts, identifiers, internal operational details, and audit fields are excluded.
     */
    private FacilityResponse toPublicFacilityResponse(FacilityService.FacilityDetail detail) {
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
                null  // updatedBy — redacted
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
