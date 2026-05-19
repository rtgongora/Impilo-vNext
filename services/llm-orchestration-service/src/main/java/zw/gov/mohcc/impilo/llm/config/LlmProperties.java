package zw.gov.mohcc.impilo.llm.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private boolean enabled = true;
    private String defaultProvider = "gemini";
    private String fallbackProvider = "deterministic";
    private String providerRoutingMode = "capability_then_priority";
    private List<String> allowedProviders = List.of("gemini", "openai", "anthropic", "deepseek", "deterministic", "mock");
    private boolean requireHumanApprovalForHighRiskActions = true;
    private boolean auditEnabled = true;
    private boolean toolCallingEnabled = true;
    private boolean structuredOutputsEnabled = true;
    private int timeoutMs = 30000;
    private int maxOutputTokens = 2048;
    private int retryMaxAttempts = 2;
    private int retryBackoffMs = 500;
    private ProviderConfig gemini = new ProviderConfig("gemini-3-flash-preview", "", "", true, 1);
    private ProviderConfig openai = new ProviderConfig("gpt-5.1", "", "", true, 2);
    private ProviderConfig anthropic = new ProviderConfig("claude-sonnet-4.5", "", "", true, 3);
    private ProviderConfig deepseek = new ProviderConfig("deepseek-v4-flash", "https://api.deepseek.com", "", true, 4);
    private ProviderConfig deterministic = new ProviderConfig("deterministic", "", "", true, 5);
    private ProviderConfig mock = new ProviderConfig("mock", "", "", true, 6);

    public record ProviderConfig(String model, String baseUrl, String apiKey, boolean enabled, int priority) {}

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getDefaultProvider() { return defaultProvider; }
    public void setDefaultProvider(String defaultProvider) { this.defaultProvider = defaultProvider; }
    public String getFallbackProvider() { return fallbackProvider; }
    public void setFallbackProvider(String fallbackProvider) { this.fallbackProvider = fallbackProvider; }
    public String getProviderRoutingMode() { return providerRoutingMode; }
    public void setProviderRoutingMode(String providerRoutingMode) { this.providerRoutingMode = providerRoutingMode; }
    public List<String> getAllowedProviders() { return allowedProviders; }
    public void setAllowedProviders(List<String> allowedProviders) { this.allowedProviders = allowedProviders; }
    public boolean isRequireHumanApprovalForHighRiskActions() { return requireHumanApprovalForHighRiskActions; }
    public void setRequireHumanApprovalForHighRiskActions(boolean requireHumanApprovalForHighRiskActions) { this.requireHumanApprovalForHighRiskActions = requireHumanApprovalForHighRiskActions; }
    public boolean isAuditEnabled() { return auditEnabled; }
    public void setAuditEnabled(boolean auditEnabled) { this.auditEnabled = auditEnabled; }
    public boolean isToolCallingEnabled() { return toolCallingEnabled; }
    public void setToolCallingEnabled(boolean toolCallingEnabled) { this.toolCallingEnabled = toolCallingEnabled; }
    public boolean isStructuredOutputsEnabled() { return structuredOutputsEnabled; }
    public void setStructuredOutputsEnabled(boolean structuredOutputsEnabled) { this.structuredOutputsEnabled = structuredOutputsEnabled; }
    public int getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
    public int getMaxOutputTokens() { return maxOutputTokens; }
    public void setMaxOutputTokens(int maxOutputTokens) { this.maxOutputTokens = maxOutputTokens; }
    public int getRetryMaxAttempts() { return retryMaxAttempts; }
    public void setRetryMaxAttempts(int retryMaxAttempts) { this.retryMaxAttempts = retryMaxAttempts; }
    public int getRetryBackoffMs() { return retryBackoffMs; }
    public void setRetryBackoffMs(int retryBackoffMs) { this.retryBackoffMs = retryBackoffMs; }
    public ProviderConfig getGemini() { return gemini; }
    public void setGemini(ProviderConfig gemini) { this.gemini = gemini; }
    public ProviderConfig getOpenai() { return openai; }
    public void setOpenai(ProviderConfig openai) { this.openai = openai; }
    public ProviderConfig getAnthropic() { return anthropic; }
    public void setAnthropic(ProviderConfig anthropic) { this.anthropic = anthropic; }
    public ProviderConfig getDeepseek() { return deepseek; }
    public void setDeepseek(ProviderConfig deepseek) { this.deepseek = deepseek; }
    public ProviderConfig getDeterministic() { return deterministic; }
    public void setDeterministic(ProviderConfig deterministic) { this.deterministic = deterministic; }
    public ProviderConfig getMock() { return mock; }
    public void setMock(ProviderConfig mock) { this.mock = mock; }
}
