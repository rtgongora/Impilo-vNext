package zw.gov.mohcc.impilo.zibo.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.zibo.domain.JobStatus;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ValidationJobEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ValidationJobEntity} persistence operations.
 * Manages asynchronous terminology validation jobs.
 */
@Repository
public interface ValidationJobRepository extends JpaRepository<ValidationJobEntity, UUID> {

    /**
     * Finds a validation job by tenant and job ID.
     */
    Optional<ValidationJobEntity> findByTenantIdAndJobId(UUID tenantId, UUID jobId);

    /**
     * Finds all jobs with a given status (non-tenant-scoped).
     * Used by the job processor to pick up pending work.
     */
    List<ValidationJobEntity> findByStatus(JobStatus status);

    /**
     * Returns a paginated list of validation jobs within a tenant.
     */
    Page<ValidationJobEntity> findByTenantId(UUID tenantId, Pageable pageable);
}
