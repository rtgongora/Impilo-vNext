package zw.gov.mohcc.impilo.msika.api.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.core.MappingService;
import zw.gov.mohcc.impilo.msika.persistence.entity.ExternalMappingEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

@RestController
@RequestMapping("/v1/mappings")
public class MappingController {

    private final MappingService mappingService;

    public MappingController(MappingService mappingService) {
        this.mappingService = mappingService;
    }

    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<PagedResponse<ExternalMappingEntity>>> getPendingMappings(Pageable pageable) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        Page<ExternalMappingEntity> page = mappingService.getPendingMappings(pageable);
        PagedResponse<ExternalMappingEntity> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/{mappingId}/approve")
    public ResponseEntity<ApiResponse<ExternalMappingEntity>> approveMapping(@PathVariable String mappingId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ExternalMappingEntity mapping = mappingService.approveMapping(mappingId);
        return ResponseEntity.ok(ApiResponse.ok(mapping, correlationId));
    }

    @PreAuthorize("hasAnyRole('CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    @PostMapping("/{mappingId}/reject")
    public ResponseEntity<ApiResponse<ExternalMappingEntity>> rejectMapping(@PathVariable String mappingId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ExternalMappingEntity mapping = mappingService.rejectMapping(mappingId);
        return ResponseEntity.ok(ApiResponse.ok(mapping, correlationId));
    }
}
