package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.CertificateEntity;

public interface CertificateRepository extends JpaRepository<CertificateEntity, UUID> {

    Optional<CertificateEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<CertificateEntity> findByEnrolmentId(UUID enrolmentId);

    List<CertificateEntity> findByTenantIdAndSubjectTypeAndSubjectId(
            UUID tenantId, String subjectType, String subjectId);

    List<CertificateEntity> findByTenantIdAndCourseId(UUID tenantId, UUID courseId);

    /** Active certificates whose validity has lapsed — drives the expiry sweep. */
    List<CertificateEntity> findByStatusAndValidUntilNotNullAndValidUntilBefore(
            String status, OffsetDateTime cutoff, Pageable pageable);

    /** Active, not-yet-reminded certificates entering the pre-expiry refresher window. */
    List<CertificateEntity> findByStatusAndRefresherNotifiedAtIsNullAndValidUntilNotNullAndValidUntilBetween(
            String status, OffsetDateTime from, OffsetDateTime to, Pageable pageable);
}
