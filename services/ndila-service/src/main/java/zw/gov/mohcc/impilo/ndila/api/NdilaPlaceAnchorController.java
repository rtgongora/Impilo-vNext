package zw.gov.mohcc.impilo.ndila.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.ndila.core.place.NdilaPlaceAnchorService;
import zw.gov.mohcc.impilo.ndila.core.place.NdilaSiteMasterSyncService;
import zw.gov.mohcc.impilo.ndila.domain.NdilaLocationEntity;
import zw.gov.mohcc.impilo.ndila.domain.NdilaPlaceAnchorEntity;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared place anchors + Indawo site sync (D-L1). Anchor writes are stewarded
 * (defence-in-depth behind ext_authz + the PLACE-LINK-STEWARD policy family);
 * reads serve the "campus view" — every owner-keyed location sharing an anchor.
 */
@RestController
@RequestMapping("/api/v1/ndila/places")
public class NdilaPlaceAnchorController {

    private static final Set<String> STEWARD_ACTOR_TYPES =
            Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD");

    private final NdilaPlaceAnchorService anchorService;
    private final NdilaSiteMasterSyncService siteSyncService;

    public NdilaPlaceAnchorController(NdilaPlaceAnchorService anchorService,
                                      NdilaSiteMasterSyncService siteSyncService) {
        this.anchorService = anchorService;
        this.siteSyncService = siteSyncService;
    }

    public record CreateAnchorRequest(BigDecimal centroidLatitude, BigDecimal centroidLongitude,
                                      String boundaryGeojson, String ward, String district,
                                      String province, String campusLabel) {}

    @PostMapping("/anchors")
    public ResponseEntity<Map<String, Object>> createAnchor(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestBody CreateAnchorRequest request,
            HttpServletRequest http) {
        String steward = requireSteward(http);
        NdilaPlaceAnchorEntity anchor = anchorService.create(tenantId, steward,
                new NdilaPlaceAnchorService.CreateAnchor(
                        request.centroidLatitude(), request.centroidLongitude(),
                        request.boundaryGeojson(), request.ward(), request.district(),
                        request.province(), request.campusLabel()));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", anchorResponse(anchor)));
    }

    @PostMapping("/anchors/{anchorId}/assign/{locationId}")
    public ResponseEntity<Map<String, Object>> assign(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("anchorId") UUID anchorId,
            @PathVariable("locationId") UUID locationId,
            HttpServletRequest http) {
        requireSteward(http);
        NdilaLocationEntity location = anchorService.assign(tenantId, anchorId, locationId);
        return ResponseEntity.ok(Map.of("data", locationResponse(location)));
    }

    @PostMapping("/locations/{locationId}/detach-anchor")
    public ResponseEntity<Map<String, Object>> detach(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("locationId") UUID locationId,
            HttpServletRequest http) {
        requireSteward(http);
        NdilaLocationEntity location = anchorService.detach(tenantId, locationId);
        return ResponseEntity.ok(Map.of("data", locationResponse(location)));
    }

    /** The campus view: every owner-keyed location sharing this anchor. */
    @GetMapping("/anchors/{anchorId}/locations")
    public ResponseEntity<Map<String, Object>> anchorLocations(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable("anchorId") UUID anchorId) {
        List<Map<String, Object>> locations = anchorService.locations(tenantId, anchorId).stream()
                .map(this::locationResponse)
                .toList();
        return ResponseEntity.ok(Map.of("data", Map.of("locations", locations)));
    }

    /** Sync Indawo sites into the owner-keyed location index (INDAWO/SITE). */
    @PostMapping("/sites/sync")
    public ResponseEntity<Map<String, Object>> syncSites(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) UUID correlationId,
            @RequestBody Map<String, Object> body,
            HttpServletRequest http) {
        requireSteward(http);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("records");
        if (records == null || records.isEmpty()) {
            throw new IllegalArgumentException("records is required");
        }
        NdilaSiteMasterSyncService.SyncResult result =
                siteSyncService.syncRecords(tenantId, records, correlationId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("recordsTotal", result.recordsTotal());
        payload.put("locationsCreated", result.locationsCreated());
        payload.put("locationsUpdated", result.locationsUpdated());
        payload.put("reviewQueued", result.reviewQueued());
        payload.put("results", result.results());
        return ResponseEntity.ok(Map.of("data", payload));
    }

    private String requireSteward(HttpServletRequest http) {
        String actorType = http.getHeader("X-Actor-Type");
        String normalised = actorType == null ? "" : actorType.trim().toUpperCase(Locale.ROOT);
        if (!STEWARD_ACTOR_TYPES.contains(normalised)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Place anchors are steward-maintained");
        }
        String actorId = http.getHeader("X-Actor-ID");
        return actorId != null ? actorId : normalised;
    }

    private Map<String, Object> anchorResponse(NdilaPlaceAnchorEntity anchor) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", anchor.getId());
        m.put("centroidLatitude", anchor.getCentroidLatitude());
        m.put("centroidLongitude", anchor.getCentroidLongitude());
        m.put("ward", anchor.getWard());
        m.put("district", anchor.getDistrict());
        m.put("province", anchor.getProvince());
        m.put("campusLabel", anchor.getCampusLabel());
        return m;
    }

    private Map<String, Object> locationResponse(NdilaLocationEntity location) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", location.getId());
        m.put("ownerService", location.getOwnerService());
        m.put("ownerEntityType", location.getOwnerEntityType());
        m.put("ownerEntityId", location.getOwnerEntityId());
        m.put("name", location.getName());
        m.put("locationType", location.getLocationType());
        m.put("anchorId", location.getAnchorId());
        m.put("latitude", location.getLatitude());
        m.put("longitude", location.getLongitude());
        return m;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> onBadRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
