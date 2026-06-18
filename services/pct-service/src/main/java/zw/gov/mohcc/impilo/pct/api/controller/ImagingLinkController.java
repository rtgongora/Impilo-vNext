package zw.gov.mohcc.impilo.pct.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.pct.api.dto.CreateImagingLinkRequest;
import zw.gov.mohcc.impilo.pct.core.ImagingLinkService;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImagingLinkEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

@RestController
@RequestMapping("/v1/encounters/{encounterId}/imaging-links")
public class ImagingLinkController {

    private final ImagingLinkService imagingLinkService;

    public ImagingLinkController(ImagingLinkService imagingLinkService) {
        this.imagingLinkService = imagingLinkService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ImagingLinkEntity>> createLink(
            @PathVariable Long encounterId,
            @Valid @RequestBody CreateImagingLinkRequest request) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        ImagingLinkEntity link = imagingLinkService.createLink(encounterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(link, correlationId));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ImagingLinkEntity>>> listLinks(@PathVariable Long encounterId) {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(imagingLinkService.listByEncounter(encounterId), correlationId));
    }
}
