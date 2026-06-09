package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.simba.persistence.entity.WellnessRouteEntity;

import java.util.List;
import java.util.UUID;

public interface WellnessRouteRepository extends JpaRepository<WellnessRouteEntity, Long> {

    List<WellnessRouteEntity> findByTenantIdAndStatusOrderByTitleAsc(UUID tenantId, String status);
}
