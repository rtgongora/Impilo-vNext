package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry-aligned vNext launcher catalogue sourced from {@code launcher-vnext-catalog.json},
 * kept in sync with {@code ui/one-ui-shell/src/lib/shell/app-registry.ts} active entries.
 */
@Service
public class HealthOsLauncherCatalogService {

    private static final Logger log = LoggerFactory.getLogger(HealthOsLauncherCatalogService.class);
    private static final String CATALOG_RESOURCE = "launcher-vnext-catalog.json";

    private final List<Map<String, Object>> catalogEntries;

    public HealthOsLauncherCatalogService(ObjectMapper objectMapper) {
        this.catalogEntries = loadCatalog(objectMapper);
    }

    public List<Map<String, Object>> platformApps() {
        return catalogEntries.stream().map(this::toLauncherTile).toList();
    }

    public int catalogSize() {
        return catalogEntries.size();
    }

    private static List<Map<String, Object>> loadCatalog(ObjectMapper objectMapper) {
        try (InputStream in = new ClassPathResource(CATALOG_RESOURCE).getInputStream()) {
            List<Map<String, Object>> raw = objectMapper.readValue(in, new TypeReference<>() {});
            return List.copyOf(raw);
        } catch (Exception e) {
            log.error("Failed to load launcher catalogue from {}: {}", CATALOG_RESOURCE, e.getMessage());
            return List.of();
        }
    }

    private Map<String, Object> toLauncherTile(Map<String, Object> entry) {
        Map<String, Object> app = new LinkedHashMap<>();
        String appCode = stringVal(entry.get("appCode"));
        app.put("id", stringVal(entry.get("id")));
        app.put("appCode", appCode);
        app.put("name", entry.get("name"));
        app.put("description", entry.getOrDefault("description", ""));
        app.put("icon", null);
        app.put("href", entry.get("href"));
        app.put("category", entry.getOrDefault("category", "system"));
        app.put("state", "AVAILABLE");
        app.put("marketplaceItemId", null);
        app.put("installationId", null);
        app.put("systemAppFlag", Boolean.TRUE.equals(entry.get("systemAppFlag")));
        app.put("weight", entry.getOrDefault("weight", 100));
        app.put("requiredRole", entry.get("requiredRole"));
        app.put("pinned", false);
        app.put("recentlyUsed", false);
        app.put("stateExplanation", null);
        app.put("serviceSlug", entry.get("serviceSlug"));
        app.put("plane", entry.getOrDefault("plane", "experience"));
        app.put("route", entry.get("href"));
        app.put("apiBacked", Boolean.TRUE.equals(entry.get("apiBacked")));
        app.put("fallbackState", false);
        String readiness = stringVal(entry.get("readiness"));
        app.put("readiness", readiness.isBlank() ? "ready" : readiness);
        app.put("requiredContext", buildRequiredContext(entry));
        return app;
    }

    private static List<String> buildRequiredContext(Map<String, Object> entry) {
        List<String> ctx = new ArrayList<>();
        if (entry.get("requiredRole") != null && !stringVal(entry.get("requiredRole")).isBlank()) {
            ctx.add("role:" + entry.get("requiredRole"));
        }
        String slug = stringVal(entry.get("serviceSlug"));
        if ("tuso".equals(slug) || "facility_operations".equals(stringVal(entry.get("appCode")))) {
            ctx.add("facilityId");
        }
        if ("varapi".equals(slug)) {
            ctx.add("providerContext");
        }
        if ("vito".equals(slug)) {
            ctx.add("actorId");
        }
        return ctx;
    }

    private static String stringVal(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
