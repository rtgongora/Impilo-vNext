package zw.gov.mohcc.impilo.experience.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "impilo.clinical-platform")
public record ClinicalPlatformProperties(String baseUrl) {
    public ClinicalPlatformProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8270";
        }
    }
}
