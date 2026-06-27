package zw.gov.mohcc.impilo.ia.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ia.persistence.entity.AssuranceRecordEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssuranceRecordRepository extends JpaRepository<AssuranceRecordEntity, Long> {

    Optional<AssuranceRecordEntity> findByTenantIdAndActorId(UUID tenantId, String actorId);
}
