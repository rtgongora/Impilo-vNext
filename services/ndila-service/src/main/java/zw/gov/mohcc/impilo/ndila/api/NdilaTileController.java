package zw.gov.mohcc.impilo.ndila.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ndila.api.dto.NdilaDtos.TileConfigResponse;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaOperationContext;
import zw.gov.mohcc.impilo.ndila.core.policy.NdilaProviderPolicyService;
import zw.gov.mohcc.impilo.ndila.core.provider.NdilaTileProvider;

@RestController
public class NdilaTileController {

    private final NdilaProviderPolicyService policy;
    private final String environment;

    public NdilaTileController(NdilaProviderPolicyService policy,
                               @Value("${ndila.environment:development}") String environment) {
        this.policy = policy;
        this.environment = environment;
    }

    @GetMapping({"/api/v1/ndila/tiles/config", "/api/v1/maps/tiles/config"})
    public ResponseEntity<TileConfigResponse> tileConfig(
            @RequestHeader(value = "X-Tenant-ID", required = false) String tenantId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId) {
        NdilaOperationContext ctx = NdilaTrustHeaders.fromHeaders(
                tenantId, null, null, "OPERATIONS_MANAGEMENT", "TILE_CONFIG",
                "ndila-api", "PUBLIC", correlationId, environment, "ZW");
        NdilaProviderPolicyService.Decision decision = policy.selectTiles(ctx);
        if (decision.chosen() instanceof NdilaTileProvider tp) {
            NdilaTileProvider.TileConfig cfg = tp.tileConfig(new NdilaTileProvider.TileConfigRequest(
                    tenantId, environment, "general"));
            return ResponseEntity.ok(new TileConfigResponse(
                    cfg.providerName(), cfg.tileUrlTemplate(), cfg.attribution(),
                    cfg.maxZoom(), cfg.supportsOffline()));
        }
        return ResponseEntity.ok(new TileConfigResponse(
                "MOCK", "mock://tiles/{z}/{x}/{y}.png", "Ndila Mock Tiles", 19, true));
    }
}
