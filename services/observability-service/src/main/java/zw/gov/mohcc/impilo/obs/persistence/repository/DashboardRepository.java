package zw.gov.mohcc.impilo.obs.persistence.repository;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.obs.persistence.entity.DashboardEntity;

@Repository
public interface DashboardRepository extends JpaRepository<DashboardEntity, Long> {

    List<DashboardEntity> findByTenantId(UUID tenantId);
}
