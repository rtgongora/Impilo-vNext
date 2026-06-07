package zw.gov.mohcc.impilo.madi.core;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MadiZiboPinService {

    private static final String DEFAULT_JURISDICTION = "ZW";

    @SuppressWarnings("unchecked")
    public Map<String, Object> componentPins(String jurisdiction) {
        String resolved = jurisdiction == null || jurisdiction.isBlank() ? DEFAULT_JURISDICTION : jurisdiction.trim().toUpperCase();
        Map<String, Object> root = loadPins();
        Map<String, Object> jurisdictions = asMap(root.get("jurisdictions"));
        Map<String, Object> jurisdictionNode = asMap(jurisdictions.get(resolved));
        if (jurisdictionNode.isEmpty() && !DEFAULT_JURISDICTION.equals(resolved)) {
            jurisdictionNode = asMap(jurisdictions.get(DEFAULT_JURISDICTION));
            resolved = DEFAULT_JURISDICTION;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jurisdiction", resolved);
        response.put("releaseTrain", jurisdictionNode.getOrDefault("release_train", resolved + "-MADI"));
        response.put("components", jurisdictionNode.getOrDefault("components", Map.of()));
        response.put("defaultJurisdiction", root.getOrDefault("default_jurisdiction", DEFAULT_JURISDICTION));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadPins() {
        try (InputStream in = new ClassPathResource("madi/zibo-jurisdiction-pins.yaml").getInputStream()) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ignored) {
            // fall through to repo-relative config path
        }
        try (InputStream in = java.nio.file.Files.newInputStream(
                java.nio.file.Path.of("config/madi/zibo-jurisdiction-pins.yaml"))) {
            Object loaded = new Yaml().load(in);
            if (loaded instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception ignored) {
            // use empty fallback
        }
        return Map.of(
                "default_jurisdiction", DEFAULT_JURISDICTION,
                "jurisdictions", Map.of());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }
}
