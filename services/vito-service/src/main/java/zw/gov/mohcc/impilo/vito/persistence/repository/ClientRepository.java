package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.IdentityStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<ClientEntity, Long> {

    Optional<ClientEntity> findByTenantIdAndHealthId(UUID tenantId, UUID healthId);

    Page<ClientEntity> findByTenantIdAndStatus(UUID tenantId, IdentityStatus status, Pageable pageable);

    Page<ClientEntity> findByTenantId(UUID tenantId, Pageable pageable);

    boolean existsByTenantIdAndHealthId(UUID tenantId, UUID healthId);
}
