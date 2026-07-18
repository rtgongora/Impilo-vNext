package zw.gov.mohcc.impilo.inpatient.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EmergencyActivationEntity;

import java.util.List;
import java.util.UUID;

public interface EmergencyActivationRepository extends JpaRepository<EmergencyActivationEntity, UUID> {
    List<EmergencyActivationEntity> findByTenantIdOrderByActivationTimeDesc(UUID tenantId);

    /**
     * VITO merge repoint: move the emergency activation's patient anchor to the surviving Health ID.
     * This is the resuscitation patient anchor — resuscitation_record has no patient column and
     * follows through activation_id → emergency_activation.subject_cpid.
     */
    @Modifying
    @Query("update EmergencyActivationEntity a set a.subjectCpid = :survivor "
            + "where a.tenantId = :tenantId and a.subjectCpid = :merged")
    int repointSubjectCpid(@Param("tenantId") UUID tenantId,
                           @Param("merged") String mergedCpid,
                           @Param("survivor") String survivorCpid);
}
