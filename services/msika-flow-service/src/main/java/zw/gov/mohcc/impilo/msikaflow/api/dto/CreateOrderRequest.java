package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
        @NotNull String orderType,
        String patientCpid,
        UUID facilityId,
        String facilityRef,
        UUID vendorId,
        String vendorRef,
        String providerRef,
        String idempotencyKey,
        List<LineItemRequest> lines
) {}
