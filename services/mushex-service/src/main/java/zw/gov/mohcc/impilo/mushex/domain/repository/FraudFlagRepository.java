package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.FraudFlagEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FraudFlagRepository extends JpaRepository<FraudFlagEntity, String> {

    Page<FraudFlagEntity> findByTenantId(UUID tenantId, Pageable pageable);

    Page<FraudFlagEntity> findByTenantIdAndStatus(UUID tenantId, String status, Pageable pageable);

    List<FraudFlagEntity> findByEntityTypeAndEntityId(String entityType, String entityId);
}
