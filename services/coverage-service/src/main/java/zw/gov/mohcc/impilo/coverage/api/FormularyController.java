package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.coverage.core.FormularyService;
import zw.gov.mohcc.impilo.coverage.domain.FormularyEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * OF-B8 — payer-formulary API (V020 {@code cv_formulary}): lookup by plan
 * version + medication code, and governed admin CRUD (delete = window
 * retirement, never a hard erase). Keyed by plan version exactly like the
 * benefit endpoints ({@link BenefitController}).
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class FormularyController {

    public record FormularyView(UUID id, UUID planVersionId, String medicationCode, String codingSystem,
                                String benefitCode, boolean covered, String tier, String copayClass,
                                boolean requiresAuthorisation, OffsetDateTime effectiveFrom,
                                OffsetDateTime effectiveTo, String notes) {
        static FormularyView of(FormularyEntity f) {
            return new FormularyView(f.getId(), f.getPlanVersionId(), f.getMedicationCode(),
                    f.getCodingSystem(), f.getBenefitCode(), f.isCovered(), f.getTier(), f.getCopayClass(),
                    f.isRequiresAuthorisation(), f.getEffectiveFrom(), f.getEffectiveTo(), f.getNotes());
        }
    }

    /** Lookup answer: the posture is explicit — never inferred from an empty body. */
    public record FormularyLookupResponse(String status, FormularyView entry) {}

    public record CreateFormularyRequest(
            @NotBlank String medicationCode,
            String codingSystem,
            @NotBlank String benefitCode,
            Boolean covered,
            String tier,
            String copayClass,
            Boolean requiresAuthorisation,
            OffsetDateTime effectiveFrom,
            OffsetDateTime effectiveTo,
            String notes) {}

    public record UpdateFormularyRequest(
            @NotNull Boolean covered,
            String tier,
            String copayClass,
            String benefitCode,
            @NotNull Boolean requiresAuthorisation,
            OffsetDateTime effectiveTo,
            String notes) {}

    private final FormularyService formularyService;

    public FormularyController(FormularyService formularyService) {
        this.formularyService = formularyService;
    }

    /** Point lookup: is this medication on this plan version's formulary right now? */
    @GetMapping("/formulary/lookup")
    public ResponseEntity<FormularyLookupResponse> lookup(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "plan_version_id") UUID planVersionId,
            @RequestParam(name = "medication_code") String medicationCode,
            @RequestParam(name = "coding_system", required = false) String codingSystem,
            @RequestParam(name = "at", required = false) OffsetDateTime at) {
        UUID tid = UUID.fromString(tenantId);
        FormularyService.FormularyResolution resolution =
                formularyService.resolve(tid, planVersionId, medicationCode, codingSystem, at);
        return ResponseEntity.ok(new FormularyLookupResponse(
                resolution.status().name(),
                resolution.entry() != null ? FormularyView.of(resolution.entry()) : null));
    }

    @GetMapping("/plan-versions/{planVersionId}/formulary")
    public ResponseEntity<List<FormularyView>> list(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID planVersionId) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(formularyService.listForPlanVersion(tid, planVersionId)
                .stream().map(FormularyView::of).toList());
    }

    @PostMapping("/plan-versions/{planVersionId}/formulary")
    public ResponseEntity<FormularyView> create(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID planVersionId,
            @Valid @RequestBody CreateFormularyRequest req) {
        UUID tid = UUID.fromString(tenantId);
        FormularyEntity entry = FormularyEntity.create(tid, podId, planVersionId,
                req.medicationCode(), req.codingSystem(), req.benefitCode());
        if (req.covered() != null) {
            entry.setCovered(req.covered());
        }
        if (req.tier() != null && !req.tier().isBlank()) {
            entry.setTier(req.tier());
        }
        entry.setCopayClass(req.copayClass());
        if (req.requiresAuthorisation() != null) {
            entry.setRequiresAuthorisation(req.requiresAuthorisation());
        }
        if (req.effectiveFrom() != null) {
            entry.setEffectiveFrom(req.effectiveFrom());
        }
        entry.setEffectiveTo(req.effectiveTo());
        entry.setNotes(req.notes());
        FormularyEntity saved = formularyService.create(entry, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(FormularyView.of(saved));
    }

    @PutMapping("/formulary/{id}")
    public ResponseEntity<FormularyView> update(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFormularyRequest req) {
        UUID tid = UUID.fromString(tenantId);
        FormularyEntity saved = formularyService.update(tid, id, req.covered(), req.tier(),
                req.copayClass(), req.benefitCode(), req.requiresAuthorisation(),
                req.effectiveTo(), req.notes(), CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(FormularyView.of(saved));
    }

    /** Governance retirement — closes the effective window; the row remains auditable. */
    @DeleteMapping("/formulary/{id}")
    public ResponseEntity<FormularyView> retire(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        UUID tid = UUID.fromString(tenantId);
        FormularyEntity saved = formularyService.retire(tid, id, CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(FormularyView.of(saved));
    }
}
