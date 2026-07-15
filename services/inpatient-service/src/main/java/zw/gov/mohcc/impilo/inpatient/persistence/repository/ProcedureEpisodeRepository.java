package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Wave 5b — identity repoint. When a PROVISIONAL trauma-patient identity is later reconciled via
     * {@code /patients/merge} → {@code vito.merge.executed}, repoint every theatre case that still carries
     * the merged (losing) CPID onto the surviving CPID, so the merge does not orphan the surgery record.
     * Returns the number of episodes repointed.
     */
    @Modifying
    @Query("update ProcedureEpisodeEntity e set e.subjectCpid = :survivor "
            + "where e.tenantId = :tenantId and e.subjectCpid = :merged")
    int repointSubjectCpid(@Param("tenantId") UUID tenantId,
                           @Param("merged") String merged,
                           @Param("survivor") String survivor);
}
