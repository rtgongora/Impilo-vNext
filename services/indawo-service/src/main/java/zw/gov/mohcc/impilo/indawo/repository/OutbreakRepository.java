package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.OutbreakEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutbreakRepository extends JpaRepository<OutbreakEntity, Long> {
    List<OutbreakEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
    List<OutbreakEntity> findByTenantIdAndStatusOrderByCreatedAtDesc(UUID tenantId, String status);
    Optional<OutbreakEntity> findByTenantIdAndOutbreakId(UUID tenantId, UUID outbreakId);
    long countByTenantIdAndStatusNot(UUID tenantId, String status);
}
