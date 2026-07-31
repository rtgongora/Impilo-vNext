package zw.gov.mohcc.impilo.orgregistry.api;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.orgregistry.core.ConfigPackExporter;
import zw.gov.mohcc.impilo.orgregistry.core.ConfigReleaseValidator;
import zw.gov.mohcc.impilo.orgregistry.core.RegulatoryConfigService;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Regulatory Configuration Registry API (NCZ-W1A).
 *
 * <p>Authoring is deliberately narrow in this wave: packs, versions, releases, approval, activation
 * and case binding. There is no visual configuration studio — the ruling is explicit that Wave 1
 * builds the registry and the discipline, not a no-code editor. Reading the active pack and a
 * version history is open to any regulatory session; changing anything is a governed act.</p>
 */
@RestController
@RequestMapping("/v1/regulatory/config")
public class RegulatoryConfigController {

    private final RegulatoryConfigService configService;
    private final ConfigPackExporter exporter;

    public RegulatoryConfigController(RegulatoryConfigService configService,
                                      ConfigPackExporter exporter) {
        this.configService = configService;
        this.exporter = exporter;
    }

    // ── Catalogue and packs ──────────────────────────────────────────────────────────────────

    @GetMapping("/definition-types")
    public List<ConfigDefinitionTypeEntity> definitionTypes() {
        return configService.listTypes();
    }

    @PostMapping("/organizations/{organizationId}/packs")
    public ResponseEntity<ConfigPackEntity> createPack(
            @PathVariable UUID organizationId,
            @RequestBody OrgRegistryDtos.CreateConfigPackRequest request) {
        ConfigPackEntity pack = configService.createPack(tenantId(), organizationId,
                request.packKey(), request.label(), request.description(), actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pack);
    }

    @GetMapping("/organizations/{organizationId}/packs")
    public List<ConfigPackEntity> listPacks(@PathVariable UUID organizationId) {
        return configService.listPacks(tenantId(), organizationId);
    }

    /**
     * Configuration audit for one regulatory organisation. Read-only; covers pack/release/activation
     * events owned by org-registry — not every register access in varapi.
     */
    @GetMapping("/organizations/{organizationId}/audit-events")
    public List<ConfigAuditEventEntity> listAuditEvents(@PathVariable UUID organizationId) {
        return configService.listAuditEventsForOrganization(tenantId(), organizationId);
    }

    // ── Definitions and versions ─────────────────────────────────────────────────────────────

    @GetMapping("/packs/{packId}/definitions")
    public List<ConfigDefinitionEntity> listDefinitions(@PathVariable UUID packId) {
        return configService.listDefinitions(tenantId(), packId);
    }

    @GetMapping("/definitions/{definitionId}/versions")
    public List<ConfigDefinitionVersionEntity> versionHistory(@PathVariable UUID definitionId) {
        return configService.listVersions(tenantId(), definitionId);
    }

    @PostMapping("/packs/{packId}/versions")
    public ResponseEntity<ConfigDefinitionVersionEntity> authorVersion(
            @PathVariable UUID packId,
            @RequestBody OrgRegistryDtos.AuthorConfigVersionRequest request) {
        ConfigDefinitionVersionEntity version = configService.authorVersion(
                tenantId(), packId, request.typeCode(), request.definitionKey(), request.label(),
                request.semanticVersion(), request.payload(), request.selfServiceRoute(),
                request.policyStatus(), request.policyDecisionRef(), request.sourcePackRef(),
                request.dependencies() == null ? List.of() : request.dependencies().stream()
                        .map(d -> new RegulatoryConfigService.DependencySpec(
                                d.typeCode(), d.definitionKey(), d.optional()))
                        .toList(),
                actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(version);
    }

    // ── Releases ─────────────────────────────────────────────────────────────────────────────

    @PostMapping("/packs/{packId}/releases")
    public ResponseEntity<ConfigReleaseEntity> createRelease(
            @PathVariable UUID packId,
            @RequestBody OrgRegistryDtos.CreateConfigReleaseRequest request) {
        ConfigReleaseEntity release = Boolean.TRUE.equals(request.cloneActive())
                ? configService.cloneActiveRelease(tenantId(), packId, request.releaseKey(),
                        request.label(), actorId())
                : configService.createRelease(tenantId(), packId, request.releaseKey(),
                        request.label(), request.notes(), actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(release);
    }

    @GetMapping("/packs/{packId}/releases")
    public List<ConfigReleaseEntity> listReleases(@PathVariable UUID packId) {
        return configService.listReleases(tenantId(), packId);
    }

    @PostMapping("/releases/{releaseId}/items")
    public ConfigReleaseItemEntity addItem(@PathVariable UUID releaseId,
                                           @RequestBody OrgRegistryDtos.AddReleaseItemRequest request) {
        return configService.addItem(tenantId(), releaseId, request.definitionVersionId(), actorId());
    }

    @PostMapping("/releases/{releaseId}/submit")
    public ConfigReleaseEntity submit(@PathVariable UUID releaseId) {
        return configService.submitForReview(tenantId(), releaseId, actorId());
    }

    @PostMapping("/releases/{releaseId}/approvals")
    public ResponseEntity<ConfigApprovalEntity> approve(
            @PathVariable UUID releaseId,
            @RequestBody OrgRegistryDtos.RecordConfigApprovalRequest request) {
        ConfigApprovalEntity approval = configService.recordApproval(tenantId(), releaseId,
                actorId(), request.approverRole(), request.decision(), request.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(approval);
    }

    /** Dry run: what would activation say? Changes nothing. */
    @GetMapping("/releases/{releaseId}/validation")
    public ConfigReleaseValidator.ValidationReport validation(@PathVariable UUID releaseId) {
        return configService.preview(tenantId(), releaseId);
    }

    @PostMapping("/releases/{releaseId}/approve")
    public ConfigReleaseValidator.ValidationReport approveRelease(@PathVariable UUID releaseId) {
        return configService.approve(tenantId(), releaseId, actorId());
    }

    @PostMapping("/releases/{releaseId}/activate")
    public ConfigActivationEntity activate(@PathVariable UUID releaseId) {
        return configService.activate(tenantId(), releaseId, actorId(), correlationId());
    }

    /** The pack's active configuration — what a NEW case would be governed by. */
    @GetMapping("/packs/{packId}/active")
    public List<RegulatoryConfigService.ResolvedDefinition> activeConfiguration(@PathVariable UUID packId) {
        return configService.resolveActive(tenantId(), packId);
    }

    /**
     * Export the pack's ACTIVE configuration back to the file format the seeder reads (AD-3), so a
     * council's approved rules can be reviewed in a pull request and promoted to another
     * environment. Only activated configuration exports: promoting a draft would move rules nobody
     * approved. Loading it elsewhere prepares a DRAFT — approval does not travel with the files.
     */
    @GetMapping("/packs/{packId}/export")
    public Map<String, String> exportPack(@PathVariable UUID packId) {
        return exporter.export(tenantId(), packId).files();
    }

    // ── Case bindings ────────────────────────────────────────────────────────────────────────

    @PostMapping("/case-bindings")
    public ResponseEntity<CaseConfigBindingEntity> bindCase(
            @RequestBody OrgRegistryDtos.BindCaseConfigRequest request) {
        CaseConfigBindingEntity binding = configService.bindCase(tenantId(), request.caseSystem(),
                request.caseType(), request.caseRef(), request.organizationId(), actorId(),
                correlationId());
        return ResponseEntity.status(HttpStatus.CREATED).body(binding);
    }

    @PostMapping("/case-bindings/{bindingId}/pins")
    public ResponseEntity<CaseConfigPinEntity> pin(
            @PathVariable UUID bindingId,
            @RequestBody OrgRegistryDtos.PinCaseConfigRequest request) {
        CaseConfigPinEntity pin = configService.pinDefinition(tenantId(), bindingId,
                request.typeCode(), request.definitionKey(), request.moment(), actorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(pin);
    }

    /**
     * The rules that governed a case. This is the endpoint that makes the Registry worth building:
     * it answers from the case's own binding, never from whatever is active today.
     */
    @GetMapping("/case-bindings/{caseSystem}/{caseType}/{caseRef}")
    public List<RegulatoryConfigService.ResolvedDefinition> resolveForCase(
            @PathVariable String caseSystem, @PathVariable String caseType,
            @PathVariable String caseRef) {
        return configService.resolveForCase(tenantId(), caseSystem, caseType, caseRef);
    }

    private UUID tenantId() {
        return TrustContextHolder.require().tenantId();
    }

    private String actorId() {
        try {
            return TrustContextHolder.require().actorId();
        } catch (IllegalStateException ex) {
            return "system";
        }
    }

    private String correlationId() {
        try {
            UUID correlationId = TrustContextHolder.require().correlationId();
            return correlationId == null ? null : correlationId.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
