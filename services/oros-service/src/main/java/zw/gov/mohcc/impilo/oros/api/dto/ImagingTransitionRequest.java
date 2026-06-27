package zw.gov.mohcc.impilo.oros.api.dto;

import jakarta.validation.constraints.NotNull;
import zw.gov.mohcc.impilo.oros.domain.ImagingWorkflowState;

/**
 * Generic request DTO to drive an explicit imaging-workflow transition.
 *
 * <p>The target state is validated against the deny-by-default
 * {@link zw.gov.mohcc.impilo.oros.core.ImagingWorkflow} guard. Used by the fulfilment and
 * reporting waves (receive/accept/reject/start/complete/...); the convenience endpoints
 * (schedule/arrive/release) wrap specific targets.</p>
 */
public record ImagingTransitionRequest(
        @NotNull ImagingWorkflowState target,
        String reason
) {}
