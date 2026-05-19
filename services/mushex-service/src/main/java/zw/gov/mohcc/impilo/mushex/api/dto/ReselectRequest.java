package zw.gov.mohcc.impilo.mushex.api.dto;

/**
 * Phase 3 follow-on — request body for the reselection endpoint
 * {@code POST /mushex/v1/payment-intents/{id}/attempts/reselect}.
 *
 * <p>Both fields are optional. The operator-supplied {@code reason} is recorded in
 * {@code metadata.rail_selection.reason_detail} and on the {@code ATTEMPT_RESELECTED}
 * outbox event.
 */
public record ReselectRequest(
        String reason
) {
    public ReselectRequest() {
        this(null);
    }
}
