package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.TransfusionOutcomeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransfusionOutcomeRepository extends JpaRepository<TransfusionOutcomeEntity, Long> {
    Optional<TransfusionOutcomeEntity> findByOutcomeIdAndTenantId(UUID outcomeId, UUID tenantId);
    List<TransfusionOutcomeEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
