package zw.gov.mohcc.impilo.telemonitoring.core;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * metric_code → FHIR coding map for the monitoring-band Observation writer (OF-B25,
 * §14.5 step 12). Common vital-sign metrics carry their LOINC coding; anything the map
 * does not know is coded HONESTLY under the local telemonitoring code system rather
 * than guessing a LOINC code — the metric code itself becomes the local code, so
 * nothing is dropped and nothing is mislabelled as standard terminology.
 *
 * <p>Config overrides/extensions: {@code telemonitoring.shr.metric-codes.<metric>} =
 * {@code "system|code|display"} (pipe-separated) merges over the built-in defaults.</p>
 */
@Component
@ConfigurationProperties(prefix = "telemonitoring.shr")
public class MetricCodeRegistry {

    public static final String LOINC = "http://loinc.org";
    /** Honest local system for metrics without a governed LOINC mapping. */
    public static final String LOCAL_SYSTEM = "https://impilo.mohcc.gov.zw/fhir/CodeSystem/telemonitoring-metric";

    public record MetricCoding(String system, String code, String display, boolean local) {}

    private static final Map<String, MetricCoding> DEFAULTS = Map.ofEntries(
            Map.entry("systolic_bp", loinc("8480-6", "Systolic blood pressure")),
            Map.entry("diastolic_bp", loinc("8462-4", "Diastolic blood pressure")),
            Map.entry("heart_rate", loinc("8867-4", "Heart rate")),
            Map.entry("pulse", loinc("8867-4", "Heart rate")),
            Map.entry("spo2", loinc("59408-5", "Oxygen saturation in Arterial blood by Pulse oximetry")),
            Map.entry("oxygen_saturation", loinc("59408-5", "Oxygen saturation in Arterial blood by Pulse oximetry")),
            Map.entry("blood_glucose", loinc("2339-0", "Glucose [Mass/volume] in Blood")),
            Map.entry("glucose", loinc("2339-0", "Glucose [Mass/volume] in Blood")),
            Map.entry("body_temperature", loinc("8310-5", "Body temperature")),
            Map.entry("temperature", loinc("8310-5", "Body temperature")),
            Map.entry("body_weight", loinc("29463-7", "Body weight")),
            Map.entry("weight", loinc("29463-7", "Body weight")),
            Map.entry("respiratory_rate", loinc("9279-1", "Respiratory rate")));

    /** {@code metric -> "system|code|display"} from configuration; merged over defaults. */
    private Map<String, String> metricCodes = new HashMap<>();

    public Map<String, String> getMetricCodes() {
        return metricCodes;
    }

    public void setMetricCodes(Map<String, String> metricCodes) {
        this.metricCodes = metricCodes != null ? metricCodes : new HashMap<>();
    }

    /** Never null: unknown metrics resolve to the honest local code system. */
    public MetricCoding resolve(String metricCode) {
        String key = normalise(metricCode);
        String configured = metricCodes.get(key);
        if (configured != null) {
            String[] parts = configured.split("\\|", 3);
            if (parts.length >= 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                return new MetricCoding(parts[0].trim(), parts[1].trim(),
                        parts.length == 3 && !parts[2].isBlank() ? parts[2].trim() : key,
                        !LOINC.equals(parts[0].trim()));
            }
        }
        MetricCoding known = DEFAULTS.get(key);
        if (known != null) {
            return known;
        }
        return new MetricCoding(LOCAL_SYSTEM, key, metricCode, true);
    }

    private static String normalise(String metricCode) {
        return metricCode == null ? "unknown" : metricCode.trim().toLowerCase(Locale.ROOT);
    }

    private static MetricCoding loinc(String code, String display) {
        return new MetricCoding(LOINC, code, display, false);
    }
}
