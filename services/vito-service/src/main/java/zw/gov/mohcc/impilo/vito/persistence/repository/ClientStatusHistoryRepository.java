package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientStatusHistoryRepository extends JpaRepository<ClientStatusHistoryEntity, UUID> {
    List<ClientStatusHistoryEntity> findByClientHealthIdOrderByChangedAtDesc(UUID clientHealthId);
    List<ClientStatusHistoryEntity> findByTenantIdAndClientHealthIdOrderByChangedAtDesc(UUID tenantId, UUID clientHealthId);
}
