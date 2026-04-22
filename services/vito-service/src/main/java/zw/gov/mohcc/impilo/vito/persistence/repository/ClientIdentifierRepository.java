package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientIdentifierRepository extends JpaRepository<ClientIdentifierEntity, UUID> {
    List<ClientIdentifierEntity> findByClientHealthIdOrderByIssueDateDesc(UUID clientHealthId);
    List<ClientIdentifierEntity> findByTenantIdAndClientHealthIdOrderByIssueDateDesc(UUID tenantId, UUID clientHealthId);
}
