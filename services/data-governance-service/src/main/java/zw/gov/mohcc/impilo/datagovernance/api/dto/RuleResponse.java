package zw.gov.mohcc.impilo.datagovernance.api.dto;

import java.util.UUID;

public record RuleResponse(UUID ruleId, UUID tenantId, String name, String description,
                            String resourcePattern, String action, String effect,
                            String requiredPurpose, int priority, boolean active, String createdAt) {}
