package zw.gov.mohcc.impilo.msikaflow.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record BookingCreateRequest(
        String patientCpid,
        @NotNull UUID facilityId,
        String serviceCode,
        String idempotencyKey
) {}
