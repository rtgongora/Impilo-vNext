package zw.gov.mohcc.impilo.tshepo.keys.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tshepo.keys.persistence.entity.KeyRotationLogEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface KeyRotationLogRepository extends JpaRepository<KeyRotationLogEntity, Long> {

    List<KeyRotationLogEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
