package zw.gov.mohcc.impilo.msikaflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "msika-flow")
public class MsikaFlowProperties {

    private Outbox outbox = new Outbox();
    private Pickup pickup = new Pickup();
    private Routing routing = new Routing();
    private Pricing pricing = new Pricing();
    private Payments payments = new Payments();
    private Fulfillment fulfillment = new Fulfillment();

    public Outbox getOutbox() { return outbox; }
    public void setOutbox(Outbox outbox) { this.outbox = outbox; }
    public Pickup getPickup() { return pickup; }
    public void setPickup(Pickup pickup) { this.pickup = pickup; }
    public Routing getRouting() { return routing; }
    public void setRouting(Routing routing) { this.routing = routing; }
    public Pricing getPricing() { return pricing; }
    public void setPricing(Pricing pricing) { this.pricing = pricing; }
    public Payments getPayments() { return payments; }
    public void setPayments(Payments payments) { this.payments = payments; }
    public Fulfillment getFulfillment() { return fulfillment; }
    public void setFulfillment(Fulfillment fulfillment) { this.fulfillment = fulfillment; }

    public static class Outbox {
        private long pollIntervalMs = 2000;
        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }
    }

    public static class Pickup {
        private int otpLength = 6;
        private long expiryHours = 48;
        private int maxClaimAttemptsPerHour = 5;
        public int getOtpLength() { return otpLength; }
        public void setOtpLength(int otpLength) { this.otpLength = otpLength; }
        public long getExpiryHours() { return expiryHours; }
        public void setExpiryHours(long expiryHours) { this.expiryHours = expiryHours; }
        public int getMaxClaimAttemptsPerHour() { return maxClaimAttemptsPerHour; }
        public void setMaxClaimAttemptsPerHour(int maxClaimAttemptsPerHour) { this.maxClaimAttemptsPerHour = maxClaimAttemptsPerHour; }
    }

    public static class Routing {
        private String defaultMode = "INTERNAL";
        public String getDefaultMode() { return defaultMode; }
        public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }
    }

    public static class Pricing {
        private String platformFeeRate = "0.015";
        private String platformFeeCap = "50.00";
        public String getPlatformFeeRate() { return platformFeeRate; }
        public void setPlatformFeeRate(String platformFeeRate) { this.platformFeeRate = platformFeeRate; }
        public String getPlatformFeeCap() { return platformFeeCap; }
        public void setPlatformFeeCap(String platformFeeCap) { this.platformFeeCap = platformFeeCap; }
    }

    /**
     * Payment-rail behaviour. {@code simulationMetadata} tags MusheX intents with
     * {@code "simulation": true} so preview/rig environments pass MusheX's
     * provider-credential gate ({@code PROVIDER_CREDENTIAL_INVALID}) without real
     * vendor-verifiable provider ids. MUST stay false in production.
     */
    public static class Payments {
        private boolean simulationMetadata = false;
        public boolean isSimulationMetadata() { return simulationMetadata; }
        public void setSimulationMetadata(boolean simulationMetadata) { this.simulationMetadata = simulationMetadata; }
    }

    /**
     * Fulfilment seams. {@code nhumeEnabled} routes delivery-mode orders to Nhume
     * (dispatch SoR); when disabled — or when the Nhume call fails — the manual
     * out-for-delivery / mark-delivered endpoints are the documented recovery path.
     */
    public static class Fulfillment {
        private boolean nhumeEnabled = true;
        public boolean isNhumeEnabled() { return nhumeEnabled; }
        public void setNhumeEnabled(boolean nhumeEnabled) { this.nhumeEnabled = nhumeEnabled; }
    }
}
