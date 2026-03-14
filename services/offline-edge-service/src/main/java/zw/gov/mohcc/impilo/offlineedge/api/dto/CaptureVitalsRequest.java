package zw.gov.mohcc.impilo.offlineedge.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for {@code POST /internal/v1/offline/vitals}.
 *
 * <p>This endpoint accepts vitals captured during an offline session.
 * The entitlement is verified via the {@code X-Offline-Entitlement} header (JWT),
 * not from the request body.</p>
 */
public record CaptureVitalsRequest(
        @NotBlank String patientRef,
        @NotNull Map<String, Object> vitals,
        @NotBlank String capturedAt,
        String deviceId,
        int sequenceNum,
        String idempotencyKey,
        String correlationId
) {}
