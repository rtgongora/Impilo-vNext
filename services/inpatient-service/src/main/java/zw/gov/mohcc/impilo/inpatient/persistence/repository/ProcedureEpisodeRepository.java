package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProcedureEpisodeRepository extends JpaRepository<ProcedureEpisodeEntity, UUID> {
    List<ProcedureEpisodeEntity> findByTenantIdAndSubjectCpidOrderByScheduledAtDesc(UUID tenantId, String subjectCpid);

    Optional<ProcedureEpisodeEntity> findByTenantIdAndBookingId(UUID tenantId, String bookingId);

    Optional<ProcedureEpisodeEntity> findByTenantIdAndOrosOrderId(UUID tenantId, String orosOrderId);

    List<ProcedureEpisodeEntity> findByTenantIdAndStatusInOrderByScheduledAtAsc(UUID tenantId, List<String> statuses);

    List<ProcedureEpisodeEntity> findByTenantIdOrderByScheduledAtAsc(UUID tenantId);
}
