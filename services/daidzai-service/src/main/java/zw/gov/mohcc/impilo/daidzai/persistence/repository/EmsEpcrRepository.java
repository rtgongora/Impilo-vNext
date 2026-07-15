package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsEpcrEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmsEpcrRepository extends JpaRepository<EmsEpcrEntity, UUID> {
    Optional<EmsEpcrEntity> findByTenantIdAndMissionId(UUID tenantId, UUID missionId);
}
