package zw.gov.mohcc.impilo.rules.api.dto;

public record DecisionResult(
        String ruleId,
        String ruleName,
        String outcome,
        String reason
) {
}
