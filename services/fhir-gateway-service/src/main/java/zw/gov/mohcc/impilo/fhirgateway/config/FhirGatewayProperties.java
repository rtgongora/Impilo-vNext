package zw.gov.mohcc.impilo.fhirgateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "fhir-gateway")
public class FhirGatewayProperties {

    private String backendUrl = "http://localhost:8090";
    private Pii pii = new Pii();
    private Outbox outbox = new Outbox();

    public static class Pii {
        private boolean enforce = true;

        public boolean isEnforce() {
            return enforce;
        }

        public void setEnforce(boolean enforce) {
            this.enforce = enforce;
        }
    }

    public static class Outbox {
        private long pollIntervalMs = 2000;

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }
    }

    public String getBackendUrl() {
        return backendUrl;
    }

    public void setBackendUrl(String backendUrl) {
        this.backendUrl = backendUrl;
    }

    public Pii getPii() {
        return pii;
    }

    public void setPii(Pii pii) {
        this.pii = pii;
    }

    public Outbox getOutbox() {
        return outbox;
    }

    public void setOutbox(Outbox outbox) {
        this.outbox = outbox;
    }
}
