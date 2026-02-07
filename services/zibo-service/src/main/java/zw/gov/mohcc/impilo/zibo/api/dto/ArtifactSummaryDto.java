package zw.gov.mohcc.impilo.zibo.api.dto;

import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight summary DTO for artifact listings (no content JSON).
 *
 * @param artifactId  the artifact identifier
 * @param fhirType    the FHIR resource type
 * @param canonicalUrl the canonical URL
 * @param version     the version string
 * @param name        the computer-friendly name
 * @param status      the lifecycle status
 * @param createdAt   the creation timestamp
 * @param publishedAt the publication timestamp (null if not published)
 */
public record ArtifactSummaryDto(
        UUID artifactId,
        ArtifactType fhirType,
        String canonicalUrl,
        String version,
        String name,
        ArtifactStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt
) {
    /**
     * Factory method to create an ArtifactSummaryDto from an ArtifactEntity.
     *
     * @param e the artifact entity
     * @return a summary DTO
     */
    public static ArtifactSummaryDto from(ArtifactEntity e) {
        return new ArtifactSummaryDto(
                e.getArtifactId(),
                e.getFhirType(),
                e.getCanonicalUrl(),
                e.getVersion(),
                e.getName(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getPublishedAt()
        );
    }
}
