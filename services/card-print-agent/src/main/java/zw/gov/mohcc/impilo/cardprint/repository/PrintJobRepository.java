package zw.gov.mohcc.impilo.cardprint.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity;
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity.JobStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PrintJobRepository extends JpaRepository<PrintJobEntity, Long> {

    Optional<PrintJobEntity> findByJobId(UUID jobId);

    Page<PrintJobEntity> findByTenantIdAndStatus(UUID tenantId, JobStatus status, Pageable pageable);

    List<PrintJobEntity> findBySubjectTypeAndSubjectId(String subjectType, String subjectId);

    /**
     * Finds jobs ready for processing, ordered by priority (highest first) then creation date (oldest first).
     */
    @Query("SELECT j FROM PrintJobEntity j WHERE j.status = :status ORDER BY j.priority DESC, j.createdAt ASC")
    List<PrintJobEntity> findByStatusOrderByPriorityDescCreatedAtAsc(@Param("status") JobStatus status, Pageable pageable);

    /**
     * Search with optional filters. Null parameters are ignored.
     */
    @Query("""
        SELECT j FROM PrintJobEntity j
        WHERE (:tenantId IS NULL OR j.tenantId = :tenantId)
          AND (:status IS NULL OR j.status = :status)
          AND (:subjectType IS NULL OR j.subjectType = :subjectType)
          AND (:subjectId IS NULL OR j.subjectId = :subjectId)
        ORDER BY j.createdAt DESC
        """)
    Page<PrintJobEntity> searchJobs(
            @Param("tenantId") UUID tenantId,
            @Param("status") JobStatus status,
            @Param("subjectType") String subjectType,
            @Param("subjectId") String subjectId,
            Pageable pageable);
}
