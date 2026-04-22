package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.ComplianceService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderComplianceActionEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for provider compliance tracking.
 */
@RestController
@RequestMapping("/v1/internal/providers/compliance")
public class ComplianceController {

    private final ComplianceService complianceService;

    public ComplianceController(ComplianceService complianceService) {
        this.complianceService = complianceService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createComplianceAction(@RequestBody Map<String, Object> request) {
        Long providerId = Long.valueOf(request.get("providerId").toString());
        String complianceActionType = (String) request.get("complianceActionType");
        String description = (String) request.get("description");
        String requirement = (String) request.get("requirement");
        LocalDate dueDate = request.get("dueDate") != null ?
                LocalDate.parse((String) request.get("dueDate")) : null;
        String notes = (String) request.get("notes");

        ProviderComplianceActionEntity action = complianceService.createComplianceAction(
                providerId, complianceActionType, description, requirement, dueDate, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Compliance action created",
                Map.of("actionId", action.getId(), "status", action.getStatus())
        ));
    }

    @PostMapping("/{actionId}/fulfil")
    public ResponseEntity<GenericResponse> fulfillAction(
            @PathVariable Long actionId,
            @RequestBody Map<String, Object> request) {
        LocalDate fulfilledDate = request.get("fulfilledDate") != null ?
                LocalDate.parse((String) request.get("fulfilledDate")) : LocalDate.now();
        String evidence = (String) request.get("evidence");
        String notes = (String) request.get("notes");

        ProviderComplianceActionEntity action = complianceService.fulfillAction(
                actionId, fulfilledDate, evidence, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Compliance action fulfilled",
                Map.of("actionId", action.getId(), "status", action.getStatus())
        ));
    }

    @PostMapping("/{actionId}/exempt")
    public ResponseEntity<GenericResponse> exemptAction(
            @PathVariable Long actionId,
            @RequestBody Map<String, Object> request) {
        String exemptionReason = (String) request.get("exemptionReason");
        String notes = (String) request.get("notes");

        ProviderComplianceActionEntity action = complianceService.exemptAction(
                actionId, exemptionReason, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Compliance action exempted",
                Map.of("actionId", action.getId(), "status", action.getStatus())
        ));
    }

    @PostMapping("/{actionId}/escalate")
    public ResponseEntity<GenericResponse> escalateAction(
            @PathVariable Long actionId,
            @RequestBody Map<String, Object> request) {
        LocalDate newDueDate = request.get("newDueDate") != null ?
                LocalDate.parse((String) request.get("newDueDate")) : null;
        String reason = (String) request.get("reason");
        String notes = (String) request.get("notes");

        ProviderComplianceActionEntity action = complianceService.escalateAction(
                actionId, newDueDate, reason, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Compliance action escalated",
                Map.of("actionId", action.getId(), "dueDate", action.getDueDate())
        ));
    }

    @GetMapping("/{actionId}")
    public ResponseEntity<ProviderComplianceActionEntity> getComplianceAction(@PathVariable Long actionId) {
        return ResponseEntity.ok(complianceService.getComplianceAction(actionId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getComplianceActionsByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(complianceService.getComplianceActionsByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/outstanding")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getOutstandingActions(@PathVariable Long providerId) {
        return ResponseEntity.ok(complianceService.getOutstandingActions(providerId));
    }

    @GetMapping("/provider/{providerId}/overdue")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getOverdueActions(@PathVariable Long providerId) {
        return ResponseEntity.ok(complianceService.getOverdueActions(providerId));
    }

    @GetMapping("/due-within/{days}")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getActionsDueWithin(@PathVariable int days) {
        return ResponseEntity.ok(complianceService.getActionsDueWithin(days));
    }

    @GetMapping("/overdue")
    public ResponseEntity<List<ProviderComplianceActionEntity>> getAllOverdueActions() {
        return ResponseEntity.ok(complianceService.getAllOverdueActions());
    }
}