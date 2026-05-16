package zw.gov.mohcc.impilo.pacs.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves configured viewer engine preferences while keeping the launch contract viewer-neutral.
 */
@Component
public class ViewerEnginePolicy {

    private final String defaultEngine;
    private final List<String> supportedEngines;

    public ViewerEnginePolicy(
            @Value("${impilo.imaging.viewer.default-engine:DICOMWEB_STACK}") String defaultEngine,
            @Value("${impilo.imaging.viewer.supported-engines:DICOMWEB_STACK,OHIF,CORNERSTONE}") String supportedCsv) {
        this.defaultEngine = normalize(defaultEngine, "DICOMWEB_STACK");
        this.supportedEngines = parseSupported(supportedCsv, this.defaultEngine);
    }

    public String resolve(String requested) {
        String normalized = normalize(requested, defaultEngine);
        if (supportedEngines.contains(normalized)) {
            return normalized;
        }
        return defaultEngine;
    }

    public String defaultEngine() {
        return defaultEngine;
    }

    public List<String> supportedEngines() {
        return supportedEngines;
    }

    public Map<String, Object> describe() {
        return Map.of(
                "defaultEngine", defaultEngine,
                "supportedEngines", supportedEngines
        );
    }

    private static List<String> parseSupported(String csv, String defaultEngine) {
        Set<String> ordered = new LinkedHashSet<>();
        if (csv != null) {
            for (String token : csv.split(",")) {
                String n = normalize(token, "");
                if (!n.isBlank()) {
                    ordered.add(n);
                }
            }
        }
        if (!ordered.contains(defaultEngine)) {
            ordered.add(defaultEngine);
        }
        return List.copyOf(new ArrayList<>(ordered));
    }

    private static String normalize(String raw, String fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }
}
