package zw.gov.mohcc.impilo.varapi.api.dto;

public record PrivilegeDecisionRequest(
        Action action,
        String reason
) {
    public enum Action {
        APPROVED,
        REJECTED
    }
}
