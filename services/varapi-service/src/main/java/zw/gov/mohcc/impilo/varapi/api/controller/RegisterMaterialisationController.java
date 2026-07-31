package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.varapi.core.RegisterMaterialisationService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.CouncilRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProfessionalRegisterRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Operator surface for professional-register materialisation (task #97).
 *
 * <p>The event consumer keeps registers current as releases activate; these endpoints exist for
 * the cases an event cannot cover — a council linked after activation, a service that was down when
 * the event was published, or an operator who needs to prove the current state matches the current
 * configuration. Reconcile is deliberately idempotent, so running it because you are unsure is
 * always safe.</p>
 *
 * <p>Organisation-scoped routes exist because the regulatory workspace is addressed by org-registry
 * identity, not by varapi's council primary key. The council-id route remains for tooling that
 * already has it.</p>
 */
@RestController
@RequestMapping("/v1/internal")
public class RegisterMaterialisationController {

    private final RegisterMaterialisationService materialisationService;
    private final CouncilRepository councilRepository;
    private final ProfessionalRegisterRepository registerRepository;

    public RegisterMaterialisationController(RegisterMaterialisationService materialisationService,
                                             CouncilRepository councilRepository,
                                             ProfessionalRegisterRepository registerRepository) {
        this.materialisationService = materialisationService;
        this.councilRepository = councilRepository;
        this.registerRepository = registerRepository;
    }

    /**
     * The live professional registers of the council regulated by this organisation, with the
     * provenance that distinguishes a configuration-materialised row from a migration seed.
     */
    @GetMapping("/organizations/{organizationId}/registers")
    public ResponseEntity<Map<String, Object>> registersForOrganisation(
            @PathVariable UUID organizationId) {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", organizationId.toString());

        CouncilEntity council = councilRepository
                .findByTenantIdAndOrgRegistryOrgId(ctx.tenantId(), organizationId)
                .orElse(null);
        if (council == null) {
            body.put("linked", false);
            body.put("councilId", null);
            body.put("councilCode", null);
            body.put("councilName", null);
            body.put("registers", List.of());
            body.put("note", "No professional council in varapi is linked to this organisation, "
                    + "so there are no registers to materialise. Link councils.org_registry_org_id "
                    + "before reconciling.");
            return ResponseEntity.ok(body);
        }

        List<Map<String, Object>> registers = registerRepository
                .findByTenantIdAndCouncilIdOrderByRegisterCodeAsc(ctx.tenantId(), council.getId())
                .stream()
                .map(ProfessionalRegisterViews::toOperatorView)
                .toList();

        body.put("linked", true);
        body.put("councilId", council.getId());
        body.put("councilCode", council.getCouncilCode());
        body.put("councilName", council.getName());
        body.put("registers", registers);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/organizations/{organizationId}/registers/reconcile")
    public ResponseEntity<Map<String, Object>> reconcileForOrganisation(
            @PathVariable UUID organizationId) {
        TrustContext ctx = TrustContextHolder.require();
        String actor = ctx.actorId() == null ? "system" : ctx.actorId();
        return materialisationService.reconcileForOrganisation(ctx.tenantId(), organizationId, actor)
                .map(report -> ResponseEntity.ok(reportBody(organizationId, true, report)))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "organizationId", organizationId.toString(),
                        "linked", false,
                        "outcome", "NO_COUNCIL_LINKED",
                        "note", "No professional council in varapi is linked to this organisation. "
                                + "Nothing was reconciled.")));
    }

    @PostMapping("/councils/{councilId}/registers/reconcile")
    public ResponseEntity<RegisterMaterialisationService.ReconcileReport> reconcile(
            @PathVariable Long councilId) {
        TrustContext ctx = TrustContextHolder.require();
        String actor = ctx.actorId() == null ? "system" : ctx.actorId();
        return ResponseEntity.ok(
                materialisationService.reconcile(ctx.tenantId(), councilId, actor));
    }

    private static Map<String, Object> reportBody(
            UUID organizationId, boolean linked,
            RegisterMaterialisationService.ReconcileReport report) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("organizationId", organizationId.toString());
        body.put("linked", linked);
        body.put("outcome", report.outcome());
        body.put("councilCode", report.councilCode());
        body.put("declared", report.declared());
        body.put("created", report.created());
        body.put("updated", report.updated());
        body.put("unchanged", report.unchanged());
        body.put("retired", report.retired());
        body.put("reinstated", report.reinstated());
        body.put("notes", report.notes());
        return body;
    }
}
