package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Request DTO for amending a placed order (OF-B1). Null fields are left unchanged;
 * the amendment is captured as a new immutable version with a mandatory reason.
 */
public record AmendOrderRequest(
        String clinicalNotes,
        String ziboOrderCode,
        String encounterRef,
        String safetyJson,
        @Valid List<OrderItemDto> items,
        @NotBlank String reason
) {}
