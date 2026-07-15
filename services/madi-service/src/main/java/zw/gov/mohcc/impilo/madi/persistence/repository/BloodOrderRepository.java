package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.BloodOrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BloodOrderRepository extends JpaRepository<BloodOrderEntity, Long> {
    Optional<BloodOrderEntity> findByOrderIdAndTenantId(UUID orderId, UUID tenantId);
    List<BloodOrderEntity> findByTenantIdAndFacilityIdOrderByCreatedAtDesc(UUID tenantId, UUID facilityId);
    List<BloodOrderEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);

    /** VITO merge repoint: move the blood order's patient anchor to the surviving Health ID. */
    @Modifying
    @Query("update BloodOrderEntity o set o.patientCpid = :survivor "
            + "where o.tenantId = :tenantId and o.patientCpid = :merged")
    int repointPatientCpid(@Param("tenantId") UUID tenantId,
                           @Param("merged") String mergedHealthId,
                           @Param("survivor") String survivorHealthId);
}
