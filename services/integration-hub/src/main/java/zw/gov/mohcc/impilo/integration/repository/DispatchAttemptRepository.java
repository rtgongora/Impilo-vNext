package zw.gov.mohcc.impilo.integration.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.integration.domain.DispatchAttemptEntity;

import java.util.List;

@Repository
public interface DispatchAttemptRepository extends JpaRepository<DispatchAttemptEntity, String> {

    List<DispatchAttemptEntity> findByTenantId(String tenantId);

    List<DispatchAttemptEntity> findByRouteId(String routeId);
}
