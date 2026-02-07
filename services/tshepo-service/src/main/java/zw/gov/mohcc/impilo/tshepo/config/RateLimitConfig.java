package zw.gov.mohcc.impilo.tshepo.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "tshepo.rate-limit")
public class RateLimitConfig {

    private int maxRequestsPerMinute = 120;
    private int stepUpWindowSeconds = 300;

    public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
    public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }
    public int getStepUpWindowSeconds() { return stepUpWindowSeconds; }
    public void setStepUpWindowSeconds(int stepUpWindowSeconds) { this.stepUpWindowSeconds = stepUpWindowSeconds; }
}
