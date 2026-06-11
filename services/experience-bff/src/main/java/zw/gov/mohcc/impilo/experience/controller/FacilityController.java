package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.config.BffFacilitiesProperties;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Facility registry proxy — delegates to TUSO when available, falls back to
 * seeded Zimbabwe health facilities so the UI always has data to display.
 */
@RestController
@RequestMapping("/internal/v1/facilities")
public class FacilityController {

    private static final Logger log = LoggerFactory.getLogger(FacilityController.class);

    private final TusoServiceClient tusoClient;
    private final BffFacilitiesProperties facilitiesProperties;

    public FacilityController(TusoServiceClient tusoClient, BffFacilitiesProperties facilitiesProperties) {
        this.tusoClient = tusoClient;
        this.facilitiesProperties = facilitiesProperties;
    }

    private static final List<Map<String, Object>> SEEDED_FACILITIES = buildSeededFacilities();

    @GetMapping
    public ResponseEntity<Map<String, Object>> listFacilities(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "facility_type") String facilityType,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String search) {

        if (facilitiesProperties.getMode() == BffFacilitiesProperties.Mode.live) {
            try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", search != null ? search : "");
            body.put("facilityType", facilityType);
            body.put("status", status);
            body.put("province", province);
            body.put("district", null);
            body.put("level", null);
            body.put("page", page);
            body.put("size", Math.min(Math.max(size, 1), 200));
            JsonNode paged = tusoClient.searchFacilities(body);
            List<Map<String, Object>> mapped = new ArrayList<>();
            if (paged != null && paged.has("items") && paged.get("items").isArray()) {
                for (JsonNode row : paged.get("items")) {
                    mapped.add(mapTusoSummaryToFacilityResource(row));
                }
            }
            if (mapped.isEmpty()) {
                log.warn("TUSO facility search returned no rows — using seeded facilities");
            } else {
                long total = paged != null && paged.has("totalElements") ? paged.get("totalElements").asLong() : mapped.size();
                return ResponseEntity.ok(Map.of(
                        "data", mapped,
                        "meta", Map.of(
                                "request_id", requestId,
                                "correlation_id", correlationId,
                                "page", Map.of(
                                        "number", page,
                                        "size", size,
                                        "total_elements", total,
                                        "total_pages", size > 0 ? (int) Math.ceil((double) total / size) : 0
                                )
                        )
                ));
            }
            } catch (Exception e) {
                log.warn("TUSO facility search failed, using seed: {}", e.getMessage());
            }
        }

        List<Map<String, Object>> filtered = SEEDED_FACILITIES;

        if (facilityType != null && !facilityType.isBlank()) {
            String ft = facilityType.toLowerCase();
            filtered = filtered.stream()
                    .filter(f -> ((String) ((Map<?, ?>) f.get("attributes")).get("facilityType")).toLowerCase().contains(ft))
                    .collect(Collectors.toList());
        }
        if (province != null && !province.isBlank()) {
            String prov = province.toLowerCase();
            filtered = filtered.stream()
                    .filter(f -> ((String) ((Map<?, ?>) f.get("attributes")).get("province")).toLowerCase().contains(prov))
                    .collect(Collectors.toList());
        }
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            filtered = filtered.stream()
                    .filter(f -> ((String) ((Map<?, ?>) f.get("attributes")).get("name")).toLowerCase().contains(q)
                            || ((String) ((Map<?, ?>) f.get("attributes")).get("district")).toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }

        int start = page * size;
        int end = Math.min(start + size, filtered.size());
        List<Map<String, Object>> paged = start < filtered.size() ? filtered.subList(start, end) : List.of();

        return ResponseEntity.ok(Map.of(
                "data", paged,
                "meta", Map.of(
                        "request_id", requestId,
                        "correlation_id", correlationId,
                        "page", Map.of(
                                "number", page,
                                "size", size,
                                "total_elements", filtered.size(),
                                "total_pages", (int) Math.ceil((double) filtered.size() / size)
                        )
                )
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getFacility(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        if (facilitiesProperties.getMode() == BffFacilitiesProperties.Mode.live) {
            try {
                long facilityId = Long.parseLong(id);
                JsonNode detail = tusoClient.getFacility(facilityId);
                if (detail != null) {
                    Map<String, Object> f = mapTusoDetailToFacilityResource(detail);
                    return ResponseEntity.ok(Map.of(
                            "data", f,
                            "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
                }
            } catch (NumberFormatException ignored) {
            } catch (Exception e) {
                log.debug("TUSO facility get miss for id={}: {}", id, e.getMessage());
            }
        }

        return SEEDED_FACILITIES.stream()
                .filter(f -> f.get("id").equals(id))
                .findFirst()
                .map(f -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("data", f);
                    body.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                    return ResponseEntity.ok(body);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private static Map<String, Object> mapTusoSummaryToFacilityResource(JsonNode n) {
        String idStr = n.has("id") ? String.valueOf(n.get("id").asLong()) : UUID.randomUUID().toString();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", text(n, "name"));
        attrs.put("code", text(n, "code"));
        attrs.put("facilityType", text(n, "type"));
        attrs.put("district", text(n, "district"));
        attrs.put("province", text(n, "province"));
        attrs.put("region", text(n, "province"));
        attrs.put("status", text(n, "status"));
        attrs.put("capabilities", List.of());
        return Map.of("id", idStr, "type", "facility", "attributes", attrs);
    }

    private static Map<String, Object> mapTusoDetailToFacilityResource(JsonNode n) {
        String idStr = n.has("id") ? String.valueOf(n.get("id").asLong()) : "0";
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", text(n, "name"));
        attrs.put("code", text(n, "facilityCode"));
        attrs.put("facilityType", text(n, "facilityType"));
        attrs.put("district", text(n, "district"));
        attrs.put("province", text(n, "province"));
        attrs.put("region", text(n, "province"));
        attrs.put("status", text(n, "status"));
        if (n.has("latitude") && !n.get("latitude").isNull()) {
            attrs.put("latitude", n.get("latitude").asDouble());
        }
        if (n.has("longitude") && !n.get("longitude").isNull()) {
            attrs.put("longitude", n.get("longitude").asDouble());
        }
        attrs.put("capabilities", List.of());
        JsonNode om = n.get("operatingModel");
        if (om != null && !om.isNull()) {
            attrs.put("facilityTier", text(om, "facilityTier"));
            attrs.put("deploymentMode", text(om, "deploymentMode"));
            attrs.put("continuityClass", text(om, "continuityClass"));
            attrs.put("workflowArchetype", text(om, "workflowArchetype"));
        }
        return Map.of("id", idStr, "type", "facility", "attributes", attrs);
    }

    private static String text(JsonNode n, String field) {
        if (n == null || !n.has(field) || n.get(field).isNull()) {
            return "";
        }
        return n.get(field).asText("");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> buildSeededFacilities() {
        List<Map<String, Object>> facilities = new ArrayList<>();

        // ── Dev / integration test facility ────────────────────────────
        facilities.add(facility("a1b2c3d4-00ff-4000-8000-000000000099", "ZW-TEST-001", "Test Facility",
                "General Hospital", "Harare", "Harare Metropolitan", "Harare",
                -17.8252, 31.0335, 50,
                List.of("OUTPATIENT", "EMERGENCY", "TELEMEDICINE", "LABORATORY", "PHARMACY")));

        // ── Central Hospitals ────────────────────────────────────────
        facilities.add(facility("fac-hch", "ZW-HCH-001", "Harare Central Hospital",
                "Central Hospital", "Harare", "Harare Metropolitan", "Harare",
                -17.8292, 31.0522, 1200,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "ICU", "MATERNITY", "LABORATORY", "PHARMACY", "RADIOLOGY", "BLOOD_BANK")));
        facilities.add(facility("fac-pgh", "ZW-PGH-001", "Parirenyatwa Group of Hospitals",
                "Central Hospital", "Harare", "Harare Metropolitan", "Harare",
                -17.7937, 31.0467, 1800,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "ICU", "MATERNITY", "LABORATORY", "PHARMACY", "RADIOLOGY", "TEACHING")));
        facilities.add(facility("fac-uch", "ZW-UCH-001", "United Bulawayo Hospitals",
                "Central Hospital", "Bulawayo", "Bulawayo Metropolitan", "Bulawayo",
                -20.1500, 28.5833, 900,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "ICU", "MATERNITY", "LABORATORY", "PHARMACY", "RADIOLOGY")));
        facilities.add(facility("fac-mpc", "ZW-MPC-001", "Mpilo Central Hospital",
                "Central Hospital", "Bulawayo", "Bulawayo Metropolitan", "Bulawayo",
                -20.1700, 28.5600, 1000,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "ICU", "LABORATORY", "PHARMACY", "RADIOLOGY")));
        facilities.add(facility("fac-chi", "ZW-CHI-001", "Chitungwiza Central Hospital",
                "Central Hospital", "Chitungwiza", "Harare Metropolitan", "Harare",
                -18.0127, 31.0755, 600,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "ICU", "MATERNITY", "LABORATORY", "PHARMACY")));

        // ── Provincial Hospitals ─────────────────────────────────────
        facilities.add(facility("fac-mnh", "ZW-MNH-001", "Masvingo Provincial Hospital",
                "Provincial Hospital", "Masvingo", "Masvingo", "Masvingo",
                -20.0644, 30.8327, 400,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-mty", "ZW-MTY-001", "Mutare Provincial Hospital",
                "Provincial Hospital", "Mutare", "Manicaland", "Manicaland",
                -18.9707, 32.6709, 350,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-gwu", "ZW-GWU-001", "Gweru Provincial Hospital",
                "Provincial Hospital", "Gweru", "Midlands", "Midlands",
                -19.4500, 29.8167, 300,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "SURGERY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-chn", "ZW-CHN-001", "Chinhoyi Provincial Hospital",
                "Provincial Hospital", "Chinhoyi", "Mashonaland West", "Mashonaland West",
                -17.3622, 30.1955, 250,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-bdt", "ZW-BDT-001", "Bindura Provincial Hospital",
                "Provincial Hospital", "Bindura", "Mashonaland Central", "Mashonaland Central",
                -17.3011, 31.3264, 200,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "MATERNITY", "LABORATORY", "PHARMACY")));

        // ── District Hospitals ───────────────────────────────────────
        facilities.add(facility("fac-kwk", "ZW-KWK-001", "Kwekwe District Hospital",
                "District Hospital", "Kwekwe", "Midlands", "Midlands",
                -18.9281, 29.8144, 150,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-krb", "ZW-KRB-001", "Kariba District Hospital",
                "District Hospital", "Kariba", "Mashonaland West", "Mashonaland West",
                -16.5167, 28.8000, 80,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-zvs", "ZW-ZVS-001", "Zvishavane District Hospital",
                "District Hospital", "Zvishavane", "Midlands", "Midlands",
                -20.3333, 30.0500, 120,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-rsh", "ZW-RSH-001", "Rusape District Hospital",
                "District Hospital", "Rusape", "Manicaland", "Manicaland",
                -18.5333, 32.1333, 100,
                List.of("INPATIENT", "OUTPATIENT", "EMERGENCY", "MATERNITY", "LABORATORY")));

        // ── Rural Health Centres ─────────────────────────────────────
        facilities.add(facility("fac-epc", "ZW-EPC-001", "Epworth Polyclinic",
                "Polyclinic", "Epworth", "Harare Metropolitan", "Harare",
                -17.8900, 31.1500, 40,
                List.of("OUTPATIENT", "MATERNITY", "PHARMACY", "IMMUNISATION")));
        facilities.add(facility("fac-mrh", "ZW-MRH-001", "Mabvuku Rural Health Centre",
                "Rural Health Centre", "Mabvuku", "Harare Metropolitan", "Harare",
                -17.8100, 31.1200, 20,
                List.of("OUTPATIENT", "MATERNITY", "IMMUNISATION")));
        facilities.add(facility("fac-hwd", "ZW-HWD-001", "Hwedza Rural Health Centre",
                "Rural Health Centre", "Hwedza", "Mashonaland East", "Mashonaland East",
                -18.6167, 31.3667, 15,
                List.of("OUTPATIENT", "MATERNITY", "IMMUNISATION")));
        facilities.add(facility("fac-nkl", "ZW-NKL-001", "Nkulumane Clinic",
                "Urban Clinic", "Bulawayo", "Bulawayo Metropolitan", "Bulawayo",
                -20.1900, 28.5300, 25,
                List.of("OUTPATIENT", "PHARMACY", "IMMUNISATION")));

        // ── Mission Hospitals ────────────────────────────────────────
        facilities.add(facility("fac-how", "ZW-HOW-001", "Howard Mission Hospital",
                "Mission Hospital", "Mutoko", "Mashonaland East", "Mashonaland East",
                -17.4000, 32.2333, 100,
                List.of("INPATIENT", "OUTPATIENT", "MATERNITY", "LABORATORY", "PHARMACY")));
        facilities.add(facility("fac-mrg", "ZW-MRG-001", "Morgenster Mission Hospital",
                "Mission Hospital", "Masvingo", "Masvingo", "Masvingo",
                -20.1000, 30.7500, 80,
                List.of("INPATIENT", "OUTPATIENT", "MATERNITY", "LABORATORY")));

        return Collections.unmodifiableList(facilities);
    }

    private static Map<String, Object> facility(String id, String code, String name,
            String type, String district, String province, String region,
            double lat, double lng, int bedCount, List<String> capabilities) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", name);
        attrs.put("code", code);
        attrs.put("facilityType", type);
        attrs.put("district", district);
        attrs.put("province", province);
        attrs.put("region", region);
        attrs.put("status", "ACTIVE");
        attrs.put("latitude", lat);
        attrs.put("longitude", lng);
        attrs.put("bedCount", bedCount);
        attrs.put("capabilities", capabilities);
        attrs.put("operatingHours", "24/7");
        attrs.put("contactPhone", "+263 4 000 0000");
        String facilityTier = inferFacilityTier(type);
        String deploymentMode = inferDeploymentMode(facilityTier);
        String continuityClass = inferContinuityClass(facilityTier);
        String workflowArchetype = inferWorkflowArchetype(facilityTier, capabilities);
        attrs.put("facilityTier", facilityTier);
        attrs.put("deploymentMode", deploymentMode);
        attrs.put("continuityClass", continuityClass);
        attrs.put("workflowArchetype", workflowArchetype);
        attrs.put("operatingModel", Map.of(
                "facilityTier", facilityTier,
                "deploymentMode", deploymentMode,
                "continuityClass", continuityClass,
                "workflowArchetype", workflowArchetype
        ));

        return Map.of("id", id, "type", "facility", "attributes", attrs);
    }

    private static String inferFacilityTier(String type) {
        String normalized = type.toUpperCase(Locale.ROOT);
        if (normalized.contains("CENTRAL")) return "CENTRAL_HOSPITAL";
        if (normalized.contains("PROVINCIAL")) return "PROVINCIAL_OR_TERTIARY_HOSPITAL";
        if (normalized.contains("MISSION")) return "DISTRICT_OR_MISSION_HOSPITAL";
        if (normalized.contains("DISTRICT")) return "DISTRICT_OR_MISSION_HOSPITAL";
        if (normalized.contains("POLYCLINIC")) return "POLYCLINIC";
        if (normalized.contains("RURAL HOSPITAL")) return "RURAL_HOSPITAL";
        if (normalized.contains("CLINIC") || normalized.contains("RURAL HEALTH CENTRE")) return "CLINIC";
        return "GENERAL_HOSPITAL";
    }

    private static String inferDeploymentMode(String facilityTier) {
        return switch (facilityTier) {
            case "CENTRAL_HOSPITAL", "PROVINCIAL_OR_TERTIARY_HOSPITAL", "GENERAL_HOSPITAL",
                 "DISTRICT_OR_MISSION_HOSPITAL", "RURAL_HOSPITAL" -> "DEDICATED_POD";
            case "POLYCLINIC" -> "SHARED_POD";
            case "VIRTUAL_HOSPITAL" -> "VIRTUAL_ONLY";
            default -> "EDGE_ASSISTED";
        };
    }

    private static String inferContinuityClass(String facilityTier) {
        return switch (facilityTier) {
            case "CLINIC", "HEALTH_POST", "COMMUNITY" -> "EDGE_CRITICAL";
            case "POLYCLINIC" -> "CONNECTED_TOLERANT";
            default -> "LOCAL_EXECUTION_REQUIRED";
        };
    }

    private static String inferWorkflowArchetype(String facilityTier, List<String> capabilities) {
        if ("VIRTUAL_HOSPITAL".equals(facilityTier)) {
            return "VIRTUAL_CARE_NETWORK";
        }
        if (capabilities.contains("INPATIENT") || capabilities.contains("SURGERY") || capabilities.contains("ICU")) {
            return switch (facilityTier) {
                case "CENTRAL_HOSPITAL", "PROVINCIAL_OR_TERTIARY_HOSPITAL", "GENERAL_HOSPITAL",
                     "QUINARY_HOSPITAL" -> "REFERRAL_AND_SPECIALIST";
                default -> "HOSPITAL_INPATIENT";
            };
        }
        if ("POLYCLINIC".equals(facilityTier)) {
            return "AMBULATORY_MULTI_SERVICE";
        }
        return "PRIMARY_CARE";
    }
}
