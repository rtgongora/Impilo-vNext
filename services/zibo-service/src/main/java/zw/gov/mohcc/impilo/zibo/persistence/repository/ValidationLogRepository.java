package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.domain.ValidationSeverity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationLogEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link ValidationLogEntity} persistence operations.
 * Provides querying and analytics over terminology validation audit logs.
 */
@Repository
public interface ValidationLogRepository extends JpaRepository<ValidationLogEntity, UUID> {

    /**
     * Returns a paginated list of validation logs within a tenant.
     */
    Page<ValidationLogEntity> findByTenantId(UUID tenantId, Pageable pageable);

    /**
     * Returns a paginated list of validation logs filtered by severity within a tenant.
     */
    Page<ValidationLogEntity> findByTenantIdAndSeverity(UUID tenantId, ValidationSeverity severity, Pageable pageable);

    /**
     * Returns a paginated list of validation logs for a specific facility within a tenant.
     */
    Page<ValidationLogEntity> findByTenantIdAndFacilityId(UUID tenantId, UUID facilityId, Pageable pageable);

    /**
     * Aggregates the most frequent validation failure issue codes within a tenant
     * since a given timestamp, ordered by count descending.
     */
    @Query("SELECT v.issueCode, COUNT(v) FROM ValidationLogEntity v WHERE v.tenantId = :tenantId AND v.createdAt > :since GROUP BY v.issueCode ORDER BY COUNT(v) DESC")
    List<Object[]> findTopFailures(@Param("tenantId") UUID tenantId, @Param("since") OffsetDateTime since);
}
