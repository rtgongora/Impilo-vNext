package zw.gov.mohcc.impilo.costa.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "costa")
public class CostaProperties {

    private Outbox outbox = new Outbox();
    private String currency = "USD";
    private String defaultCostMethod = "TARIFF";
    private StepUp stepUp = new StepUp();

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getDefaultCostMethod() { return defaultCostMethod; }
    public void setDefaultCostMethod(String defaultCostMethod) { this.defaultCostMethod = defaultCostMethod; }
    public StepUp getStepUp() { return stepUp; }
    public void setStepUp(StepUp stepUp) { this.stepUp = stepUp; }

    public static class Outbox {
        private long pollIntervalMs = 2000;
        private int batchSize = 100;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class StepUp {
        private boolean tariffPublish = true;
        private boolean rulePublish = true;
        private boolean billOverride = true;
        private BigDecimal refundThreshold = new BigDecimal("1000.00");

        public boolean isTariffPublish() { return tariffPublish; }
        public void setTariffPublish(boolean tariffPublish) { this.tariffPublish = tariffPublish; }
        public boolean isRulePublish() { return rulePublish; }
        public void setRulePublish(boolean rulePublish) { this.rulePublish = rulePublish; }
        public boolean isBillOverride() { return billOverride; }
        public void setBillOverride(boolean billOverride) { this.billOverride = billOverride; }
        public BigDecimal getRefundThreshold() { return refundThreshold; }
        public void setRefundThreshold(BigDecimal refundThreshold) { this.refundThreshold = refundThreshold; }
    }
}
