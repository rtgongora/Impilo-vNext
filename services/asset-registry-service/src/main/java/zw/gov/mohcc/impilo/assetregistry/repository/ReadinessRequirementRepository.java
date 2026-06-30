package zw.gov.mohcc.impilo.assetregistry.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.assetregistry.domain.ReadinessRequirementEntity;

import java.util.List;
import java.util.UUID;

public interface ReadinessRequirementRepository extends JpaRepository<ReadinessRequirementEntity, UUID> {
    List<ReadinessRequirementEntity> findByProfileId(UUID profileId);
}
