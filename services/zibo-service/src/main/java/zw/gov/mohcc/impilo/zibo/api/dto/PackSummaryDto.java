package zw.gov.mohcc.impilo.zibo.api.dto;

import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight summary DTO for pack listings.
 *
 * @param packId        the pack identifier
 * @param name          the pack name
 * @param version       the version string
 * @param status        the lifecycle status
 * @param artifactCount the number of artifacts in the pack
 * @param createdAt     the creation timestamp
 * @param publishedAt   the publication timestamp (null if not published)
 */
public record PackSummaryDto(
        UUID packId,
        String name,
        String version,
        ArtifactStatus status,
        int artifactCount,
        OffsetDateTime createdAt,
        OffsetDateTime publishedAt
) {
    /**
     * Factory method to create a PackSummaryDto from a PackEntity and artifact count.
     *
     * @param e             the pack entity
     * @param artifactCount the number of artifacts in the pack
     * @return a summary DTO
     */
    public static PackSummaryDto from(PackEntity e, int artifactCount) {
        return new PackSummaryDto(
                e.getPackId(),
                e.getName(),
                e.getVersion(),
                e.getStatus(),
                artifactCount,
                e.getCreatedAt(),
                e.getPublishedAt()
        );
    }
}
