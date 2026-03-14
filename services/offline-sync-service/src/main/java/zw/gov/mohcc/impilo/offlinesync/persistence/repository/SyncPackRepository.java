package zw.gov.mohcc.impilo.offlinesync.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.offlinesync.persistence.entity.SyncPackEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SyncPackRepository extends JpaRepository<SyncPackEntity, Long> {

    List<SyncPackEntity> findByTenantId(UUID tenantId);

    List<SyncPackEntity> findByStatus(String status);

    List<SyncPackEntity> findByTenantIdAndStatus(UUID tenantId, String status);
}
