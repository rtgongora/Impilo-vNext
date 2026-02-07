package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.GofrSyncLogEntity;

import java.util.UUID;

@Repository
public interface GofrSyncLogRepository extends JpaRepository<GofrSyncLogEntity, Long> {

    Page<GofrSyncLogEntity> findByTenantIdOrderByStartedAtDesc(UUID tenantId, Pageable pageable);

    Page<GofrSyncLogEntity> findByTenantIdAndStatusOrderByStartedAtDesc(
            UUID tenantId, String status, Pageable pageable);
}
