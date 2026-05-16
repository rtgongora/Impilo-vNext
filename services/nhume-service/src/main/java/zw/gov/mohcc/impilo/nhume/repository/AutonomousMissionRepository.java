package zw.gov.mohcc.impilo.nhume.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.nhume.domain.AutonomousMissionEntity;

import java.util.List;
import java.util.UUID;

public interface AutonomousMissionRepository extends JpaRepository<AutonomousMissionEntity, UUID> {

    List<AutonomousMissionEntity> findByDeliveryIdOrderByCreatedAtDesc(UUID deliveryId);

    @Query("SELECT m FROM AutonomousMissionEntity m WHERE m.tenantId = :tenantId"
            + " AND (:status IS NULL OR m.status = :status)"
            + " AND (:providerCode IS NULL OR m.providerCode = :providerCode)"
            + " ORDER BY m.createdAt DESC")
    Page<AutonomousMissionEntity> findFiltered(@Param("tenantId") UUID tenantId,
                                                @Param("status") String status,
                                                @Param("providerCode") String providerCode,
                                                Pageable pageable);
}
