package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request to assign or reassign a bed to an admitted patient.
 *
 * <p>Used after initial admission when bed assignment was deferred,
 * or when a patient needs to be moved to a different bed within
 * the same ward.</p>
 *
 * @param wardId the ward containing the bed
 * @param bedId  the specific bed to assign
 */
public record AssignBedRequest(
        @NotNull UUID wardId,
        @NotNull UUID bedId
) {}
