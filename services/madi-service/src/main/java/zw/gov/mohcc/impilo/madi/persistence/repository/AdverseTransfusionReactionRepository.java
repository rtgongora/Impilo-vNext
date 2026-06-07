package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.AdverseTransfusionReactionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AdverseTransfusionReactionRepository extends JpaRepository<AdverseTransfusionReactionEntity, Long> {
    Optional<AdverseTransfusionReactionEntity> findByReactionIdAndTenantId(UUID reactionId, UUID tenantId);
    List<AdverseTransfusionReactionEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
