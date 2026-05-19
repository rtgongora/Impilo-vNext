package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.search.NdilaSpatialSearchService;

import java.util.UUID;

@RestController
public class NdilaSpatialSearchController {

    private final NdilaSpatialSearchService searchService;

    public NdilaSpatialSearchController(NdilaSpatialSearchService searchService) {
        this.searchService = searchService;
    }

    @PostMapping({"/api/v1/ndila/spatial/nearby", "/api/v1/maps/spatial/nearby"})
    public ResponseEntity<NearbyResponse> nearby(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody NearbyRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        Coordinate origin = new Coordinate(req.origin().latitude().doubleValue(),
                req.origin().longitude().doubleValue());
        int limit = req.limit() == null ? 20 : req.limit();
        var matches = searchService.nearby(tenant, origin, req.radiusMeters(),
                req.entityTypes(), limit);
        var out = matches.stream().map(m -> new NearbyMatch(
                m.location().getId().toString(),
                m.location().getName(),
                m.location().getLocationType(),
                m.distanceMeters(),
                m.location().getLatitude(),
                m.location().getLongitude())).toList();
        return ResponseEntity.ok(new NearbyResponse(req.origin(), out));
    }

    @PostMapping({"/api/v1/ndila/spatial/nearest", "/api/v1/maps/spatial/nearest"})
    public ResponseEntity<NearbyResponse> nearest(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody NearbyRequest req) {
        UUID tenant = UUID.fromString(tenantId);
        Coordinate origin = new Coordinate(req.origin().latitude().doubleValue(),
                req.origin().longitude().doubleValue());
        int limit = req.limit() == null ? 1 : req.limit();
        String locType = req.entityTypes() == null || req.entityTypes().isEmpty() ? null : req.entityTypes().get(0);
        var matches = searchService.nearest(tenant, origin, locType, limit);
        var out = matches.stream().map(m -> new NearbyMatch(
                m.location().getId().toString(),
                m.location().getName(),
                m.location().getLocationType(),
                m.distanceMeters(),
                m.location().getLatitude(),
                m.location().getLongitude())).toList();
        return ResponseEntity.ok(new NearbyResponse(req.origin(), out));
    }
}
