package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.PracticeContextService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderPracticeContextEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for provider practice contexts.
 */
@RestController
@RequestMapping("/v1/internal/providers/practice-contexts")
public class PracticeContextController {

    private final PracticeContextService practiceContextService;

    public PracticeContextController(PracticeContextService practiceContextService) {
        this.practiceContextService = practiceContextService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createPracticeContext(@RequestBody Map<String, Object> request) {
        Long providerId = Long.valueOf(request.get("providerId").toString());
        String contextType = (String) request.get("contextType");
        String description = (String) request.get("description");
        String locationCode = (String) request.get("locationCode");
        String authorizationReference = (String) request.get("authorizationReference");
        LocalDate authorizationStartDate = request.get("authorizationStartDate") != null ?
                LocalDate.parse((String) request.get("authorizationStartDate")) : null;
        LocalDate authorizationEndDate = request.get("authorizationEndDate") != null ?
                LocalDate.parse((String) request.get("authorizationEndDate")) : null;
        String notes = (String) request.get("notes");

        ProviderPracticeContextEntity context = practiceContextService.createPracticeContext(
                providerId, contextType, description, locationCode, authorizationReference,
                authorizationStartDate, authorizationEndDate, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Practice context created",
                Map.of("contextId", context.getId(), "status", context.getStatus())
        ));
    }

    @PostMapping("/{contextId}/authorize")
    public ResponseEntity<GenericResponse> authorizeContext(
            @PathVariable Long contextId,
            @RequestBody Map<String, Object> request) {
        String authorizationReference = (String) request.get("authorizationReference");
        LocalDate authorizationStartDate = request.get("authorizationStartDate") != null ?
                LocalDate.parse((String) request.get("authorizationStartDate")) : LocalDate.now();
        LocalDate authorizationEndDate = request.get("authorizationEndDate") != null ?
                LocalDate.parse((String) request.get("authorizationEndDate")) : null;
        String authorizedBy = (String) request.get("authorizedBy");
        String notes = (String) request.get("notes");

        ProviderPracticeContextEntity context = practiceContextService.authorizeContext(
                contextId, authorizationReference, authorizationStartDate, authorizationEndDate, authorizedBy, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Practice context authorized",
                Map.of("contextId", context.getId(), "status", context.getStatus())
        ));
    }

    @PostMapping("/{contextId}/revoke")
    public ResponseEntity<GenericResponse> revokeContext(
            @PathVariable Long contextId,
            @RequestBody Map<String, Object> request) {
        LocalDate revocationDate = request.get("revocationDate") != null ?
                LocalDate.parse((String) request.get("revocationDate")) : LocalDate.now();
        String reason = (String) request.get("reason");

        ProviderPracticeContextEntity context = practiceContextService.revokeContext(contextId, revocationDate, reason);

        return ResponseEntity.ok(GenericResponse.success(
                "Practice context revoked",
                Map.of("contextId", context.getId(), "status", context.getStatus())
        ));
    }

    @PostMapping("/{contextId}/renew")
    public ResponseEntity<GenericResponse> renewContext(
            @PathVariable Long contextId,
            @RequestBody Map<String, Object> request) {
        LocalDate newEndDate = LocalDate.parse((String) request.get("newEndDate"));
        String renewalReference = (String) request.get("renewalReference");
        String notes = (String) request.get("notes");

        ProviderPracticeContextEntity context = practiceContextService.renewContext(
                contextId, newEndDate, renewalReference, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Practice context renewed",
                Map.of("contextId", context.getId(), "authorizationEndDate", context.getEndDate())
        ));
    }

    @GetMapping("/{contextId}")
    public ResponseEntity<ProviderPracticeContextEntity> getPracticeContext(@PathVariable Long contextId) {
        return ResponseEntity.ok(practiceContextService.getPracticeContext(contextId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderPracticeContextEntity>> getContextsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(practiceContextService.getContextsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/active")
    public ResponseEntity<List<ProviderPracticeContextEntity>> getActiveContextsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(practiceContextService.getActiveContextsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/context-type/{contextType}")
    public ResponseEntity<List<ProviderPracticeContextEntity>> getContextsByType(
            @PathVariable Long providerId,
            @PathVariable String contextType) {
        return ResponseEntity.ok(practiceContextService.getActiveContextsByType(providerId, contextType));
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<ProviderPracticeContextEntity>> getExpiringContexts(
            @RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(practiceContextService.getExpiringContexts(daysAhead));
    }
}
