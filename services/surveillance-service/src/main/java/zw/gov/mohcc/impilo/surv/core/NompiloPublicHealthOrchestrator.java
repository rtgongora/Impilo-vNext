package zw.gov.mohcc.impilo.surv.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NompiloPublicHealthOrchestrator {
    private final PublicHealthOperationsHomeService operationsHomeService;
    private final RestTemplate llmRestTemplate;
    private final String llmBaseUrl;
    private final boolean llmEnabled;

    public NompiloPublicHealthOrchestrator(
            PublicHealthOperationsHomeService operationsHomeService,
            RestTemplate llmRestTemplate,
            @Value("${surv.llm.base-url:http://localhost:8265}") String llmBaseUrl,
            @Value("${surv.llm.enabled:true}") boolean llmEnabled) {
        this.operationsHomeService = operationsHomeService;
        this.llmRestTemplate = llmRestTemplate;
        this.llmBaseUrl = llmBaseUrl;
        this.llmEnabled = llmEnabled;
    }

    public Map<String, Object> ask(UUID tenantId, String actorId, String prompt) {
        if (!llmEnabled) {
            return fallback(tenantId, true);
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("useCase", "NOMPILO_PUBLIC_HEALTH_ASK");
            payload.put("actorContext", Map.of(
                    "tenantId", tenantId.toString(),
                    "actorId", actorId != null ? actorId : "unknown",
                    "purposeOfUse", "PUBLIC_HEALTH_OPERATIONS"));
            payload.put("messages", List.of(
                    Map.of("role", "system", "content", "You are Nompilo Public Health intelligence assistant."),
                    Map.of("role", "user", "content", prompt != null ? prompt : "What is the public health situation today?")));
            payload.put("requiredCapabilities", List.of("CHAT", "STRUCTURED_OUTPUT", "TOOL_CALLING"));
            payload.put("riskLevel", "MODERATE");
            payload.put("requiresAudit", true);
            payload.put("requiresHumanApprovalForActions", true);
            Map<String, Object> raw = llmRestTemplate.postForEntity(llmBaseUrl + "/internal/v1/llm/structured", payload, Map.class).getBody();
            if (raw == null) {
                return fallback(tenantId, true);
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("provider", raw.getOrDefault("provider", "deterministic"));
            out.put("model", raw.getOrDefault("model", "deterministic"));
            out.put("fallbackUsed", raw.getOrDefault("fallbackUsed", false));
            out.put("requiresHumanApproval", raw.getOrDefault("requiresHumanApproval", true));
            out.put("content", raw.getOrDefault("content", ""));
            out.put("structuredOutput", raw.get("structuredOutput"));
            out.put("auditRef", raw.get("auditRef"));
            return out;
        } catch (Exception ex) {
            return fallback(tenantId, true);
        }
    }

    public Map<String, Object> briefing(UUID tenantId, String actorId, String topic) {
        String prompt = topic == null || topic.isBlank() ? "Prepare a role-aware district public-health briefing." : topic;
        return ask(tenantId, actorId, prompt);
    }

    private Map<String, Object> fallback(UUID tenantId, boolean deterministicOnly) {
        Map<String, Object> deterministic = operationsHomeService.buildNompiloSummary(tenantId);
        Map<String, Object> structured = new LinkedHashMap<>();
        structured.put("summary", deterministic.get("message"));
        structured.put("riskLevel", "MODERATE");
        structured.put("recommendedActions", deterministic.get("suggestions"));
        structured.put("suggestedTasks", List.of());
        structured.put("suggestedMessages", List.of());
        structured.put("requiresHumanApproval", true);
        structured.put("confidence", 0.65d);
        structured.put("sourceSignals", deterministic.getOrDefault("kpis", Map.of()));
        structured.put("auditMetadata", Map.of("source", "DETERMINISTIC_OPERATIONS_HOME"));
        return Map.of(
                "provider", "deterministic",
                "model", "deterministic",
                "fallbackUsed", true,
                "deterministicOnly", deterministicOnly,
                "requiresHumanApproval", true,
                "content", deterministic.getOrDefault("message", ""),
                "structuredOutput", structured,
                "auditRef", null
        );
    }
}
