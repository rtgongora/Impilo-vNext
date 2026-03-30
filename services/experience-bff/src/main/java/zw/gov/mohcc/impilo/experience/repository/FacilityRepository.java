package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.Facility;

import java.util.UUID;

public interface FacilityRepository extends JpaRepository<Facility, UUID> {

    @Query("""
        SELECT f FROM Facility f
        WHERE f.tenantId = :tenantId
        AND (:status IS NULL OR f.status = :status)
        AND (:facilityType IS NULL OR f.facilityType = :facilityType)
        AND (:province IS NULL OR f.province = :province)
        AND (:pattern IS NULL OR LOWER(f.name) LIKE :pattern
             OR LOWER(f.code) LIKE :pattern)
        """)
    Page<Facility> findByFilters(
            @Param("tenantId") String tenantId,
            @Param("status") String status,
            @Param("facilityType") String facilityType,
            @Param("province") String province,
            @Param("pattern") String pattern,
            Pageable pageable);
}
