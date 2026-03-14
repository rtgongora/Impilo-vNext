package zw.gov.mohcc.impilo.offlinesync.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.ConflictQueueEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ConflictQueueRepository extends JpaRepository<ConflictQueueEntity, Long> {

    List<ConflictQueueEntity> findByTenantId(UUID tenantId);

    List<ConflictQueueEntity> findByResolutionIsNull();

    List<ConflictQueueEntity> findBySyncPackId(Long syncPackId);
}
