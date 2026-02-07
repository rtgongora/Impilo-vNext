package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.persistence.entity.PackArtifactEntity;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link PackArtifactEntity} persistence operations.
 * Manages the many-to-many relationship between packs and artifacts.
 */
@Repository
public interface PackArtifactRepository extends JpaRepository<PackArtifactEntity, UUID> {

    /**
     * Finds all artifact links for a given pack.
     */
    List<PackArtifactEntity> findByPackId(UUID packId);

    /**
     * Removes a specific artifact from a pack.
     */
    void deleteByPackIdAndArtifactId(UUID packId, UUID artifactId);

    /**
     * Checks whether an artifact is already linked to a pack.
     */
    boolean existsByPackIdAndArtifactId(UUID packId, UUID artifactId);
}
