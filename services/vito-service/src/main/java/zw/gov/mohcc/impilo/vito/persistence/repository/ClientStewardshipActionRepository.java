package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.ClientStewardshipStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientStewardshipActionEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientStewardshipActionRepository extends JpaRepository<ClientStewardshipActionEntity, UUID> {
    List<ClientStewardshipActionEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientStewardshipActionEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
    Page<ClientStewardshipActionEntity> findByTenantIdAndStatus(UUID tenantId,
                                                                ClientStewardshipStatus status,
                                                                Pageable pageable);
    long countByTenantIdAndStatus(UUID tenantId, ClientStewardshipStatus status);
}
