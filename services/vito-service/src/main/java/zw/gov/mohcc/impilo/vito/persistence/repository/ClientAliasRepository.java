package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAliasEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientAliasRepository extends JpaRepository<ClientAliasEntity, UUID> {
    List<ClientAliasEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientAliasEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
}
