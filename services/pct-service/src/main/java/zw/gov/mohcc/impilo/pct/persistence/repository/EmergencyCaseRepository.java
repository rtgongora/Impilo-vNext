package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.pct.persistence.entity.EmergencyCaseEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyCaseRepository extends JpaRepository<EmergencyCaseEntity, Long> {
    Optional<EmergencyCaseEntity> findByEmergencyCaseId(UUID emergencyCaseId);
    List<EmergencyCaseEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);

    /** VITO merge repoint: move the emergency case's known Health ID to the surviving one. */
    @Modifying
    @Query("update EmergencyCaseEntity c set c.knownSubjectCpid = :survivor "
            + "where c.tenantId = :tenantId and c.knownSubjectCpid = :merged")
    int repointKnownSubjectCpid(@Param("tenantId") UUID tenantId,
                             @Param("merged") String mergedHealthId,
                             @Param("survivor") String survivorHealthId);
}
