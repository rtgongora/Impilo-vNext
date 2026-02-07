package zw.gov.mohcc.impilo.varapi.api.dto;

public record ReconciliationDecisionRequest(
        Action action,
        String reason
) {
    public enum Action {
        ACCEPT,
        REJECT,
        MERGE,
        DEFER
    }
}
