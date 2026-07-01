package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityImportProvenanceDto;
import zw.gov.mohcc.impilo.tuso.core.FacilityProvenanceService;

/**
 * Exposes a facility's import provenance + configuration-completeness checklist. TUSO is the facility
 * SoR and the producer of this read-model; the internal facility identity, the public administrative
 * code, and downstream materialisation state are all surfaced distinctly and honestly.
 */
@RestController
@RequestMapping("/v1/internal/facilities")
public class FacilityProvenanceController {

    private final FacilityProvenanceService provenanceService;

    public FacilityProvenanceController(FacilityProvenanceService provenanceService) {
        this.provenanceService = provenanceService;
    }

    @GetMapping("/{facilityId}/import-provenance")
    public ResponseEntity<ApiResponse<FacilityImportProvenanceDto>> importProvenance(
            @PathVariable Long facilityId) {
        TrustContext ctx = TrustContextHolder.require();
        FacilityImportProvenanceDto dto = provenanceService.buildProvenance(facilityId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Facility not found: " + facilityId));
        return ResponseEntity.ok(ApiResponse.ok(dto, ctx.correlationId().toString()));
    }
}
