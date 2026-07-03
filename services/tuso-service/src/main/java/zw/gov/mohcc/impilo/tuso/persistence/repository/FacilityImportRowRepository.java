package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityImportRowEntity;

import java.util.List;

@Repository
public interface FacilityImportRowRepository extends JpaRepository<FacilityImportRowEntity, Long> {

    void deleteByImportRunId(Long importRunId);

    /**
     * Filtered, paginated row search for a run. All filters are null-safe (null = no constraint).
     * {@code outcome} doubles as the row status filter.
     */
    @Query("""
            SELECT r FROM FacilityImportRowEntity r
             WHERE r.importRunId = :runId
               AND (:outcome IS NULL OR r.outcome = :outcome)
               AND (:decisionStatus IS NULL OR r.decisionStatus = :decisionStatus)
               AND (:duplicateType IS NULL OR r.duplicateType = :duplicateType)
               AND (:province IS NULL OR lower(r.province) = lower(cast(:province as string)))
               AND (:district IS NULL OR lower(r.district) = lower(cast(:district as string)))
               AND (:facilityCode IS NULL OR r.facilityCode = :facilityCode)
               AND (:facilityName IS NULL OR lower(r.facilityName) LIKE lower(concat('%', cast(:facilityName as string), '%')))
               AND (:hasAcceptableMissing IS NULL OR r.hasAcceptableMissing = :hasAcceptableMissing)
            """)
    Page<FacilityImportRowEntity> search(
            @Param("runId") Long runId,
            @Param("outcome") String outcome,
            @Param("decisionStatus") String decisionStatus,
            @Param("duplicateType") String duplicateType,
            @Param("province") String province,
            @Param("district") String district,
            @Param("facilityCode") String facilityCode,
            @Param("facilityName") String facilityName,
            @Param("hasAcceptableMissing") Boolean hasAcceptableMissing,
            Pageable pageable);

    /** Count of rows per outcome for a run — powers review-bucket counts without loading rows. */
    @Query("SELECT r.outcome, COUNT(r) FROM FacilityImportRowEntity r WHERE r.importRunId = :runId GROUP BY r.outcome")
    List<Object[]> countByOutcome(@Param("runId") Long runId);

    long countByImportRunIdAndHasAcceptableMissingTrue(Long importRunId);

    long countByImportRunId(Long importRunId);

    /** Rows of a given duplicate type, grouped for review (ordered by group key then code). */
    List<FacilityImportRowEntity> findByImportRunIdAndDuplicateTypeOrderByDuplicateGroupKeyAscFacilityCodeAsc(
            Long importRunId, String duplicateType);

    java.util.Optional<FacilityImportRowEntity> findByIdAndImportRunId(Long id, Long importRunId);

    /** Other rows in the same run that carry a given facility code (for in-run uniqueness checks). */
    List<FacilityImportRowEntity> findByImportRunIdAndFacilityCode(Long importRunId, String facilityCode);

    List<FacilityImportRowEntity> findByImportRunIdAndDecisionStatus(Long importRunId, String decisionStatus);
}
