package zw.gov.mohcc.impilo.simba.social.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.social.entity.ContentReportEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ContentReportRepository extends JpaRepository<ContentReportEntity, Long> {

    Optional<ContentReportEntity> findByReportIdAndTenantId(UUID reportId, UUID tenantId);

    List<ContentReportEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    long countByTenantIdAndStatus(UUID tenantId, String status);
}
