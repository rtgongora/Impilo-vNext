package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.core.PickupStatus;
import zw.gov.mohcc.impilo.vito.persistence.entity.DelegatedPickupEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DelegatedPickupRepository extends JpaRepository<DelegatedPickupEntity, Long> {

    Optional<DelegatedPickupEntity> findByPickupTokenHash(String tokenHash);

    Optional<DelegatedPickupEntity> findByTenantIdAndIssuanceRequestId(UUID tenantId, Long issuanceRequestId);

    Page<DelegatedPickupEntity> findByTenantIdAndStatus(UUID tenantId, PickupStatus status, Pageable pageable);
}
