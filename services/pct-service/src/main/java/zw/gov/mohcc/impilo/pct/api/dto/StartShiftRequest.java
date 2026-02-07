package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to start a clinical shift at a facility workspace.
 *
 * <p>Initiates a workspace session that tracks which provider is
 * signed into which workspace for shift-based duty management.</p>
 *
 * @param facilityId  the facility where the shift is being started
 * @param workspaceId the workspace (service point) to sign into
 * @param dutyMode    the duty mode for this shift (e.g. CLINICAL, ADMINISTRATIVE)
 */
public record StartShiftRequest(
        @NotNull UUID facilityId,
        @NotNull UUID workspaceId,
        @NotBlank String dutyMode
) {}
