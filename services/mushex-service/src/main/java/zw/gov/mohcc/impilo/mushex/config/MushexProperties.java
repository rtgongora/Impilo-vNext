package zw.gov.mohcc.impilo.mushex.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@ConfigurationProperties(prefix = "mushex")
public class MushexProperties {

    private Outbox outbox = new Outbox();
    private String currency = "USD";
    private String hmacPepper = "mushex-dev-pepper-change-in-prod";
    private Remittance remittance = new Remittance();
    private StepUp stepUp = new StepUp();
    private Adapters adapters = new Adapters();

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getHmacPepper() { return hmacPepper; }
    public void setHmacPepper(String hmacPepper) { this.hmacPepper = hmacPepper; }
    public Remittance getRemittance() { return remittance; }
    public void setRemittance(Remittance remittance) { this.remittance = remittance; }
    public StepUp getStepUp() { return stepUp; }
    public void setStepUp(StepUp stepUp) { this.stepUp = stepUp; }
    public Adapters getAdapters() { return adapters; }
    public void setAdapters(Adapters adapters) { this.adapters = adapters; }

    public static class Outbox {
        private long pollIntervalMs = 2000;
        private int batchSize = 100;

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Remittance {
        private int otpLength = 6;
        private int expiryMinutes = 60;
        private int maxClaimsPerToken = 1;
        private int rateLimitPerMinute = 5;

        public int getOtpLength() { return otpLength; }
        public void setOtpLength(int otpLength) { this.otpLength = otpLength; }
        public int getExpiryMinutes() { return expiryMinutes; }
        public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }
        public int getMaxClaimsPerToken() { return maxClaimsPerToken; }
        public void setMaxClaimsPerToken(int maxClaimsPerToken) { this.maxClaimsPerToken = maxClaimsPerToken; }
        public int getRateLimitPerMinute() { return rateLimitPerMinute; }
        public void setRateLimitPerMinute(int rateLimitPerMinute) { this.rateLimitPerMinute = rateLimitPerMinute; }
    }

    public static class StepUp {
        private boolean payoutRelease = true;
        private boolean destinationChange = true;
        private BigDecimal largeRefundThreshold = new BigDecimal("1000.00");
        private boolean claimOverride = true;
        private boolean settlementRelease = true;
        private boolean crossFacilityDashboard = true;

        public boolean isPayoutRelease() { return payoutRelease; }
        public void setPayoutRelease(boolean payoutRelease) { this.payoutRelease = payoutRelease; }
        public boolean isDestinationChange() { return destinationChange; }
        public void setDestinationChange(boolean destinationChange) { this.destinationChange = destinationChange; }
        public BigDecimal getLargeRefundThreshold() { return largeRefundThreshold; }
        public void setLargeRefundThreshold(BigDecimal largeRefundThreshold) { this.largeRefundThreshold = largeRefundThreshold; }
        public boolean isClaimOverride() { return claimOverride; }
        public void setClaimOverride(boolean claimOverride) { this.claimOverride = claimOverride; }
        public boolean isSettlementRelease() { return settlementRelease; }
        public void setSettlementRelease(boolean settlementRelease) { this.settlementRelease = settlementRelease; }
        public boolean isCrossFacilityDashboard() { return crossFacilityDashboard; }
        public void setCrossFacilityDashboard(boolean crossFacilityDashboard) { this.crossFacilityDashboard = crossFacilityDashboard; }
    }

    public static class Adapters {
        private Sandbox sandbox = new Sandbox();

        public Sandbox getSandbox() { return sandbox; }
        public void setSandbox(Sandbox sandbox) { this.sandbox = sandbox; }
    }

    public static class Sandbox {
        private boolean enabled = true;
        private long simulateDelayMs = 500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSimulateDelayMs() { return simulateDelayMs; }
        public void setSimulateDelayMs(long simulateDelayMs) { this.simulateDelayMs = simulateDelayMs; }
    }
}
