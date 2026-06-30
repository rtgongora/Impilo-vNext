package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessPlanEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WellnessPlanRepository extends JpaRepository<WellnessPlanEntity, Long> {

    List<WellnessPlanEntity> findByTenantIdAndStatusOrderByNameAsc(UUID tenantId, String status);

    Optional<WellnessPlanEntity> findByPlanIdAndTenantId(UUID planId, UUID tenantId);
}
