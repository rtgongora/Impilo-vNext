package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.MsikaAppsClient;
import zw.gov.mohcc.impilo.experience.service.HealthOsLauncherCatalogService;

import java.time.Instant;
import java.util.*;

/**
 * Health OS launcher contract surface ({@code /internal/v1/launcher/apps/**}).
 *
 * <p>Merges registry-aligned platform catalogue with marketplace launcher tiles from msika-apps-service.</p>
 */
@RestController
@RequestMapping("/internal/v1/launcher/apps")
public class HealthOsLauncherController {

    private final MsikaAppsClient msikaApps;
    private final ObjectMapper objectMapper;
    private final HealthOsLauncherCatalogService catalogService;

    public HealthOsLauncherController(
            MsikaAppsClient msikaApps,
            ObjectMapper objectMapper,
            HealthOsLauncherCatalogService catalogService) {
        this.msikaApps = msikaApps;
        this.objectMapper = objectMapper;
        this.catalogService = catalogService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> listApps(
            @RequestParam(required = false) Boolean includeUnavailable,
            @RequestParam(required = false) Boolean includeDeprecated,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String roles) {
        List<Map<String, Object>> apps = new ArrayList<>(catalogService.platformApps());
        apps.addAll(parseMarketplaceLauncher(facilityId, roles, includeUnavailable, includeDeprecated));

        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("actorId", "");
        actor.put("actorType", "");
        actor.put("tenantId", "");
        actor.put("facilityId", facilityId != null ? facilityId : "");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actor", actor);
        body.put("apps", apps);
        body.put("catalogCount", catalogService.catalogSize());
        body.put("marketplaceCount", apps.size() - catalogService.catalogSize());
        body.put("generatedAt", Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{appCode}/state")
    public ResponseEntity<Map<String, Object>> appState(
            @PathVariable String appCode,
            @RequestParam(required = false) Boolean includeUnavailable,
            @RequestParam(required = false) Boolean includeDeprecated,
            @RequestParam(required = false) String facilityId,
            @RequestParam(required = false) String roles) {
        Map<String, Object> listBody = listApps(includeUnavailable, includeDeprecated, facilityId, roles).getBody();
        if (listBody == null || !(listBody.get("apps") instanceof List<?> list)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown app: " + appCode);
        }
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> app && appCode.equals(String.valueOf(app.get("appCode")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) app;
                return ResponseEntity.ok(typed);
            }
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown app: " + appCode);
    }

    @PostMapping("/{appCode}/request-access")
    public ResponseEntity<Map<String, Object>> requestAccess(
            @PathVariable String appCode,
            @RequestBody Map<String, Object> body) {
        String itemId = resolveMarketplaceItemId(appCode);
        if (itemId == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No marketplace item for app: " + appCode);
        }

        Map<String, Object> activationBody = new LinkedHashMap<>();
        activationBody.put("itemId", itemId);
        activationBody.put("purposeOfUse", body.getOrDefault("purposeOfUse", "TREATMENT"));
        activationBody.put("justification", body.get("justification"));

        try {
            String raw = msikaApps.requestActivation(objectMapper.writeValueAsString(activationBody)).getBody();
            Map<String, Object> created = raw == null || raw.isBlank()
                    ? Map.of("activationRequestId", UUID.randomUUID().toString(), "status", "PENDING")
                    : objectMapper.readValue(raw, new TypeReference<>() {});
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("activationRequestId", created.getOrDefault("id", created.get("activationRequestId")));
            response.put("status", created.getOrDefault("status", "PENDING"));
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Activation request failed: " + e.getMessage());
        }
    }

    private List<Map<String, Object>> parseMarketplaceLauncher(
            String facilityId,
            String roles,
            Boolean includeUnavailable,
            Boolean includeDeprecated) {
        try {
            String raw = msikaApps.launcher(facilityId, roles).getBody();
            if (raw == null || raw.isBlank()) {
                return List.of();
            }
            List<Map<String, Object>> tiles = objectMapper.readValue(raw, new TypeReference<>() {});
            List<Map<String, Object>> mapped = new ArrayList<>();
            for (Map<String, Object> tile : tiles) {
                String state = String.valueOf(tile.getOrDefault("state", "AVAILABLE"));
                if (!Boolean.TRUE.equals(includeUnavailable) && "NOT_AVAILABLE_AT_FACILITY".equals(state)) {
                    continue;
                }
                if (!Boolean.TRUE.equals(includeDeprecated) && "DEPRECATED".equals(state)) {
                    continue;
                }
                mapped.add(mapMarketplaceTile(tile));
            }
            return mapped;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String resolveMarketplaceItemId(String appCode) {
        try {
            String raw = msikaApps.catalogue(new org.springframework.util.LinkedMultiValueMap<>()).getBody();
            if (raw == null || raw.isBlank()) {
                return null;
            }
            List<Map<String, Object>> items = objectMapper.readValue(raw, new TypeReference<>() {});
            return items.stream()
                    .filter(item -> appCode.equals(String.valueOf(item.get("itemCode"))))
                    .map(item -> String.valueOf(item.get("id")))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private static Map<String, Object> mapMarketplaceTile(Map<String, Object> tile) {
        Map<String, Object> app = new LinkedHashMap<>();
        String itemCode = String.valueOf(tile.getOrDefault("itemCode", tile.get("id")));
        app.put("id", String.valueOf(tile.getOrDefault("id", itemCode)));
        app.put("appCode", itemCode);
        app.put("name", tile.getOrDefault("name", itemCode));
        app.put("description", tile.getOrDefault("description", ""));
        app.put("icon", tile.get("iconRef"));
        app.put("href", tile.getOrDefault("launchUrl", "/launcher/launch/" + itemCode));
        app.put("category", tile.getOrDefault("category", "marketplace"));
        app.put("state", tile.getOrDefault("state", "AVAILABLE"));
        app.put("marketplaceItemId", tile.get("id"));
        app.put("installationId", tile.get("installationId"));
        app.put("systemAppFlag", false);
        app.put("weight", 100);
        app.put("requiredRole", null);
        app.put("pinned", false);
        app.put("recentlyUsed", false);
        app.put("stateExplanation", tile.get("reason"));
        app.put("serviceSlug", "msika-apps");
        app.put("plane", "enterprise");
        app.put("route", app.get("href"));
        app.put("apiBacked", true);
        app.put("fallbackState", false);
        app.put("readiness", "ready");
        app.put("requiredContext", List.of("facilityId"));
        return app;
    }
}
