package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;
import zw.gov.mohcc.impilo.experience.imaging.ImagingAccessPolicyService;
import zw.gov.mohcc.impilo.experience.imaging.ImagingGovernanceService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Governed facility imaging capability + modality registry API for the Experience layer.
 *
 * <p>Clinical users see operational readiness only; raw networking (host, port, AE titles,
 * serial numbers) is redacted unless the actor holds a technical/admin role. All configuration
 * mutations require a technical/admin role and are audited.</p>
 */
@RestController
@RequestMapping("/internal/v1/imaging/facilities")
public class ImagingFacilityExperienceController {

    /** Fields never shown to non-technical clinical actors. */
    private static final Set<String> TECHNICAL_FIELDS = Set.of(
            "host", "port", "aeTitle", "calledAeTitle", "callingAeTitle",
            "serialNumber", "defaultStorageDestination", "defaultWorklistSource",
            "defaultArchiveTarget", "defaultWorklistTarget", "notes");

    private final PacsServiceClient pacsServiceClient;
    private final ImagingAccessPolicyService imagingAccessPolicyService;
    private final ImagingGovernanceService imagingGovernanceService;
    private final ObjectMapper objectMapper;

    public ImagingFacilityExperienceController(
            PacsServiceClient pacsServiceClient,
            ImagingAccessPolicyService imagingAccessPolicyService,
            ImagingGovernanceService imagingGovernanceService,
            ObjectMapper objectMapper) {
        this.pacsServiceClient = pacsServiceClient;
        this.imagingAccessPolicyService = imagingAccessPolicyService;
        this.imagingGovernanceService = imagingGovernanceService;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listCapabilities(
            HttpServletRequest request,
            @RequestParam(name = "deployment_mode", required = false) String deploymentMode) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.listFacilityCapabilities(deploymentMode);
        audit(request, "IMAGING_FACILITY_CAPABILITY_LIST", "GET:imaging/facilities", "SUCCESS",
                "imaging_facility_capability", null,
                Map.of("deployment_mode", deploymentMode != null ? deploymentMode : ""));
        return ResponseEntity.ok(envelope(redactForActor(raw)));
    }

    @GetMapping("/{facilityId}/capability")
    public ResponseEntity<Map<String, Object>> getCapability(
            HttpServletRequest request,
            @PathVariable String facilityId) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.getFacilityCapability(facilityId);
        audit(request, "IMAGING_FACILITY_CAPABILITY_GET", "GET:imaging/facilities/capability", "SUCCESS",
                "imaging_facility_capability", facilityId, Map.of());
        return ResponseEntity.ok(envelope(redactForActor(raw)));
    }

    @PutMapping("/{facilityId}/capability")
    public ResponseEntity<Map<String, Object>> upsertCapability(
            HttpServletRequest request,
            @PathVariable String facilityId,
            @RequestBody Map<String, Object> body) {
        imagingGovernanceService.assertGovernedMutate();
        imagingAccessPolicyService.requireTechnicalImagingAdmin();
        JsonNode raw = pacsServiceClient.upsertFacilityCapability(facilityId, body);
        audit(request, "IMAGING_FACILITY_CAPABILITY_UPSERT", "PUT:imaging/facilities/capability", "SUCCESS",
                "imaging_facility_capability", facilityId,
                Map.of("deployment_mode", String.valueOf(body.getOrDefault("deploymentMode", ""))));
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/{facilityId}/readiness")
    public ResponseEntity<Map<String, Object>> readiness(
            HttpServletRequest request,
            @PathVariable String facilityId) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.facilityImagingReadiness(facilityId);
        audit(request, "IMAGING_FACILITY_READINESS", "GET:imaging/facilities/readiness", "SUCCESS",
                "imaging_facility_capability", facilityId, Map.of());
        return ResponseEntity.ok(envelope(raw));
    }

    @GetMapping("/{facilityId}/modalities")
    public ResponseEntity<Map<String, Object>> listModalities(
            HttpServletRequest request,
            @PathVariable String facilityId,
            @RequestParam(name = "include_inactive", defaultValue = "false") boolean includeInactive) {
        imagingGovernanceService.assertGovernedRead();
        JsonNode raw = pacsServiceClient.listFacilityModalities(facilityId, includeInactive);
        audit(request, "IMAGING_MODALITY_LIST", "GET:imaging/facilities/modalities", "SUCCESS",
                "imaging_modality", facilityId, Map.of("include_inactive", String.valueOf(includeInactive)));
        return ResponseEntity.ok(envelope(redactForActor(raw)));
    }

    @PostMapping("/{facilityId}/modalities")
    public ResponseEntity<Map<String, Object>> registerModality(
            HttpServletRequest request,
            @PathVariable String facilityId,
            @RequestBody Map<String, Object> body) {
        imagingGovernanceService.assertGovernedMutate();
        imagingAccessPolicyService.requireTechnicalImagingAdmin();
        JsonNode raw = pacsServiceClient.registerFacilityModality(facilityId, body);
        audit(request, "IMAGING_MODALITY_REGISTERED", "POST:imaging/facilities/modalities", "SUCCESS",
                "imaging_modality", facilityId,
                Map.of("modality_type", String.valueOf(body.getOrDefault("modalityType", ""))));
        return ResponseEntity.ok(envelope(raw));
    }

    @PutMapping("/{facilityId}/modalities/{modalityId}")
    public ResponseEntity<Map<String, Object>> updateModality(
            HttpServletRequest request,
            @PathVariable String facilityId,
            @PathVariable Long modalityId,
            @RequestBody Map<String, Object> body) {
        imagingGovernanceService.assertGovernedMutate();
        imagingAccessPolicyService.requireTechnicalImagingAdmin();
        JsonNode raw = pacsServiceClient.updateFacilityModality(facilityId, modalityId, body);
        audit(request, "IMAGING_MODALITY_UPDATED", "PUT:imaging/facilities/modalities", "SUCCESS",
                "imaging_modality", String.valueOf(modalityId), Map.of("facility_id", facilityId));
        return ResponseEntity.ok(envelope(raw));
    }

    @PostMapping("/{facilityId}/modalities/{modalityId}/deactivate")
    public ResponseEntity<Map<String, Object>> deactivateModality(
            HttpServletRequest request,
            @PathVariable String facilityId,
            @PathVariable Long modalityId) {
        imagingGovernanceService.assertGovernedMutate();
        imagingAccessPolicyService.requireTechnicalImagingAdmin();
        JsonNode raw = pacsServiceClient.deactivateFacilityModality(facilityId, modalityId);
        audit(request, "IMAGING_MODALITY_DEACTIVATED", "POST:imaging/facilities/modalities/deactivate", "SUCCESS",
                "imaging_modality", String.valueOf(modalityId), Map.of("facility_id", facilityId));
        return ResponseEntity.ok(envelope(raw));
    }

    /**
     * Removes technical/network fields recursively unless the actor holds a technical/admin role.
     * Clinical users see readiness and support flags, never raw networking.
     */
    private JsonNode redactForActor(JsonNode raw) {
        if (raw == null || imagingAccessPolicyService.isTechnicalImagingAdmin()) {
            return raw;
        }
        JsonNode copy = raw.deepCopy();
        redactNode(copy);
        return copy;
    }

    private static void redactNode(JsonNode node) {
        if (node instanceof ObjectNode obj) {
            obj.remove(TECHNICAL_FIELDS);
            obj.forEach(ImagingFacilityExperienceController::redactNode);
        } else if (node instanceof ArrayNode arr) {
            arr.forEach(ImagingFacilityExperienceController::redactNode);
        }
    }

    private void audit(
            HttpServletRequest request,
            String eventType,
            String action,
            String outcome,
            String resourceType,
            String resourceId,
            Map<String, Object> detail) {
        String subject = request.getHeader(CompanionHeaders.SUBJECT_ID);
        imagingGovernanceService.recordImagingAudit(
                request.getHeader(CompanionHeaders.TENANT_ID),
                request.getHeader(CompanionHeaders.CORRELATION_ID),
                request.getHeader(CompanionHeaders.PURPOSE_OF_USE),
                request.getHeader(CompanionHeaders.FACILITY_ID),
                eventType,
                action,
                outcome,
                actorId(),
                actorType(),
                subject,
                resourceType,
                resourceId,
                detail);
    }

    private static String actorId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "anonymous";
        }
        Object p = auth.getPrincipal();
        if (p instanceof Jwt jwt) {
            return jwt.getSubject() != null ? jwt.getSubject() : auth.getName();
        }
        return auth.getName();
    }

    private static String actorType() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().isEmpty()) {
            return "UNKNOWN";
        }
        return auth.getAuthorities().iterator().next().getAuthority();
    }

    private Map<String, Object> envelope(JsonNode data) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("success", true);
        map.put("data", objectMapper.convertValue(data, Object.class));
        return map;
    }
}
