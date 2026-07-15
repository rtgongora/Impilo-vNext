package zw.gov.mohcc.impilo.simba.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.simba.persistence.entity.AssessmentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssessmentRepository extends JpaRepository<AssessmentEntity, Long> {

    Optional<AssessmentEntity> findByAssessmentIdAndTenantId(UUID assessmentId, UUID tenantId);

    List<AssessmentEntity> findByTenantIdAndPersonCpidOrderByCreatedAtDesc(UUID tenantId, String personCpid);

    Optional<AssessmentEntity> findFirstByTenantIdAndPersonCpidAndTemplateCodeAndStatusInOrderByCreatedAtDesc(
            UUID tenantId, String personCpid, String templateCode, List<String> statuses);

    Optional<AssessmentEntity> findFirstByTenantIdAndPersonCpidAndTemplateCodeAndStatusOrderByCompletedAtDesc(
            UUID tenantId, String personCpid, String templateCode, String status);
}
