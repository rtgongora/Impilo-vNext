package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.RouteStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentRouteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface FulfillmentRouteRepository extends JpaRepository<FulfillmentRouteEntity, String> {
    Optional<FulfillmentRouteEntity> findByOrderId(String orderId);
    List<FulfillmentRouteEntity> findByStatus(RouteStatus status);
    List<FulfillmentRouteEntity> findByStatusAndRetryCountLessThan(RouteStatus status, int maxRetries);
}
