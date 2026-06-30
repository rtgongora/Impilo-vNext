package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DeteriorationEscalationEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface DeteriorationEscalationRepository extends JpaRepository<DeteriorationEscalationEntity, UUID> {

    List<DeteriorationEscalationEntity> findByTenantIdAndSubjectCpidOrderByRaisedAtDesc(UUID tenantId, String subjectCpid);

    List<DeteriorationEscalationEntity> findByTenantIdAndWardIdAndStatusOrderByRaisedAtDesc(
            UUID tenantId, UUID wardId, String status);

    List<DeteriorationEscalationEntity> findByTenantIdAndStatusInAndResponseDueAtBefore(
            UUID tenantId, List<String> statuses, OffsetDateTime before);

    // Sweeper across tenants for the overdue scan (single-node dev/test).
    List<DeteriorationEscalationEntity> findByStatusInAndResponseDueAtBefore(
            List<String> statuses, OffsetDateTime before);
}
