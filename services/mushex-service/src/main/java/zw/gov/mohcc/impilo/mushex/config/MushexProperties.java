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
    private RailSelection railSelection = new RailSelection();

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
    public RailSelection getRailSelection() { return railSelection; }
    public void setRailSelection(RailSelection railSelection) { this.railSelection = railSelection; }

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
        private RealRail mobileMoney = new RealRail();
        private RealRail bankTransfer = new RealRail();
        private RealRail cardGateway = new RealRail();

        public Sandbox getSandbox() { return sandbox; }
        public void setSandbox(Sandbox sandbox) { this.sandbox = sandbox; }
        public RealRail getMobileMoney() { return mobileMoney; }
        public void setMobileMoney(RealRail mobileMoney) { this.mobileMoney = mobileMoney; }
        public RealRail getBankTransfer() { return bankTransfer; }
        public void setBankTransfer(RealRail bankTransfer) { this.bankTransfer = bankTransfer; }
        public RealRail getCardGateway() { return cardGateway; }
        public void setCardGateway(RealRail cardGateway) { this.cardGateway = cardGateway; }
    }

    /**
     * Conservative per-rail readiness flags for real-money adapters
     * (MOBILE_MONEY, BANK_TRANSFER, CARD_GATEWAY). These properties never carry
     * actual credentials; they are operator-set booleans that signal intent.
     *
     * <p>Defaults are {@code enabled=false} and {@code credentialsConfigured=false}
     * — production deployments must explicitly opt in per environment.
     */
    public static class RealRail {
        private boolean enabled = false;
        private boolean credentialsConfigured = false;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isCredentialsConfigured() { return credentialsConfigured; }
        public void setCredentialsConfigured(boolean credentialsConfigured) {
            this.credentialsConfigured = credentialsConfigured;
        }
    }

    /**
     * Rail-selection policy configuration. See {@code docs/design/g4-rail-selection-policy.md}.
     *
     * <p>Defaults are deliberately conservative for production:
     * <ul>
     *   <li>{@code enabled=true} — rail-selection metadata is computed and recorded on every intent.</li>
     *   <li>{@code defaultRail=SANDBOX} — when callers do not preference a rail, never accidentally route to a real external gateway.</li>
     *   <li>{@code allowDirectGateway=false} — Mode A (direct gateway) is opt-in per environment.</li>
     *   <li>{@code allowSandboxFallback=true} — operators may disable this to force hard rejection of unavailable preferred rails.</li>
     * </ul>
     */
    public static class RailSelection {
        private boolean enabled = true;
        private zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType defaultRail =
                zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType.SANDBOX;
        private boolean allowDirectGateway = false;
        private boolean allowSandboxFallback = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType getDefaultRail() { return defaultRail; }
        public void setDefaultRail(zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType defaultRail) {
            this.defaultRail = defaultRail;
        }
        public boolean isAllowDirectGateway() { return allowDirectGateway; }
        public void setAllowDirectGateway(boolean allowDirectGateway) { this.allowDirectGateway = allowDirectGateway; }
        public boolean isAllowSandboxFallback() { return allowSandboxFallback; }
        public void setAllowSandboxFallback(boolean allowSandboxFallback) { this.allowSandboxFallback = allowSandboxFallback; }
    }

    public static class Sandbox {
        private boolean enabled = true;
        private long simulateDelayMs = 500;
        /**
         * When true and a COSTA handoff sends {@code impilo_simulation} in payment-intent metadata,
         * MusheX skips external credential and payer-contract checks so local demos can run without adapters.
         */
        private boolean bypassCredentialCheckForSimulation = true;
        /**
         * When true with {@code bypassCredentialCheckForSimulation}, applies {@code simulation_outcome}
         * from metadata immediately after intent creation (demo state machine).
         */
        private boolean applySimulationOutcomeOnCreate = true;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public long getSimulateDelayMs() { return simulateDelayMs; }
        public void setSimulateDelayMs(long simulateDelayMs) { this.simulateDelayMs = simulateDelayMs; }
        public boolean isBypassCredentialCheckForSimulation() { return bypassCredentialCheckForSimulation; }
        public void setBypassCredentialCheckForSimulation(boolean bypassCredentialCheckForSimulation) {
            this.bypassCredentialCheckForSimulation = bypassCredentialCheckForSimulation;
        }
        public boolean isApplySimulationOutcomeOnCreate() { return applySimulationOutcomeOnCreate; }
        public void setApplySimulationOutcomeOnCreate(boolean applySimulationOutcomeOnCreate) {
            this.applySimulationOutcomeOnCreate = applySimulationOutcomeOnCreate;
        }
    }
}
