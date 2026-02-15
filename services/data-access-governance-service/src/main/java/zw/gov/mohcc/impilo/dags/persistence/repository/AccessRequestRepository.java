package zw.gov.mohcc.impilo.dags.persistence.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.dags.core.AccessRequestStatus;
import zw.gov.mohcc.impilo.dags.persistence.entity.AccessRequestEntity;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequestEntity, Long> {

    Page<AccessRequestEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<AccessRequestEntity> findByTenantIdAndStatus(UUID tenantId, AccessRequestStatus status, Pageable pageable);

    Optional<AccessRequestEntity> findByIdAndTenantId(Long id, UUID tenantId);
}
