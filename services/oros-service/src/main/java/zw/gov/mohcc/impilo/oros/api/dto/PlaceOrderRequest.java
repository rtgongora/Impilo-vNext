package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

import java.util.List;

/**
 * Request DTO for placing a new clinical order.
 */
public record PlaceOrderRequest(
        @NotNull OrderType orderType,
        OrderPriority priority,
        @NotBlank String patientCpid,
        String ziboOrderCode,
        String encounterRef,
        String clinicalNotes,
        @Valid List<OrderItemDto> items
) {}
