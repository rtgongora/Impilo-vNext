package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyIncidentRepository extends JpaRepository<EmergencyIncidentEntity, UUID> {
    Optional<EmergencyIncidentEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** VITO merge repoint: move the subject anchor from the tombstoned to the surviving Health ID. */
    @Modifying
    @Query("update EmergencyIncidentEntity i set i.subjectHealthId = :survivor "
            + "where i.tenantId = :tenantId and i.subjectHealthId = :merged")
    int repointSubjectHealthId(@Param("tenantId") UUID tenantId,
                               @Param("merged") String mergedHealthId,
                               @Param("survivor") String survivorHealthId);
    List<EmergencyIncidentEntity> findByTenantIdOrderByOpenedAtDesc(UUID tenantId);
    List<EmergencyIncidentEntity> findByTenantIdAndIncidentTypeOrderByOpenedAtDesc(UUID tenantId, String incidentType);
    List<EmergencyIncidentEntity> findByTenantIdAndStatusOrderByOpenedAtDesc(UUID tenantId, String status);
}
