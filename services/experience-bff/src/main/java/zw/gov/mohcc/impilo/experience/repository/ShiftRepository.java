package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.experience.domain.Shift;

import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    @Query("""
        SELECT s FROM Shift s
        WHERE s.tenantId = :tenantId
        AND s.userId = :userId
        AND s.status = 'ACTIVE'
        """)
    Optional<Shift> findCurrentShift(
            @Param("tenantId") String tenantId,
            @Param("userId") String userId);

    Page<Shift> findByTenantIdAndFacilityId(String tenantId, String facilityId, Pageable pageable);
}
