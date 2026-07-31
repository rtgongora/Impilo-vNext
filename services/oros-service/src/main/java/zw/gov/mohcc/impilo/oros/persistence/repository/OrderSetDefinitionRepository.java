package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.oros.persistence.entity.OrderSetDefinitionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderSetDefinitionRepository extends JpaRepository<OrderSetDefinitionEntity, UUID> {
    List<OrderSetDefinitionEntity> findByTenantIdAndActiveTrueOrderBySetNameAsc(UUID tenantId);
    List<OrderSetDefinitionEntity> findByActiveTrueOrderBySetNameAsc();
    Optional<OrderSetDefinitionEntity> findByTenantIdAndSetId(UUID tenantId, UUID setId);
    Optional<OrderSetDefinitionEntity> findBySetId(UUID setId);
}
