package zw.gov.mohcc.impilo.surv.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Composes jurisdiction-pack and workspace context for public-health operations screens.
 * Pack selection is carried by the client (query param / session); trust headers supply workspace scope.
 */
@Service
public class PublicHealthContextService {

    private static final List<Map<String, Object>> JURISDICTION_PACKS = List.of(
            pack("city_health", "City Health Pack", "Urban municipal public health operations"),
            pack("rdc_health", "Rural District Council Health Pack", "Rural public health and community health"),
            pack("provincial", "Provincial Public Health Oversight Pack", "Provincial surveillance and coordination"),
            pack("national", "National Public Health Oversight Pack", "National surveillance and policy"),
            pack("port_health", "Port Health Pack", "Border and port of entry operations"),
            pack("school_health", "School Health Pack", "School-based health services and inspections"));

    public Map<String, Object> buildContext(String workspaceId, String facilityId, String jurisdictionPack) {
        String activePack = jurisdictionPack != null && !jurisdictionPack.isBlank()
                ? jurisdictionPack.trim()
                : "national";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("report_type", "PUBLIC_HEALTH_CONTEXT");
        body.put("active_jurisdiction_pack", activePack);
        body.put("jurisdiction_packs", JURISDICTION_PACKS);
        body.put("workspace_id", workspaceId);
        body.put("facility_id", facilityId);
        body.put("recommended_purpose_of_use", "PUBLIC_HEALTH");
        body.put("visibility_profile", visibilityForPack(activePack));
        return body;
    }

    private static Map<String, Object> pack(String id, String label, String description) {
        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("id", id);
        pack.put("label", label);
        pack.put("description", description);
        return pack;
    }

    private static String visibilityForPack(String packId) {
        return switch (packId) {
            case "national", "provincial" -> "AGGREGATE_NATIONAL";
            case "port_health" -> "PORT_ENTRY";
            case "school_health" -> "INSTITUTIONAL";
            case "city_health", "rdc_health" -> "DISTRICT_OPERATIONAL";
            default -> "DISTRICT_OPERATIONAL";
        };
    }
}
