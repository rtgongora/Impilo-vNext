package zw.gov.mohcc.impilo.mushex.service.rail;

import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;
import zw.gov.mohcc.impilo.mushex.domain.enums.RailSelectionReason;

/**
 * Outcome of a successful rail selection. A failed selection is reported via
 * {@link RailSelectionException} and never produces a {@code RailSelectionResult}.
 *
 * <p>See {@code docs/design/g4-rail-selection-policy.md} §5.2.
 */
public record RailSelectionResult(
        AdapterType effectiveRail,
        AdapterType preferredRail,
        boolean fallbackApplied,
        boolean directGatewayRequested,
        RailSelectionReason reason,
        String reasonDetail
) {
    public RailSelectionResult {
        if (effectiveRail == null) {
            throw new IllegalArgumentException("effectiveRail must not be null");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason must not be null");
        }
        if (reason.name().startsWith("REJECTED_")) {
            throw new IllegalArgumentException(
                    "RailSelectionResult cannot carry a REJECTED_* reason; throw RailSelectionException instead");
        }
    }
}
