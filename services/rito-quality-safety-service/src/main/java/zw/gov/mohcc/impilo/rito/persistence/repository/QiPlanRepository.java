package zw.gov.mohcc.impilo.rito.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.rito.persistence.entity.QiPlanEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QiPlanRepository extends JpaRepository<QiPlanEntity, UUID> {

    Optional<QiPlanEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    List<QiPlanEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    List<QiPlanEntity> findByTenantIdAndStatus(UUID tenantId, String status);

    boolean existsByTenantIdAndQiReference(UUID tenantId, String qiReference);
}
