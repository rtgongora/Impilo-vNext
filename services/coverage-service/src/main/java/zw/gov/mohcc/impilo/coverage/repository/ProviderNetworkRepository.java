package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.coverage.domain.ProviderNetworkEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProviderNetworkRepository extends JpaRepository<ProviderNetworkEntity, Long> {

    Optional<ProviderNetworkEntity> findByNetworkId(UUID networkId);

    @Query("""
            SELECT n FROM ProviderNetworkEntity n
            WHERE n.tenantId = :tenantId
              AND (:payerId IS NULL OR n.payerId = :payerId)
              AND (:status IS NULL OR n.status = :status)
            ORDER BY n.createdAt DESC
            """)
    List<ProviderNetworkEntity> search(
            @Param("tenantId") UUID tenantId,
            @Param("payerId") String payerId,
            @Param("status") String status);
}
