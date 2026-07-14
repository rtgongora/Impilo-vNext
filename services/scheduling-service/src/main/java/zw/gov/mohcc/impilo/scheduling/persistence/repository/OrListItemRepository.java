package zw.gov.mohcc.impilo.scheduling.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.scheduling.persistence.entity.OrListItemEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrListItemRepository extends JpaRepository<OrListItemEntity, UUID> {

    Optional<OrListItemEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    List<OrListItemEntity> findBySessionIdOrderBySequencePositionAsc(UUID sessionId);
}
