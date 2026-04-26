package zw.gov.mohcc.impilo.costa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "impilo.integrations")
public class ImpiloIntegrationProperties {

    /**
     * Base URL for MusheX (payment intents, settlement). No trailing slash.
     */
    private String mushexBaseUrl = "http://localhost:8102";

    public String getMushexBaseUrl() {
        return mushexBaseUrl;
    }

    public void setMushexBaseUrl(String mushexBaseUrl) {
        this.mushexBaseUrl = mushexBaseUrl;
    }
}
