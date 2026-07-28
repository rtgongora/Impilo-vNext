package zw.gov.mohcc.impilo.orgregistry.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.*;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exports a pack's ACTIVE configuration back to the file format the seeder reads (AD-3).
 *
 * <p>This is what makes environment promotion reviewable. A council approves a release in one
 * environment; the export is a set of files a human can read in a pull request, and which the
 * seeder in the next environment loads byte-for-byte — the same checksum discipline in both
 * directions, so a promotion that was tampered with in transit does not load.</p>
 *
 * <p><b>Checksummed, not cryptographically signed.</b> AD-3 says "signed pack", and this export is
 * deliberately not that: signing needs a key and a verifier, and calling a SHA-256 manifest a
 * signature would overstate what it proves. A checksum shows the files are internally consistent
 * and unchanged since export; it does not prove WHO produced them. What carries authorship today is
 * the approval record in the source environment, which the export names. Real signing —
 * tshepo-keys, verified at load — is a separate, honest piece of work.</p>
 */
@Component
public class ConfigPackExporter {

    private final ConfigPackRepository packRepository;
    private final ConfigReleaseRepository releaseRepository;
    private final ConfigReleaseItemRepository releaseItemRepository;
    private final ConfigDefinitionRepository definitionRepository;
    private final ConfigDefinitionVersionRepository versionRepository;
    private final ConfigDependencyRepository dependencyRepository;
    private final ConfigApprovalRepository approvalRepository;
    private final OrganizationRepository organizationRepository;
    private final ObjectMapper objectMapper;

    public ConfigPackExporter(ConfigPackRepository packRepository,
                              ConfigReleaseRepository releaseRepository,
                              ConfigReleaseItemRepository releaseItemRepository,
                              ConfigDefinitionRepository definitionRepository,
                              ConfigDefinitionVersionRepository versionRepository,
                              ConfigDependencyRepository dependencyRepository,
                              ConfigApprovalRepository approvalRepository,
                              OrganizationRepository organizationRepository,
                              ObjectMapper objectMapper) {
        this.packRepository = packRepository;
        this.releaseRepository = releaseRepository;
        this.releaseItemRepository = releaseItemRepository;
        this.definitionRepository = definitionRepository;
        this.versionRepository = versionRepository;
        this.dependencyRepository = dependencyRepository;
        this.approvalRepository = approvalRepository;
        this.organizationRepository = organizationRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Path within the pack -> the exact file content to write. Callers must write these bytes
     * verbatim: the manifest's checksums are taken over precisely this rendering, and the seeder
     * that re-reads them recomputes the same hash. A re-indent on the way out breaks the round trip.
     */
    public record ExportedPack(Map<String, String> files) {
    }

    public ExportedPack export(java.util.UUID tenantId, java.util.UUID packId) {
        ConfigPackEntity pack = packRepository.findByTenantIdAndId(tenantId, packId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No configuration pack " + packId));
        ConfigReleaseEntity release = releaseRepository
                .findByTenantIdAndPackIdAndLifecycleState(tenantId, packId, "ACTIVE")
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "This pack has no active release. Only approved, activated configuration is "
                                + "exportable — promoting a draft would move rules nobody approved."));

        Map<String, String> files = new LinkedHashMap<>();
        ArrayNode entries = objectMapper.createArrayNode();

        for (ConfigReleaseItemEntity item : releaseItemRepository.findByReleaseId(release.getId())) {
            ConfigDefinitionEntity definition = definitionRepository.findById(item.getDefinitionId())
                    .orElse(null);
            ConfigDefinitionVersionEntity version = versionRepository
                    .findById(item.getDefinitionVersionId()).orElse(null);
            if (definition == null || version == null) {
                continue;
            }

            ObjectNode file = objectMapper.createObjectNode();
            file.put("typeCode", definition.getTypeCode());
            file.put("definitionKey", definition.getDefinitionKey());
            file.put("label", definition.getLabel());
            file.put("semanticVersion", version.getSemanticVersion());
            if (version.getSelfServiceRoute() != null) {
                file.put("selfServiceRoute", version.getSelfServiceRoute());
            }
            if (version.getPolicyStatus() != null) {
                file.put("policyStatus", version.getPolicyStatus());
            }
            if (version.getPolicyDecisionRef() != null) {
                file.put("policyDecisionRef", version.getPolicyDecisionRef());
            }

            ArrayNode dependencies = objectMapper.createArrayNode();
            for (ConfigDependencyEntity dependency
                    : dependencyRepository.findByDefinitionVersionId(version.getId())) {
                ObjectNode row = objectMapper.createObjectNode();
                row.put("typeCode", dependency.getRequiredTypeCode());
                row.put("definitionKey", dependency.getRequiredDefinitionKey());
                row.put("optional", dependency.isOptional());
                dependencies.add(row);
            }
            if (!dependencies.isEmpty()) {
                file.set("dependencies", dependencies);
            }
            file.set("payload", version.getPayload());

            String fileName = definition.getDefinitionKey() + ".json";
            String rendered = render(file);
            files.put("definitions/" + fileName, rendered);

            ObjectNode entry = objectMapper.createObjectNode();
            entry.put("file", fileName);
            entry.put("sha256", RegulatoryConfigPackSeeder.sha256(
                    RegulatoryConfigPackSeeder.normalise(rendered.getBytes(StandardCharsets.UTF_8))));
            entries.add(entry);
        }

        if (files.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The active release contains no resolvable definitions to export.");
        }

        ObjectNode manifest = objectMapper.createObjectNode();
        manifest.put("packKey", pack.getPackKey());
        manifest.put("organizationCode", organizationCode(pack));
        manifest.put("label", pack.getLabel());
        if (pack.getDescription() != null) {
            manifest.put("description", pack.getDescription());
        }

        ObjectNode releaseNode = objectMapper.createObjectNode();
        releaseNode.put("releaseKey", release.getReleaseKey());
        releaseNode.put("label", release.getLabel());
        releaseNode.put("notes", "Exported from an activated release. Loading this pack in another "
                + "environment prepares a DRAFT; it does not carry the approval across.");
        manifest.set("release", releaseNode);

        // Provenance, because a checksum says the files are unchanged, not who stood behind them.
        ObjectNode provenance = objectMapper.createObjectNode();
        provenance.put("exportedFromReleaseId", release.getId().toString());
        provenance.put("activatedAt", String.valueOf(release.getActivatedAt()));
        provenance.put("activatedBy", release.getActivatedBy());
        ArrayNode approvers = objectMapper.createArrayNode();
        approvalRepository.findByReleaseId(release.getId()).stream()
                .filter(a -> "APPROVE".equals(a.getDecision()))
                .forEach(a -> approvers.add(a.getApproverHealthId()));
        provenance.set("approvedBy", approvers);
        provenance.put("integrity", "SHA-256 checksums only — this pack is not cryptographically "
                + "signed, so it evidences that the files are unchanged, not who produced them.");
        manifest.set("provenance", provenance);

        manifest.set("definitions", entries);
        files.put("manifest.json", render(manifest));
        return new ExportedPack(files);
    }

    private String organizationCode(ConfigPackEntity pack) {
        return organizationRepository.findById(pack.getOrganizationId())
                .map(OrganizationEntity::getCode)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "The pack's organisation has no code, so the export could not name the "
                                + "regulator it belongs to and would not load anywhere."));
    }

    /** The exact text to write for one pack file, so export and re-import agree byte for byte. */
    private String render(JsonNode node) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to render exported pack file", ex);
        }
    }
}
