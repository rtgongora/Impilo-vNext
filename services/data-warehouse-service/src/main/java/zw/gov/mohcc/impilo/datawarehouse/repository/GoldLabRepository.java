package zw.gov.mohcc.impilo.datawarehouse.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldLabEntity;

import java.util.Optional;
import java.util.UUID;

public interface GoldLabRepository extends JpaRepository<GoldLabEntity, Long> {

    Optional<GoldLabEntity> findByTenantIdAndLabResultId(UUID tenantId, String labResultId);
}
