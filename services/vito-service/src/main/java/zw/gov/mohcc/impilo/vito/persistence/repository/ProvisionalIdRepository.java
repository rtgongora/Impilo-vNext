package zw.gov.mohcc.impilo.vito.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.vito.persistence.entity.ProvisionalIdEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProvisionalIdRepository extends JpaRepository<ProvisionalIdEntity, Long> {

    Optional<ProvisionalIdEntity> findByTenantIdAndProvisionalRef(UUID tenantId, String provisionalRef);

    Page<ProvisionalIdEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    Page<ProvisionalIdEntity> findByTenantIdAndFacilityIdAndStatus(
            UUID tenantId, UUID facilityId, String status, Pageable pageable);
}
