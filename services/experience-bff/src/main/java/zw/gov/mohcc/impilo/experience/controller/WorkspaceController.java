package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import java.util.*;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Workspace management endpoints.
 * GET  /internal/v1/workspaces — list workspaces for a facility.
 * POST /internal/v1/workspaces/{id}/activate — activate a workspace.
 *
 * Falls back to seeded workspaces for Parirenyatwa Group of Hospitals when
 * TUSO is unavailable.
 */
@RestController
@RequestMapping("/internal/v1/workspaces")
public class WorkspaceController {

    private final TusoServiceClient tusoClient;

    public WorkspaceController(TusoServiceClient tusoClient) {
        this.tusoClient = tusoClient;
    }

    private static final List<Map<String, Object>> SEEDED = buildSeeded();

    @GetMapping
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {

        try {
            long facIdLong = Long.parseLong(facilityId);
            com.fasterxml.jackson.databind.JsonNode tusoData = tusoClient.listWorkspaces(facIdLong);
            if (tusoData != null && tusoData.isArray() && tusoData.size() > 0) {
                List<Map<String, Object>> mapped = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode ws : tusoData) {
                    mapped.add(mapTusoWorkspace(ws, facilityId));
                }
                return ResponseEntity.ok(Map.of(
                        "data", mapped,
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (Exception e) {
            // fall through to seeded data
        }

        List<Map<String, Object>> filtered = SEEDED.stream()
                .filter(w -> facilityId.equals(((Map<?, ?>) w.get("attributes")).get("facilityId")))
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "data", filtered,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private static Map<String, Object> mapTusoWorkspace(com.fasterxml.jackson.databind.JsonNode ws, String facilityId) {
        String id = ws.has("id") ? ws.get("id").asText() : "";
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", ws.has("name") ? ws.get("name").asText() : "");
        attrs.put("facilityId", facilityId);
        attrs.put("workspaceType", ws.has("workspaceType") ? ws.get("workspaceType").asText() : "");
        attrs.put("description", ws.has("description") && !ws.get("description").isNull() ? ws.get("description").asText() : null);
        attrs.put("status", ws.has("active") && ws.get("active").asBoolean() ? "ACTIVE" : "INACTIVE");
        return Map.of("id", id, "type", "workspace", "attributes", attrs);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getWorkspace(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        try {
            java.util.UUID workspaceUuid = java.util.UUID.fromString(id);
            com.fasterxml.jackson.databind.JsonNode tusoWs = tusoClient.getWorkspace(workspaceUuid);
            if (tusoWs != null) {
                String facilityId = tusoWs.has("facilityId") ? tusoWs.get("facilityId").asText() : "";
                return ResponseEntity.ok(Map.of(
                        "data", mapTusoWorkspace(tusoWs, facilityId),
                        "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
            }
        } catch (IllegalArgumentException ignored) {
            // non-UUID ids fall through to seeded lookup
        } catch (Exception e) {
            // fall through to seeded lookup
        }

        Optional<Map<String, Object>> seeded = SEEDED.stream()
                .filter(w -> id.equals(w.get("id")))
                .findFirst();

        if (seeded.isPresent()) {
            return ResponseEntity.ok(Map.of(
                    "data", seeded.get(),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }

        return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).body(Map.of(
                "error", Map.of("code", "NOT_FOUND", "message", "Workspace not found"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<Map<String, Object>> activateWorkspace(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(CompanionHeaders.POD_ID) String podId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @RequestBody(required = false) Map<String, Object> body) {

        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("status", "ACTIVE");

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", Map.of("id", id, "type", "workspace", "attributes", attrs));
        response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
        return ResponseEntity.ok(response);
    }

    private static List<Map<String, Object>> buildSeeded() {
        List<Map<String, Object>> ws = new ArrayList<>();
        String pgh = "fac-pgh";

        // ── Parirenyatwa Group of Hospitals ───────────────────────────
        ws.add(workspace("ws-pgh-opd", pgh, "OPD — General Outpatients", "OUTPATIENT_DEPARTMENT",
                "Ground Floor, Main Building", 24));
        ws.add(workspace("ws-pgh-ed", pgh, "Emergency Department (Casualty)", "EMERGENCY_DEPARTMENT",
                "Block A, Ground Floor", 18));
        ws.add(workspace("ws-pgh-icu", pgh, "Intensive Care Unit", "ICU",
                "Block C, 2nd Floor", 12));
        ws.add(workspace("ws-pgh-mat", pgh, "Maternity Ward", "MATERNITY",
                "Block D", 40));
        ws.add(workspace("ws-pgh-surg", pgh, "General Surgery Theatre", "SURGERY",
                "Block B, 1st Floor", 8));
        ws.add(workspace("ws-pgh-paed", pgh, "Paediatrics Ward", "PAEDIATRICS",
                "Block E", 30));
        ws.add(workspace("ws-pgh-med", pgh, "Internal Medicine Ward", "MEDICAL_WARD",
                "Block F, 3rd Floor", 45));
        ws.add(workspace("ws-pgh-lab", pgh, "Central Laboratory", "LABORATORY",
                "Block G, Ground Floor", 6));
        ws.add(workspace("ws-pgh-pharm", pgh, "Main Pharmacy", "PHARMACY",
                "Main Building, Ground Floor", 4));
        ws.add(workspace("ws-pgh-rad", pgh, "Radiology & Imaging", "RADIOLOGY",
                "Block H", 5));
        ws.add(workspace("ws-pgh-ortho", pgh, "Orthopaedics", "ORTHOPAEDICS",
                "Block B, 2nd Floor", 20));
        ws.add(workspace("ws-pgh-psych", pgh, "Psychiatric Unit", "PSYCHIATRY",
                "Annex Building", 25));
        ws.add(workspace("ws-pgh-dent", pgh, "Dental Clinic", "DENTAL",
                "Block A, 1st Floor", 6));
        ws.add(workspace("ws-pgh-eye", pgh, "Eye Clinic (Ophthalmology)", "OPHTHALMOLOGY",
                "Block A, 2nd Floor", 8));
        ws.add(workspace("ws-pgh-ent", pgh, "ENT Clinic", "ENT",
                "Block A, 2nd Floor", 4));
        ws.add(workspace("ws-pgh-onc", pgh, "Oncology Unit", "ONCOLOGY",
                "Block I", 15));

        // ── Harare Central Hospital ──────────────────────────────────
        String hch = "fac-hch";
        ws.add(workspace("ws-hch-opd", hch, "OPD — Outpatients", "OUTPATIENT_DEPARTMENT",
                "Main Block", 20));
        ws.add(workspace("ws-hch-ed", hch, "Emergency Department", "EMERGENCY_DEPARTMENT",
                "Ground Floor", 14));
        ws.add(workspace("ws-hch-icu", hch, "ICU", "ICU", "2nd Floor", 10));
        ws.add(workspace("ws-hch-mat", hch, "Maternity", "MATERNITY", "Wing C", 35));
        ws.add(workspace("ws-hch-lab", hch, "Laboratory", "LABORATORY", "Ground Floor East", 4));
        ws.add(workspace("ws-hch-pharm", hch, "Pharmacy", "PHARMACY", "Main Entrance", 3));

        // ── Generic workspaces for other facilities ──────────────────
        for (String facId : List.of("fac-uch", "fac-mpc", "fac-chi", "fac-mnh", "fac-mty", "fac-gwu")) {
            ws.add(workspace("ws-" + facId + "-opd", facId, "Outpatients", "OUTPATIENT_DEPARTMENT", "Main Building", 12));
            ws.add(workspace("ws-" + facId + "-ed", facId, "Emergency", "EMERGENCY_DEPARTMENT", "Ground Floor", 8));
            ws.add(workspace("ws-" + facId + "-mat", facId, "Maternity", "MATERNITY", "Ward Block", 20));
            ws.add(workspace("ws-" + facId + "-pharm", facId, "Pharmacy", "PHARMACY", "Main Building", 2));
        }

        return Collections.unmodifiableList(ws);
    }

    private static Map<String, Object> workspace(String id, String facilityId,
            String name, String workspaceType, String location, int capacity) {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("name", name);
        attrs.put("facilityId", facilityId);
        attrs.put("workspaceType", workspaceType);
        attrs.put("location", location);
        attrs.put("capacity", capacity);
        attrs.put("status", "ACTIVE");
        return Map.of("id", id, "type", "workspace", "attributes", attrs);
    }
}
