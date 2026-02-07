package zw.gov.mohcc.impilo.oros.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.oros.domain.RouteStatus;
import zw.gov.mohcc.impilo.oros.domain.RouteTarget;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link RoutingEntity} persistence operations.
 */
@Repository
public interface RoutingRepository extends JpaRepository<RoutingEntity, UUID> {

    /**
     * Finds all routing records for an order.
     *
     * @param orderId the ULID order identifier
     * @return list of routing records
     */
    List<RoutingEntity> findByOrderId(String orderId);

    /**
     * Finds routing records by status (e.g., PENDING or RETRYING for retry processing).
     *
     * @param status the route status
     * @return list of matching routing records
     */
    List<RoutingEntity> findByStatus(RouteStatus status);

    /**
     * Finds a routing record for an order filtered by route target.
     *
     * @param orderId     the ULID order identifier
     * @param routeTarget the target system
     * @return the routing record if found
     */
    Optional<RoutingEntity> findByOrderIdAndRouteTarget(String orderId, RouteTarget routeTarget);
}
