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

    /**
     * Base URL for coverage-service (member coverage, eligibility, claims). No trailing slash.
     */
    private String coverageBaseUrl = "http://localhost:8140";

    public String getCoverageBaseUrl() {
        return coverageBaseUrl;
    }

    public void setCoverageBaseUrl(String coverageBaseUrl) {
        this.coverageBaseUrl = coverageBaseUrl;
    }

    /**
     * Base URL for VARAPI provider registry (recognition lookups). No trailing slash.
     */
    private String varapiBaseUrl = "http://localhost:8083";

    public String getVarapiBaseUrl() {
        return varapiBaseUrl;
    }

    public void setVarapiBaseUrl(String varapiBaseUrl) {
        this.varapiBaseUrl = varapiBaseUrl;
    }

    /**
     * Base URL for Vashandi workforce service (recognition lookups). No trailing slash.
     */
    private String vashandiBaseUrl = "http://localhost:8167";

    public String getVashandiBaseUrl() {
        return vashandiBaseUrl;
    }

    public void setVashandiBaseUrl(String vashandiBaseUrl) {
        this.vashandiBaseUrl = vashandiBaseUrl;
    }
}
