package zw.gov.mohcc.impilo.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.integration.domain.RouteDefinitionEntity;

import java.util.List;

@Repository
public interface RouteDefinitionRepository extends JpaRepository<RouteDefinitionEntity, String> {

    List<RouteDefinitionEntity> findByTenantId(String tenantId);

    List<RouteDefinitionEntity> findByTenantIdAndEnabled(String tenantId, boolean enabled);
}
