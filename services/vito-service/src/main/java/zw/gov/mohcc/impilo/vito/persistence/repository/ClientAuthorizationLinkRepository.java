package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuthorizationLinkEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ClientAuthorizationLinkRepository extends JpaRepository<ClientAuthorizationLinkEntity, UUID> {
    List<ClientAuthorizationLinkEntity> findByClientHealthIdOrderByCreatedAtDesc(UUID clientHealthId);
    List<ClientAuthorizationLinkEntity> findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(UUID tenantId, UUID clientHealthId);
}
