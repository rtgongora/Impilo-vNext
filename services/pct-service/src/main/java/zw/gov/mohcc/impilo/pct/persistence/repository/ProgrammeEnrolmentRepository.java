package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.ProgrammeEnrolmentEntity;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgrammeEnrolmentRepository extends JpaRepository<ProgrammeEnrolmentEntity, UUID> {

    List<ProgrammeEnrolmentEntity> findByTenantIdAndSubjectCpidOrderByEnrolledOnDesc(
            UUID tenantId, String subjectCpid);

    List<ProgrammeEnrolmentEntity> findByTenantIdAndSubjectCpidAndStatusInOrderByEnrolledOnDesc(
            UUID tenantId, String subjectCpid, Collection<String> statuses);

    List<ProgrammeEnrolmentEntity> findByTenantIdAndSubjectCpidAndProgrammeOrderByEnrolledOnDesc(
            UUID tenantId, String subjectCpid, String programme);

    /** The one active enrolment for a programme, if any — the partial-unique constraint guarantees ≤1. */
    Optional<ProgrammeEnrolmentEntity> findFirstByTenantIdAndSubjectCpidAndProgrammeAndStatusNot(
            UUID tenantId, String subjectCpid, String programme, String status);

    Optional<ProgrammeEnrolmentEntity> findByTenantIdAndClientOfflineId(UUID tenantId, String clientOfflineId);
}
