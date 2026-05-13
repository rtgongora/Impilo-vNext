package zw.gov.mohcc.impilo.learning.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.learning.persistence.entity.EnrolmentEntity;

public interface EnrolmentRepository extends JpaRepository<EnrolmentEntity, UUID> {

    Optional<EnrolmentEntity> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<EnrolmentEntity> findFirstByTenantIdAndSubjectTypeAndSubjectIdAndCourseIdAndStatusIn(
            UUID tenantId, String subjectType, String subjectId, UUID courseId, List<String> statuses);

    List<EnrolmentEntity> findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            UUID tenantId, String subjectType, String subjectId, Pageable pageable);

    List<EnrolmentEntity> findByTenantIdAndCourseIdOrderByCreatedAtDesc(
            UUID tenantId, UUID courseId, Pageable pageable);

    List<EnrolmentEntity> findByTenantIdAndCourseId(UUID tenantId, UUID courseId);

    List<EnrolmentEntity> findByTenantIdAndSubjectTypeAndSubjectIdAndStatus(
            UUID tenantId, String subjectType, String subjectId, String status);
}
