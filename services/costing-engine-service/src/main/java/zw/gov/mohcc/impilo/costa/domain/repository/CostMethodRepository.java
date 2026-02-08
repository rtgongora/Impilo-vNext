package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.CostMethodEntity;
import zw.gov.mohcc.impilo.costa.domain.enums.CostMethodType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CostMethodRepository extends JpaRepository<CostMethodEntity, Long> {
    List<CostMethodEntity> findByTenantIdAndStatus(UUID tenantId, String status);
    Optional<CostMethodEntity> findByTenantIdAndMethodTypeAndStatus(UUID tenantId, CostMethodType methodType, String status);
    Optional<CostMethodEntity> findByTenantIdAndNameAndVersion(UUID tenantId, String name, int version);
}
