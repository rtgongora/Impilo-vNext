package zw.gov.mohcc.impilo.llm.core;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmRequest;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmToolCall;
import zw.gov.mohcc.impilo.llm.core.LlmModels.LlmToolResult;

@Service
public class LlmToolExecutionGateway {
    private static final Set<String> ALLOWED_TOOLS = Set.of(
            "searchPublicHealthSignals",
            "getInspectionSummary",
            "getFacilityPublicHealthProfile",
            "getDistrictRiskDashboard",
            "getOpenAlerts",
            "createDraftPublicHealthTask",
            "createDraftCommsMessage",
            "recommendInspectionSchedule",
            "summarizePublicHealthSituation",
            "generateRoleAwareBriefing",
            "explainIndicatorTrend",
            "suggestEscalationPath"
    );
    private static final Set<String> HIGH_RISK_TOOLS = Set.of("createDraftPublicHealthTask", "createDraftCommsMessage");

    private final LlmToolCallLogRepository logRepository;

    public LlmToolExecutionGateway(LlmToolCallLogRepository logRepository) {
        this.logRepository = logRepository;
    }

    public LlmToolResult execute(String provider, LlmRequest request, LlmToolCall call) {
        boolean allowed = ALLOWED_TOOLS.contains(call.name());
        boolean requiresApproval = HIGH_RISK_TOOLS.contains(call.name()) || request.requiresHumanApprovalForActions();
        Map<String, Object> output = allowed
                ? Map.of("status", "SIMULATED", "tool", call.name(), "arguments", call.arguments())
                : Map.of("status", "BLOCKED", "reason", "Tool not allowed");
        LlmToolCallLogEntity entity = new LlmToolCallLogEntity();
        entity.setProvider(provider);
        entity.setToolName(call.name());
        entity.setDecision(allowed ? (requiresApproval ? "DRAFT_REQUIRES_APPROVAL" : "EXECUTED") : "BLOCKED");
        entity.setInputSummary(call.arguments() != null ? call.arguments().toString() : "{}");
        entity.setOutputSummary(output.toString());
        entity.setRequiresHumanApproval(requiresApproval);
        Object tenant = request.actorContext() != null ? request.actorContext().get("tenantId") : null;
        if (tenant != null) {
            try {
                entity.setTenantId(UUID.fromString(String.valueOf(tenant)));
            } catch (IllegalArgumentException ignored) {
                // no-op
            }
        }
        if (request.actorContext() != null && request.actorContext().get("actorId") != null) {
            entity.setActorId(String.valueOf(request.actorContext().get("actorId")));
        }
        logRepository.save(entity);
        return new LlmToolResult(call.id(), call.name(), output, allowed, allowed ? null : "TOOL_NOT_ALLOWED");
    }
}
