package zw.gov.mohcc.impilo.vito.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * VITO service configuration properties.
 * Bound from application.yml under the 'vito' prefix.
 */
@Component
@ConfigurationProperties(prefix = "vito")
public class VitoProperties {

    private Argon2 argon2 = new Argon2();
    private Hmac hmac = new Hmac();
    private Did did = new Did();
    private Card card = new Card();
    private Matching matching = new Matching();
    private String registryMode = "STANDALONE";
    private Opencr opencr = new Opencr();

    // --- Nested classes ---

    public static class Argon2 {
        private int memoryCostKb = 65536; // 64 MiB
        private int iterations = 3;
        private int parallelism = 4;

        public int getMemoryCostKb() { return memoryCostKb; }
        public void setMemoryCostKb(int memoryCostKb) { this.memoryCostKb = memoryCostKb; }
        public int getIterations() { return iterations; }
        public void setIterations(int iterations) { this.iterations = iterations; }
        public int getParallelism() { return parallelism; }
        public void setParallelism(int parallelism) { this.parallelism = parallelism; }
    }

    public static class Hmac {
        private String pepper = "changeme-not-for-production";

        public String getPepper() { return pepper; }
        public void setPepper(String pepper) { this.pepper = pepper; }
    }

    public static class Did {
        private String method = "impilo";

        public String getMethod() { return method; }
        public void setMethod(String method) { this.method = method; }
    }

    public static class Card {
        private int expiryYears = 5;

        public int getExpiryYears() { return expiryYears; }
        public void setExpiryYears(int expiryYears) { this.expiryYears = expiryYears; }
    }

    public static class Matching {
        private double thresholdAutoLink = 0.95;
        private double thresholdManualReview = 0.70;

        public double getThresholdAutoLink() { return thresholdAutoLink; }
        public void setThresholdAutoLink(double thresholdAutoLink) { this.thresholdAutoLink = thresholdAutoLink; }
        public double getThresholdManualReview() { return thresholdManualReview; }
        public void setThresholdManualReview(double thresholdManualReview) { this.thresholdManualReview = thresholdManualReview; }
    }

    public static class Opencr {
        private String baseUrl = "http://localhost:3000/fhir";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    }

    // --- Getters/Setters ---

    public Argon2 getArgon2() { return argon2; }
    public void setArgon2(Argon2 argon2) { this.argon2 = argon2; }
    public Hmac getHmac() { return hmac; }
    public void setHmac(Hmac hmac) { this.hmac = hmac; }
    public Did getDid() { return did; }
    public void setDid(Did did) { this.did = did; }
    public Card getCard() { return card; }
    public void setCard(Card card) { this.card = card; }
    public Matching getMatching() { return matching; }
    public void setMatching(Matching matching) { this.matching = matching; }
    public String getRegistryMode() { return registryMode; }
    public void setRegistryMode(String registryMode) { this.registryMode = registryMode; }
    public Opencr getOpencr() { return opencr; }
    public void setOpencr(Opencr opencr) { this.opencr = opencr; }
}
