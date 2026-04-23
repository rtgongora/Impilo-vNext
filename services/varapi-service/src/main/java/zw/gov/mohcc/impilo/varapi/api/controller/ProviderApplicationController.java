package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.ProviderApplicationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderApplicationEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for provider lifecycle applications.
 */
@RestController
@RequestMapping("/v1/internal/providers/applications")
public class ProviderApplicationController {

    private final ProviderApplicationService applicationService;

    public ProviderApplicationController(ProviderApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createApplication(@RequestBody Map<String, Object> request) {
        String applicationType = (String) request.get("applicationType");
        Long providerId = request.get("providerId") != null ? Long.valueOf(request.get("providerId").toString()) : null;
        Long councilId = request.get("councilId") != null ? Long.valueOf(request.get("councilId").toString()) : null;
        String authorityRoute = (String) request.get("authorityRoute");
        String notes = (String) request.get("notes");

        ProviderApplicationEntity application = applicationService.createApplication(
                applicationType, providerId, councilId, authorityRoute, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Application created",
                Map.of("applicationId", application.getId(), "workflowState", application.getWorkflowState())
        ));
    }

    @PostMapping("/{applicationId}/submit")
    public ResponseEntity<GenericResponse> submitApplication(@PathVariable Long applicationId) {
        ProviderApplicationEntity application = applicationService.submitApplication(applicationId);
        return ResponseEntity.ok(GenericResponse.success(
                "Application submitted",
                Map.of("applicationId", application.getId(), "workflowState", application.getWorkflowState())
        ));
    }

    @PostMapping("/{applicationId}/verify")
    public ResponseEntity<GenericResponse> completeVerification(
            @PathVariable Long applicationId,
            @RequestBody Map<String, String> request) {
        String verificationState = request.get("verificationState");
        ProviderApplicationEntity application = applicationService.completeVerification(applicationId, verificationState);
        return ResponseEntity.ok(GenericResponse.success(
                "Verification completed",
                Map.of("applicationId", application.getId(), "verificationState", application.getVerificationState())
        ));
    }

    @PostMapping("/{applicationId}/committee")
    public ResponseEntity<GenericResponse> advanceToCommittee(
            @PathVariable Long applicationId,
            @RequestBody Map<String, String> request) {
        String committeeState = request.get("committeeState");
        ProviderApplicationEntity application = applicationService.advanceToCommitteeReview(applicationId, committeeState);
        return ResponseEntity.ok(GenericResponse.success(
                "Advanced to committee review",
                Map.of("applicationId", application.getId(), "workflowState", application.getWorkflowState())
        ));
    }

    @PostMapping("/{applicationId}/decision")
    public ResponseEntity<GenericResponse> recordDecision(
            @PathVariable Long applicationId,
            @RequestBody Map<String, Object> request) {
        String decision = (String) request.get("decision");
        LocalDate decisionDate = request.get("decisionDate") != null ?
                LocalDate.parse((String) request.get("decisionDate")) : null;
        String authorityRoute = (String) request.get("authorityRoute");

        ProviderApplicationEntity application = applicationService.recordDecision(
                applicationId, decision, decisionDate, authorityRoute);
        return ResponseEntity.ok(GenericResponse.success(
                "Decision recorded",
                Map.of("applicationId", application.getId(), "workflowState", application.getWorkflowState())
        ));
    }

    @PostMapping("/{applicationId}/approve")
    public ResponseEntity<GenericResponse> approveAndActivate(@PathVariable Long applicationId) {
        ProviderApplicationEntity application = applicationService.approveAndActivate(applicationId);
        return ResponseEntity.ok(GenericResponse.success(
                "Application approved and provider activated",
                Map.of("applicationId", application.getId(), "workflowState", application.getWorkflowState())
        ));
    }

    @PostMapping("/{applicationId}/close")
    public ResponseEntity<GenericResponse> closeApplication(
            @PathVariable Long applicationId,
            @RequestBody Map<String, String> request) {
        String reason = request.get("reason");
        ProviderApplicationEntity application = applicationService.closeApplication(applicationId, reason);
        return ResponseEntity.ok(GenericResponse.success(
                "Application closed",
                Map.of("applicationId", application.getId(), "workflowState", application.getWorkflowState())
        ));
    }

    @GetMapping("/{applicationId}")
    public ResponseEntity<ProviderApplicationEntity> getApplication(@PathVariable Long applicationId) {
        return ResponseEntity.ok(applicationService.getApplication(applicationId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderApplicationEntity>> getApplicationsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(applicationService.getApplicationsByProvider(providerId));
    }

    @GetMapping("/open")
    public ResponseEntity<List<ProviderApplicationEntity>> getOpenApplications(
            @RequestParam(required = false) String workflowState) {
        return ResponseEntity.ok(applicationService.getOpenApplications(workflowState));
    }
}
