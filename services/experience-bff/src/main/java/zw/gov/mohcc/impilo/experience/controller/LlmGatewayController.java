package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.publichealth.PublicHealthGovernanceService;

import java.util.Map;

@RestController
public class LlmGatewayController {
    private final RestTemplate restTemplate;
    private final PublicHealthGovernanceService governance;
    private final String llmBaseUrl;

    public LlmGatewayController(
            RestTemplate serviceRestTemplate,
            PublicHealthGovernanceService governance,
            @org.springframework.beans.factory.annotation.Value("${impilo.services.llm-orchestration-base-url:http://localhost:8265}") String llmBaseUrl) {
        this.restTemplate = serviceRestTemplate;
        this.governance = governance;
        this.llmBaseUrl = llmBaseUrl;
    }

    @GetMapping("/internal/v1/llm/providers")
    public ResponseEntity<?> providers(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return restTemplate.getForEntity(llmBaseUrl + "/internal/v1/llm/providers", Map.class);
    }

    @GetMapping("/internal/v1/llm/providers/health")
    public ResponseEntity<?> providerHealth(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return restTemplate.getForEntity(llmBaseUrl + "/internal/v1/llm/providers/health", Map.class);
    }

    @PostMapping("/internal/v1/llm/providers/{provider}/test")
    public ResponseEntity<?> testProvider(
            @PathVariable String provider,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return restTemplate.postForEntity(llmBaseUrl + "/internal/v1/llm/providers/" + provider + "/test", Map.of(), Map.class);
    }

    @PostMapping("/internal/v1/llm/chat")
    public ResponseEntity<?> chat(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedRead();
        return restTemplate.postForEntity(llmBaseUrl + "/internal/v1/llm/chat", body, Map.class);
    }

    @PostMapping("/internal/v1/llm/structured")
    public ResponseEntity<?> structured(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedRead();
        return restTemplate.postForEntity(llmBaseUrl + "/internal/v1/llm/structured", body, Map.class);
    }

    @PostMapping("/internal/v1/public-health/nompilo/ask")
    public ResponseEntity<?> askPublicHealthNompilo(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedRead();
        Map<String, Object> payload = Map.of(
                "useCase", "NOMPILO_PUBLIC_HEALTH_ASK",
                "actorContext", Map.of(
                        "tenantId", tenantId != null ? tenantId : "",
                        "actorId", actorId != null ? actorId : "",
                        "purposeOfUse", "PUBLIC_HEALTH_OPERATIONS"),
                "messages", body.getOrDefault("messages", java.util.List.of(Map.of("role", "user", "content", String.valueOf(body.getOrDefault("prompt", ""))))),
                "requiredCapabilities", java.util.List.of("CHAT", "STRUCTURED_OUTPUT", "TOOL_CALLING"),
                "riskLevel", String.valueOf(body.getOrDefault("riskLevel", "MODERATE")),
                "requiresAudit", true,
                "requiresHumanApprovalForActions", true,
                "temperature", 0.2
        );
        return restTemplate.postForEntity(llmBaseUrl + "/internal/v1/llm/structured", payload, Map.class);
    }

    @PostMapping("/internal/v1/public-health/nompilo/briefing")
    public ResponseEntity<?> publicHealthBriefing(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(value = CompanionHeaders.TENANT_ID, required = false) String tenantId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedRead();
        Map<String, Object> payload = Map.of(
                "useCase", "NOMPILO_PUBLIC_HEALTH_BRIEFING",
                "actorContext", Map.of(
                        "tenantId", tenantId != null ? tenantId : "",
                        "actorId", actorId != null ? actorId : "",
                        "purposeOfUse", "PUBLIC_HEALTH_OPERATIONS"),
                "messages", java.util.List.of(
                        Map.of("role", "system", "content", "Generate role-aware public health briefing"),
                        Map.of("role", "user", "content", String.valueOf(body.getOrDefault("prompt", "Summarize public health situation today")))),
                "requiredCapabilities", java.util.List.of("CHAT", "STRUCTURED_OUTPUT"),
                "riskLevel", "MODERATE",
                "requiresAudit", true,
                "requiresHumanApprovalForActions", true,
                "temperature", 0.2
        );
        return restTemplate.postForEntity(llmBaseUrl + "/internal/v1/llm/structured", payload, Map.class);
    }
}
