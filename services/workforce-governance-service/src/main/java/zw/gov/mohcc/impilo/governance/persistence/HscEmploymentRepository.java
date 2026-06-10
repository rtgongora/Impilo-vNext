package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface HscEmploymentRepository extends JpaRepository<HscEmploymentEntity, UUID> {

    List<HscEmploymentEntity> findByTenantIdAndProviderWorkerIdOrderByUpdatedAtDesc(UUID tenantId, String providerWorkerId);

    List<HscEmploymentEntity> findByTenantIdAndLinkedHealthIdOrderByUpdatedAtDesc(UUID tenantId, String linkedHealthId);

    @Query("""
            SELECT h FROM HscEmploymentEntity h
            WHERE h.tenantId = :tenantId
            AND (:providerWorkerId IS NULL OR h.providerWorkerId = :providerWorkerId)
            AND (:healthId IS NULL OR h.linkedHealthId = :healthId)
            AND (:facilityId IS NULL OR h.currentPostingFacility = :facilityId)
            AND (:province IS NULL OR h.currentPostingProvince = :province)
            AND (:district IS NULL OR h.currentPostingDistrict = :district)
            AND (:status IS NULL OR h.employmentStatus = :status)
            ORDER BY h.updatedAt DESC
            """)
    List<HscEmploymentEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("providerWorkerId") String providerWorkerId,
            @Param("healthId") String healthId,
            @Param("facilityId") String facilityId,
            @Param("province") String province,
            @Param("district") String district,
            @Param("status") String status);
}
