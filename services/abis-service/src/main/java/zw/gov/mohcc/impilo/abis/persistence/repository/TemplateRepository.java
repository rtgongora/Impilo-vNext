package zw.gov.mohcc.impilo.abis.persistence.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.abis.core.BiometricModality;
import zw.gov.mohcc.impilo.abis.persistence.entity.TemplateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TemplateRepository extends JpaRepository<TemplateEntity, Long> {

    Optional<TemplateEntity> findByTenantIdAndSubjectRefAndModalityAndPosition(
            UUID tenantId, String subjectRef, BiometricModality modality, String position);

    Optional<TemplateEntity> findByTenantIdAndSubjectRefAndModalityAndPositionAndStatus(
            UUID tenantId, String subjectRef, BiometricModality modality, String position, String status);

    Optional<TemplateEntity> findFirstByTenantIdAndSubjectRefAndModalityAndStatusOrderByUpdatedAtDesc(
            UUID tenantId, String subjectRef, BiometricModality modality, String status);

    List<TemplateEntity> findByTenantIdAndModalityAndStatus(
            UUID tenantId, BiometricModality modality, String status, Pageable pageable);

    /** All templates for a subject in a given status (used to mark a merged subject's templates). */
    List<TemplateEntity> findByTenantIdAndSubjectRefAndStatus(
            UUID tenantId, String subjectRef, String status);

    long countByTenantId(UUID tenantId);

    long countByTenantIdAndStatus(UUID tenantId, String status);

    /** Template counts grouped by (modality, status) for the stats dashboard. */
    @Query("""
            SELECT t.modality, t.status, COUNT(t)
            FROM TemplateEntity t
            WHERE t.tenantId = :tenantId
            GROUP BY t.modality, t.status
            """)
    List<Object[]> countByModalityAndStatus(@Param("tenantId") UUID tenantId);

    /** Quality-score histogram buckets (0-19,20-39,...,80-100) for ACTIVE templates. */
    @Query("""
            SELECT (CASE WHEN t.qualityScore >= 80 THEN 80
                         WHEN t.qualityScore >= 60 THEN 60
                         WHEN t.qualityScore >= 40 THEN 40
                         WHEN t.qualityScore >= 20 THEN 20
                         ELSE 0 END) AS bucket, COUNT(t)
            FROM TemplateEntity t
            WHERE t.tenantId = :tenantId AND t.status = 'ACTIVE' AND t.qualityScore IS NOT NULL
            GROUP BY (CASE WHEN t.qualityScore >= 80 THEN 80
                          WHEN t.qualityScore >= 60 THEN 60
                          WHEN t.qualityScore >= 40 THEN 40
                          WHEN t.qualityScore >= 20 THEN 20
                          ELSE 0 END)
            """)
    List<Object[]> qualityHistogram(@Param("tenantId") UUID tenantId);
}
