package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Request DTO for placing or drafting a clinical order.
 *
 * <p>Used both by the legacy immediate-place endpoint ({@code POST /v1/orders}) and the
 * diagnostic draft endpoint ({@code POST /v1/orders/draft}). The diagnostic/imaging journey
 * fields ({@code requestSource}, referring provider, {@code scheduledAt}, {@code safetyJson})
 * are optional and default sensibly for lab/pharmacy orders.</p>
 */
public record PlaceOrderRequest(
        @NotNull OrderType orderType,
        OrderPriority priority,
        @NotBlank String patientCpid,
        String ziboOrderCode,
        String encounterRef,
        String clinicalNotes,
        @Valid List<OrderItemDto> items,
        // ── Diagnostic/imaging journey (optional) ──
        RequestSource requestSource,
        String referringProviderId,
        String referringProviderName,
        OffsetDateTime scheduledAt,
        String safetyJson
) {}
