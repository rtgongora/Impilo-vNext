package zw.gov.mohcc.impilo.tshepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tshepo.learning")
public class TshepoLearningRelayProperties {

    /** learning-service base URL (no trailing slash). */
    private String baseUrl = "";
    /** Shared secret presented by upstream orchestrators (e.g. Varapi) to Tshepo. */
    private String relayApiKey = "";
    /** Secret Tshepo sends to learning-service as {@code X-Impilo-Learning-Orchestration-Key}. */
    private String platformOrchestrationKey = "";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getRelayApiKey() {
        return relayApiKey;
    }

    public void setRelayApiKey(String relayApiKey) {
        this.relayApiKey = relayApiKey;
    }

    public String getPlatformOrchestrationKey() {
        return platformOrchestrationKey;
    }

    public void setPlatformOrchestrationKey(String platformOrchestrationKey) {
        this.platformOrchestrationKey = platformOrchestrationKey;
    }

    public boolean isRelayConfigured() {
        return baseUrl != null
                && !baseUrl.isBlank()
                && relayApiKey != null
                && !relayApiKey.isBlank()
                && platformOrchestrationKey != null
                && !platformOrchestrationKey.isBlank();
    }
}
