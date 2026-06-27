package zw.gov.mohcc.impilo.oros.api.dto;

import zw.gov.mohcc.impilo.oros.domain.AdapterMode;
import zw.gov.mohcc.impilo.oros.domain.RouteDestinationType;
import zw.gov.mohcc.impilo.oros.domain.RouteStatus;
import zw.gov.mohcc.impilo.oros.domain.RouteTarget;
import zw.gov.mohcc.impilo.oros.persistence.entity.RoutingEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for an order's route, including its assigned destination.
 */
public record RouteDto(
        UUID routeId,
        String orderId,
        RouteTarget routeTarget,
        AdapterMode adapterMode,
        RouteStatus status,
        RouteDestinationType destinationType,
        UUID destinationFacilityId,
        String destinationDepartmentId,
        String destinationServicePointId,
        String destinationProviderId,
        String destinationName,
        String expectedReturnMethod,
        OffsetDateTime routeExpiry
) {
    public static RouteDto from(RoutingEntity e) {
        return new RouteDto(
                e.getRouteId(), e.getOrderId(), e.getRouteTarget(), e.getAdapterMode(), e.getStatus(),
                e.getDestinationType(), e.getDestinationFacilityId(), e.getDestinationDepartmentId(),
                e.getDestinationServicePointId(), e.getDestinationProviderId(), e.getDestinationName(),
                e.getExpectedReturnMethod(), e.getRouteExpiry());
    }
}
