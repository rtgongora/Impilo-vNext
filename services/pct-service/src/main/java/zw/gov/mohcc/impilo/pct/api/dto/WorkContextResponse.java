package zw.gov.mohcc.impilo.pct.api.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response containing the active workspace session context.
 *
 * <p>Returned after starting a shift or querying the current work context.
 * Contains all the identifiers needed to scope subsequent PCT operations
 * to the correct facility, workspace, and shift.</p>
 *
 * @param sessionId   the unique session identifier
 * @param facilityId  the facility where the session is active
 * @param workspaceId the workspace (service point) the provider is signed into
 * @param dutyMode    the duty mode for this session (e.g. CLINICAL, ADMINISTRATIVE)
 * @param shiftId     the shift identifier, if applicable
 * @param startedAt   when the session was started
 */
public record WorkContextResponse(
        UUID sessionId,
        UUID facilityId,
        UUID workspaceId,
        String dutyMode,
        String shiftId,
        OffsetDateTime startedAt
) {}
