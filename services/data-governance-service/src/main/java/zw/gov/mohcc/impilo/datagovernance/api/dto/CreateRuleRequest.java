package zw.gov.mohcc.impilo.datagovernance.api.dto;

public record CreateRuleRequest(String name, String description, String resourcePattern,
                                 String action, String effect, String requiredPurpose, Integer priority) {}
