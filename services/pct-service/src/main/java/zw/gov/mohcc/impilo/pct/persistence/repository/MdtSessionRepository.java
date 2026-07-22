package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.MdtSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MdtSessionRepository extends JpaRepository<MdtSessionEntity, UUID> {
    Optional<MdtSessionEntity> findByTenantIdAndMdtSessionId(UUID tenantId, UUID mdtSessionId);
    List<MdtSessionEntity> findTop50ByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
