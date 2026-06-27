package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.RouteDestinationType;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Request DTO for assigning a routing destination to a diagnostic order (the requester's
 * referral/routing action).
 */
public record RouteRequest(
        @NotNull RouteDestinationType destinationType,
        UUID destinationFacilityId,
        String destinationDepartmentId,
        String destinationServicePointId,
        String destinationProviderId,
        String destinationName,
        String expectedReturnMethod,
        OffsetDateTime routeExpiry
) {}
