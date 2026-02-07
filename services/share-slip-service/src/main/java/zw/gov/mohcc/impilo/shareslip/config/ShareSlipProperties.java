package zw.gov.mohcc.impilo.shareslip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Share Slip service configuration properties.
 * Bound from application.yml under the 'share-slip' prefix.
 */
@Component
@ConfigurationProperties(prefix = "share-slip")
public class ShareSlipProperties {

    private Otp otp = new Otp();
    private Link link = new Link();
    private Hmac hmac = new Hmac();
    private Landela landela = new Landela();

    // --- Nested classes ---

    public static class Otp {
        private int length = 6;
        private int expiryMinutes = 15;
        private int maxAttempts = 3;

        public int getLength() { return length; }
        public void setLength(int length) { this.length = length; }
        public int getExpiryMinutes() { return expiryMinutes; }
        public void setExpiryMinutes(int expiryMinutes) { this.expiryMinutes = expiryMinutes; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }

    public static class Link {
        private int defaultExpiryHours = 24;
        private int maxExpiryHours = 168; // 7 days

        public int getDefaultExpiryHours() { return defaultExpiryHours; }
        public void setDefaultExpiryHours(int defaultExpiryHours) { this.defaultExpiryHours = defaultExpiryHours; }
        public int getMaxExpiryHours() { return maxExpiryHours; }
        public void setMaxExpiryHours(int maxExpiryHours) { this.maxExpiryHours = maxExpiryHours; }
    }

    public static class Hmac {
        private String pepper = "changeme-not-for-production";

        public String getPepper() { return pepper; }
        public void setPepper(String pepper) { this.pepper = pepper; }
    }

    public static class Landela {
        private String baseUrl = "http://localhost:8092";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    // --- Getters/Setters ---

    public Otp getOtp() { return otp; }
    public void setOtp(Otp otp) { this.otp = otp; }
    public Link getLink() { return link; }
    public void setLink(Link link) { this.link = link; }
    public Hmac getHmac() { return hmac; }
    public void setHmac(Hmac hmac) { this.hmac = hmac; }
    public Landela getLandela() { return landela; }
    public void setLandela(Landela landela) { this.landela = landela; }
}
