package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmsUnitEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmsUnitRepository extends JpaRepository<EmsUnitEntity, UUID> {
    Optional<EmsUnitEntity> findByIdAndTenantId(UUID id, UUID tenantId);
}
