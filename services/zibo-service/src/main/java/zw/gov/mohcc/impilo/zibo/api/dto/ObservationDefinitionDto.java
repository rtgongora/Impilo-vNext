package zw.gov.mohcc.impilo.zibo.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Full governed {@code ObservationDefinition} artifact (including FHIR content) for the
 * clinical-knowledge-platform reference-range resolver.
 *
 * @param artifactId     the artifact identifier (provenance)
 * @param canonicalUrl   canonical URL (provenance)
 * @param version        version (provenance — recorded in interpretation audit traces)
 * @param status         lifecycle status (DRAFT/ACTIVE/PUBLISHED)
 * @param effectiveStart inclusive applicability start (nullable)
 * @param effectiveEnd   exclusive applicability end (nullable)
 * @param content        the FHIR R4 ObservationDefinition resource
 */
public record ObservationDefinitionDto(
        UUID artifactId,
        String canonicalUrl,
        String version,
        String status,
        OffsetDateTime effectiveStart,
        OffsetDateTime effectiveEnd,
        JsonNode content
) {
    public static ObservationDefinitionDto from(ArtifactEntity e, ObjectMapper mapper) {
        JsonNode content;
        try {
            content = e.getContentJson() == null ? mapper.createObjectNode() : mapper.readTree(e.getContentJson());
        } catch (Exception ex) {
            content = mapper.createObjectNode();
        }
        return new ObservationDefinitionDto(
                e.getArtifactId(),
                e.getCanonicalUrl(),
                e.getVersion(),
                e.getStatus() == null ? null : e.getStatus().name(),
                e.getEffectiveStart(),
                e.getEffectiveEnd(),
                content);
    }
}
