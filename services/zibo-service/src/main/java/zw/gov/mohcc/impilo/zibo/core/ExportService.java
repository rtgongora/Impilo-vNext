package zw.gov.mohcc.impilo.zibo.core;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.PackArtifactRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exports terminology packs and individual artifacts as FHIR resources.
 *
 * <p>Pack exports collect all artifacts in a pack and wrap them in a FHIR
 * Bundle (type: collection) suitable for import into other terminology
 * servers or FHIR endpoints.</p>
 *
 * <p>Individual artifact exports return the raw FHIR resource JSON as
 * stored in the artifact's content.</p>
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final PackService packService;
    private final ArtifactRepository artifactRepository;
    private final PackArtifactRepository packArtifactRepository;
    private final ObjectMapper objectMapper;
    private final FhirContext fhirContext;

    public ExportService(PackService packService,
                         ArtifactRepository artifactRepository,
                         PackArtifactRepository packArtifactRepository,
                         ObjectMapper objectMapper) {
        this.packService = packService;
        this.artifactRepository = artifactRepository;
        this.packArtifactRepository = packArtifactRepository;
        this.objectMapper = objectMapper;
        this.fhirContext = FhirContext.forR4();
    }

    /**
     * Export a terminology pack as a FHIR Bundle (type: collection).
     *
     * <p>Collects all artifacts in the pack, parses their content JSON
     * into FHIR resources, and wraps them in a Bundle. The bundle includes
     * metadata about the pack (identifier, timestamp).</p>
     *
     * @param packId the pack to export
     * @return a JSON string representing the FHIR Bundle
     * @throws IllegalArgumentException if the pack is not found for this tenant
     * @throws IllegalStateException    if any artifact content cannot be parsed as a FHIR resource
     */
    @Transactional(readOnly = true)
    public String exportPackAsBundle(UUID packId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Validate the pack exists
        PackEntity pack = packService.getPack(packId);

        // Collect all artifacts in the pack
        List<PackArtifactEntity> associations = packArtifactRepository.findByPackId(packId);
        List<ArtifactEntity> artifacts = new ArrayList<>();

        for (PackArtifactEntity pa : associations) {
            artifactRepository.findByTenantIdAndArtifactId(tenantId, pa.getArtifactId())
                    .ifPresent(artifacts::add);
        }

        // Build FHIR Bundle
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.COLLECTION);
        bundle.setTimestamp(new Date());

        // Add pack metadata as identifier
        bundle.setId(packId.toString());
        bundle.getIdentifier()
                .setSystem("urn:impilo:zibo:pack")
                .setValue(pack.getName() + "|" + pack.getVersion());

        IParser parser = fhirContext.newJsonParser();

        int entryCount = 0;
        for (ArtifactEntity artifact : artifacts) {
            String contentJson = artifact.getContentJson();
            if (contentJson == null || contentJson.isBlank()) {
                log.warn("Artifact {} has no content, skipping in export", artifact.getId());
                continue;
            }

            try {
                Resource resource = (Resource) parser.parseResource(contentJson);

                Bundle.BundleEntryComponent entry = bundle.addEntry();
                entry.setResource(resource);
                entry.setFullUrl(artifact.getCanonicalUrl()
                        + (artifact.getVersion() != null ? "|" + artifact.getVersion() : ""));

                entryCount++;
            } catch (Exception e) {
                log.error("Failed to parse artifact {} content as FHIR resource: {}",
                        artifact.getId(), e.getMessage());
                throw new IllegalStateException(
                        "Failed to parse artifact " + artifact.getId() + " as FHIR resource: " + e.getMessage(), e);
            }
        }

        bundle.setTotal(entryCount);

        String bundleJson = fhirContext.newJsonParser()
                .setPrettyPrint(true)
                .encodeResourceToString(bundle);

        log.info("Exported pack {} as FHIR Bundle: {} entries", packId, entryCount);

        return bundleJson;
    }

    /**
     * Export a single artifact's raw FHIR resource JSON content.
     *
     * @param artifactId the artifact to export
     * @return the raw content JSON of the artifact
     * @throws IllegalArgumentException if the artifact is not found for this tenant
     */
    @Transactional(readOnly = true)
    public String exportArtifact(UUID artifactId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        ArtifactEntity artifact = artifactRepository.findByTenantIdAndArtifactId(tenantId, artifactId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Artifact not found: " + artifactId + " for tenant " + tenantId));

        String contentJson = artifact.getContentJson();
        if (contentJson == null || contentJson.isBlank()) {
            log.warn("Artifact {} has no content JSON", artifactId);
            return "{}";
        }

        return contentJson;
    }

    /**
     * Export a summary list of all artifacts in a pack.
     *
     * <p>Returns lightweight summaries (no content JSON) suitable for
     * displaying pack contents in a UI or API response.</p>
     *
     * @param packId the pack identifier
     * @return a list of artifact summary maps, each containing id, canonicalUrl,
     *         version, name, title, fhirType, and status
     * @throws IllegalArgumentException if the pack is not found for this tenant
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> exportPackArtifactList(UUID packId) {
        TrustContext ctx = TrustContextHolder.require();
        UUID tenantId = ctx.tenantId();

        // Validate the pack exists
        packService.getPack(packId);

        List<PackArtifactEntity> associations = packArtifactRepository.findByPackId(packId);

        return associations.stream()
                .map(pa -> artifactRepository.findByTenantIdAndArtifactId(tenantId, pa.getArtifactId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(this::buildArtifactSummary)
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // Internal helpers
    // ------------------------------------------------------------------

    private Map<String, Object> buildArtifactSummary(ArtifactEntity artifact) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("artifactId", artifact.getId().toString());
        summary.put("canonicalUrl", artifact.getCanonicalUrl());
        summary.put("version", artifact.getVersion());
        summary.put("name", artifact.getName());
        summary.put("title", artifact.getTitle());
        summary.put("fhirType", artifact.getFhirType().name());
        summary.put("status", artifact.getStatus().name());
        summary.put("contentHash", artifact.getContentHash());
        summary.put("publisher", artifact.getPublisher());
        if (artifact.getPublishedAt() != null) {
            summary.put("publishedAt", artifact.getPublishedAt().toString());
        }
        return summary;
    }
}
