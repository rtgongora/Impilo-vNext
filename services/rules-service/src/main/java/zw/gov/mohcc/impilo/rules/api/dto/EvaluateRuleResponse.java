package zw.gov.mohcc.impilo.rules.api.dto;

public record EvaluateRuleResponse(
        String ruleKey,
        int version,
        String outcome,
        String reason,
        String auditId
) {
}
