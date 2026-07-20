package zw.gov.mohcc.impilo.abis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Governed ABIS thresholds (W3d/W3e). Bound from {@code abis.*} config so operators can
 * tune per-environment without code changes:
 *
 * <pre>
 * abis:
 *   dedup:
 *     enabled: true
 *     threshold: 0.85      # 1:N score at/above which enrol opens a duplicate case
 *   quality:
 *     min-score:
 *       FINGERPRINT: 40    # reject enrol below this quality_score proxy (per modality)
 *       FACE: 30
 *       default: 0         # 0 disables gating for unlisted modalities
 * </pre>
 *
 * <p>NFIQ2 (fingerprint) / ISO quality is an external follow-on; gating uses the current
 * {@code quality_score} proxy. Per-tenant overrides are a documented follow-on (global here).</p>
 */
@Component
@ConfigurationProperties(prefix = "abis")
public class AbisThresholdProperties {

    private final Dedup dedup = new Dedup();
    private final Quality quality = new Quality();

    public Dedup getDedup() {
        return dedup;
    }

    public Quality getQuality() {
        return quality;
    }

    /** Minimum acceptable quality for a modality, or 0 (disabled) if unset. */
    public int minQuality(String modalityName) {
        Map<String, Integer> m = quality.getMinScore();
        if (m == null || m.isEmpty()) {
            return 0;
        }
        String key = modalityName == null ? "" : modalityName.toUpperCase(Locale.ROOT);
        Integer v = m.get(key);
        if (v == null) {
            v = m.get(modalityName == null ? "" : modalityName.toLowerCase(Locale.ROOT));
        }
        if (v == null) {
            v = m.getOrDefault("default", m.getOrDefault("DEFAULT", 0));
        }
        return v == null ? 0 : v;
    }

    public static class Dedup {
        /** Whether enrol-time 1:N dedup runs (W3b). Off by default (additive). */
        private boolean enabled = false;
        /** Score at/above which a candidate opens a duplicate-investigation case. */
        private double threshold = 0.85;
        /** Hard cap on gallery templates loaded for the enrol-time 1:N. */
        private int maxGallery = 500;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public double getThreshold() { return threshold; }
        public void setThreshold(double threshold) { this.threshold = threshold; }

        public int getMaxGallery() { return maxGallery; }
        public void setMaxGallery(int maxGallery) { this.maxGallery = maxGallery; }
    }

    public static class Quality {
        /** Per-modality minimum quality_score; key "default" applies to unlisted modalities. */
        private Map<String, Integer> minScore = new LinkedHashMap<>();

        public Map<String, Integer> getMinScore() { return minScore; }
        public void setMinScore(Map<String, Integer> minScore) { this.minScore = minScore; }
    }
}
