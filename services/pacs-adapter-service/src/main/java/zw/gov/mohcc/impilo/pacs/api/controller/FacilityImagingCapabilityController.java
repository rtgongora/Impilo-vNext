package zw.gov.mohcc.impilo.pacs.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.pacs.api.dto.FacilityImagingCapabilityRequest;
import zw.gov.mohcc.impilo.pacs.api.dto.ImagingModalityRequest;
import zw.gov.mohcc.impilo.pacs.core.FacilityImagingCapabilityService;
import zw.gov.mohcc.impilo.pacs.persistence.entity.FacilityImagingCapabilityEntity;
import zw.gov.mohcc.impilo.pacs.persistence.entity.ImagingModalityEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-facility imaging capability + modality registry API.
 * Base path {@code /internal/v1/imaging-facilities}.
 *
 * <p>An unconfigured facility is an honest state: {@code GET .../capability} returns
 * {@code configured=false} rather than a fabricated default.</p>
 */
@RestController
@RequestMapping("/internal/v1/imaging-facilities")
public class FacilityImagingCapabilityController {

    private final FacilityImagingCapabilityService capabilityService;

    public FacilityImagingCapabilityController(FacilityImagingCapabilityService capabilityService) {
        this.capabilityService = capabilityService;
    }

    @GetMapping
    public ResponseEntity<List<FacilityImagingCapabilityEntity>> listCapabilities(
            @RequestParam(name = "deploymentMode", required = false) String deploymentMode) {
        return ResponseEntity.ok(capabilityService.listCapabilities(tenantId(), deploymentMode));
    }

    @GetMapping("/{facilityId}/capability")
    public ResponseEntity<Map<String, Object>> getCapability(@PathVariable String facilityId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("facilityId", facilityId);
        var maybe = capabilityService.getCapability(tenantId(), facilityId);
        out.put("configured", maybe.isPresent());
        out.put("capability", maybe.orElse(null));
        return ResponseEntity.ok(out);
    }

    @PutMapping("/{facilityId}/capability")
    public ResponseEntity<FacilityImagingCapabilityEntity> upsertCapability(
            @PathVariable String facilityId,
            @Valid @RequestBody FacilityImagingCapabilityRequest request) {
        try {
            return ResponseEntity.ok(capabilityService.upsertCapability(
                    tenantId(), facilityId, request, actorId(), actorType()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @GetMapping("/{facilityId}/readiness")
    public ResponseEntity<Map<String, Object>> readiness(@PathVariable String facilityId) {
        return ResponseEntity.ok(capabilityService.readiness(tenantId(), facilityId));
    }

    @GetMapping("/{facilityId}/modalities")
    public ResponseEntity<List<ImagingModalityEntity>> listModalities(
            @PathVariable String facilityId,
            @RequestParam(name = "includeInactive", defaultValue = "false") boolean includeInactive) {
        return ResponseEntity.ok(capabilityService.listModalities(tenantId(), facilityId, includeInactive));
    }

    @PostMapping("/{facilityId}/modalities")
    public ResponseEntity<ImagingModalityEntity> registerModality(
            @PathVariable String facilityId,
            @Valid @RequestBody ImagingModalityRequest request) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(capabilityService.registerModality(
                    tenantId(), facilityId, request, actorId(), actorType()));
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
    }

    @PutMapping("/{facilityId}/modalities/{modalityId}")
    public ResponseEntity<ImagingModalityEntity> updateModality(
            @PathVariable String facilityId,
            @PathVariable Long modalityId,
            @Valid @RequestBody ImagingModalityRequest request) {
        try {
            return ResponseEntity.ok(capabilityService.updateModality(
                    tenantId(), facilityId, modalityId, request, actorId(), actorType()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/{facilityId}/modalities/{modalityId}/deactivate")
    public ResponseEntity<ImagingModalityEntity> deactivateModality(
            @PathVariable String facilityId,
            @PathVariable Long modalityId) {
        try {
            return ResponseEntity.ok(capabilityService.deactivateModality(
                    tenantId(), facilityId, modalityId, actorId(), actorType()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static UUID tenantId() {
        TrustContext trust = TrustContextHolder.get();
        if (trust != null && trust.tenantId() != null) {
            return trust.tenantId();
        }
        RequestContext ctx = RequestContextHolder.get();
        if (ctx != null && ctx.tenantId() != null && !ctx.tenantId().isBlank()) {
            try {
                return UUID.fromString(ctx.tenantId().trim());
            } catch (IllegalArgumentException e) {
                return UUID.nameUUIDFromBytes(ctx.tenantId().trim().getBytes(StandardCharsets.UTF_8));
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenant context required");
    }

    private static String actorId() {
        TrustContext trust = TrustContextHolder.get();
        if (trust != null && trust.actorId() != null && !trust.actorId().isBlank()) {
            return trust.actorId();
        }
        return "anonymous";
    }

    private static String actorType() {
        TrustContext trust = TrustContextHolder.get();
        if (trust != null && trust.actorType() != null && !trust.actorType().isBlank()) {
            return trust.actorType();
        }
        return "UNKNOWN";
    }
}
