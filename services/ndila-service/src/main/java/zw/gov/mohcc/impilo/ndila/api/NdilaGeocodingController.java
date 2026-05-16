package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.*;
import zw.gov.mohcc.impilo.ndila.core.geo.Coordinate;
import zw.gov.mohcc.impilo.ndila.core.geocoding.NdilaGeocodingService;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaGeocodingProvider;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@RestController
public class NdilaGeocodingController {

    private final NdilaGeocodingService geocodingService;

    public NdilaGeocodingController(NdilaGeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @PostMapping({"/api/v1/ndila/geocode", "/api/v1/maps/geocode"})
    public ResponseEntity<GeocodeResponse> geocode(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody GeocodeRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType, purpose, "GEOCODE", "ndila-api",
                "PUBLIC", correlationId, "development", "ZW");
        NdilaGeocodingService.GeocodeResponse resp = geocodingService.geocode(ctx,
                new NdilaGeocodingProvider.GeocodeRequest(
                        req.query(), req.country(), req.province(), req.district(), req.purposeOfUse()));
        List<GeocodeResultDto> results = resp.results().stream()
                .map(r -> new GeocodeResultDto(
                        r.name(),
                        BigDecimal.valueOf(r.coordinate().latitude()),
                        BigDecimal.valueOf(r.coordinate().longitude()),
                        r.address(), r.locality(), r.district(), r.province(), r.country(),
                        r.confidence(), r.providerName(), r.providerPlaceId(),
                        r.verificationStatus()))
                .toList();
        return ResponseEntity.ok(new GeocodeResponse(
                resp.success(), resp.denialReason(), results, resp.provider(),
                resp.fallbackUsed(), resp.servedFromCache(), resp.policyDecisionId()));
    }

    @PostMapping({"/api/v1/ndila/reverse-geocode", "/api/v1/maps/reverse-geocode"})
    public ResponseEntity<GeocodeResponse> reverse(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Actor-ID", required = false) String actorId,
            @RequestHeader(value = "X-Actor-Type", required = false) String actorType,
            @RequestHeader(value = "X-Purpose-Of-Use", required = false) String purpose,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @RequestBody ReverseGeocodeRequest req) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, actorId, actorType, purpose, "REVERSE_GEOCODE", "ndila-api",
                "PUBLIC", correlationId, "development", "ZW");
        Optional<NdilaGeocodingProvider.GeocodeResult> result = geocodingService.reverse(
                ctx, new Coordinate(req.latitude().doubleValue(), req.longitude().doubleValue()), req.country());
        List<GeocodeResultDto> list = result
                .map(r -> List.of(new GeocodeResultDto(
                        r.name(),
                        BigDecimal.valueOf(r.coordinate().latitude()),
                        BigDecimal.valueOf(r.coordinate().longitude()),
                        r.address(), r.locality(), r.district(), r.province(), r.country(),
                        r.confidence(), r.providerName(), r.providerPlaceId(), r.verificationStatus())))
                .orElse(List.of());
        return ResponseEntity.ok(new GeocodeResponse(
                true, null, list, list.isEmpty() ? null : list.get(0).provider(), false, false, null));
    }

    @GetMapping({"/api/v1/ndila/search", "/api/v1/maps/search"})
    public ResponseEntity<GeocodeResponse> search(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestParam("q") String query,
            @RequestParam(value = "country", required = false) String country,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "district", required = false) String district) {
        return geocode(tenantId, null, null, "OPERATIONS_MANAGEMENT", null,
                new GeocodeRequest(query, country, province, district, "OPERATIONS_MANAGEMENT"));
    }
}
