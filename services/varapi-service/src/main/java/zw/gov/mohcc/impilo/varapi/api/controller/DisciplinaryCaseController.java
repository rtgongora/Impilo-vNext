package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.DisciplinaryCaseService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.DisciplinaryActionEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderDisciplinaryCaseEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Provider disciplinary casework (R2/G9). Open cases, record actions (a suspension/revocation
 * propagates to the PIC-eligibility read-side), and close with a decision. Provider-scoped and
 * tenant-wide open queues are lists; single-case detail carries the full narrative (the experience
 * layer masks the list to status/trigger; full narrative is gateway-gated to approver roles).
 */
@RestController
@RequestMapping("/v1/internal/providers/disciplinary-cases")
public class DisciplinaryCaseController {

    private final DisciplinaryCaseService service;

    public DisciplinaryCaseController(DisciplinaryCaseService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> open(@RequestBody Map<String, Object> body) {
        Long providerId = Long.valueOf(String.valueOf(body.get("providerId")));
        ProviderDisciplinaryCaseEntity c = service.openCase(
                providerId,
                (String) body.get("triggerType"),
                (String) body.get("summary"),
                (String) body.get("routeToAuthority"));
        return ResponseEntity.ok(GenericResponse.success("Disciplinary case opened",
                Map.of("caseId", c.getId(), "status", c.getStatus())));
    }

    @PostMapping("/{caseId}/record-action")
    public ResponseEntity<GenericResponse> recordAction(@PathVariable Long caseId,
                                                        @RequestBody Map<String, Object> body) {
        LocalDate expiry = body.get("expiryDate") != null ? LocalDate.parse(String.valueOf(body.get("expiryDate"))) : null;
        DisciplinaryActionEntity a = service.recordAction(
                caseId,
                (String) body.get("actionType"),
                (String) body.get("severity"),
                (String) body.get("description"),
                expiry);
        return ResponseEntity.ok(GenericResponse.success("Disciplinary action recorded",
                Map.of("actionId", a.getId())));
    }

    @PostMapping("/{caseId}/close")
    public ResponseEntity<GenericResponse> close(@PathVariable Long caseId,
                                                 @RequestBody Map<String, Object> body) {
        ProviderDisciplinaryCaseEntity c = service.closeCase(
                caseId,
                (String) body.get("finalDecision"),
                (String) body.get("finalRecommendation"));
        return ResponseEntity.ok(GenericResponse.success("Disciplinary case closed",
                Map.of("caseId", c.getId(), "status", c.getStatus())));
    }

    @GetMapping("/{caseId}")
    public ResponseEntity<ProviderDisciplinaryCaseEntity> getCase(@PathVariable Long caseId) {
        return ResponseEntity.ok(service.getCase(caseId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderDisciplinaryCaseEntity>> byProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(service.listByProvider(providerId));
    }

    @GetMapping("/open")
    public ResponseEntity<List<ProviderDisciplinaryCaseEntity>> open() {
        return ResponseEntity.ok(service.listOpen());
    }
}
