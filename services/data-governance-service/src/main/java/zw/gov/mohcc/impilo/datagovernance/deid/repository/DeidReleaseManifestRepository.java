package zw.gov.mohcc.impilo.datagovernance.deid.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.deid.domain.DeidReleaseManifestEntity;

import java.util.Optional;
import java.util.UUID;

public interface DeidReleaseManifestRepository extends JpaRepository<DeidReleaseManifestEntity, UUID> {

    Optional<DeidReleaseManifestEntity> findByRunId(UUID runId);
}
