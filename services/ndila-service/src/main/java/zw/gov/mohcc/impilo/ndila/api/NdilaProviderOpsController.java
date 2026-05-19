package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaRoutingProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;
import zw.gov.mohcc.impilo.ndila.core.provider.ProviderCapabilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Operations endpoints — provider visibility for the Ndila operations
 * dashboard. Read-only.
 */
@RestController
public class NdilaProviderOpsController {

    private final List<NdilaGeocodingProvider> geocoders;
    private final List<NdilaRoutingProvider> routers;
    private final List<NdilaTileProvider> tiles;

    public NdilaProviderOpsController(List<NdilaGeocodingProvider> geocoders,
                                      List<NdilaRoutingProvider> routers,
                                      List<NdilaTileProvider> tiles) {
        this.geocoders = geocoders;
        this.routers = routers;
        this.tiles = tiles;
    }

    @GetMapping("/internal/v1/ndila/providers")
    public ResponseEntity<Map<String, Object>> providers() {
        Map<String, Object> out = new HashMap<>();
        out.put("geocoding", geocoders.stream().map(p -> describe(p.providerName(),
                p.capabilities(), p.enabled())).toList());
        out.put("routing", routers.stream().map(p -> describe(p.providerName(),
                p.capabilities(), p.enabled())).toList());
        out.put("tiles", tiles.stream().map(p -> describe(p.providerName(),
                p.capabilities(), p.enabled())).toList());
        return ResponseEntity.ok(out);
    }

    private Map<String, Object> describe(String name, ProviderCapabilities cap, boolean enabled) {
        List<String> caps = new ArrayList<>();
        if (cap.supportsGeocoding()) caps.add("geocoding");
        if (cap.supportsReverseGeocoding()) caps.add("reverse-geocoding");
        if (cap.supportsRouting()) caps.add("routing");
        if (cap.supportsDistanceMatrix()) caps.add("distance-matrix");
        if (cap.supportsIsochrones()) caps.add("isochrones");
        if (cap.supportsTraffic()) caps.add("traffic");
        if (cap.supportsOffline()) caps.add("offline");
        if (cap.supportsTiles()) caps.add("tiles");
        if (cap.supportsVectorTiles()) caps.add("vector-tiles");
        if (cap.supportsBoundaries()) caps.add("boundaries");
        if (cap.supportsZimbabweCoverage()) caps.add("zw-coverage");
        if (cap.supportsCommercialSla()) caps.add("commercial-sla");
        if (cap.supportsSensitiveWorkflows()) caps.add("sensitive-workflows");
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("enabled", enabled);
        m.put("capabilities", caps);
        m.put("estimatedCostCategory", cap.estimatedCostCategory());
        m.put("dataResidencyNotes", cap.dataResidencyNotes());
        m.put("privacyNotes", cap.privacyNotes());
        return m;
    }
}
