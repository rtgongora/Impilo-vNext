package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.core.FulfilmentDispatchService;
import zw.gov.mohcc.impilo.msikaflow.domain.FulfilmentPathway;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;

import java.util.UUID;

/**
 * OF-B17 — fulfilment-pathway election for a marketplace request (§11.8
 * post-commitment column intake). The delivery-minimum block (name / phone /
 * address) is stored request-row-only and disclosed to Nhume only at
 * delivery-task creation (§12.1) — it is NEVER published to competing vendors.
 *
 * <p>Additive logistics-lane surface: the RFO request machinery
 * ({@code MarketplaceRequestController}) is deliberately untouched.</p>
 */
@RestController
@RequestMapping("/v1/marketplace-requests")
public class MarketplacePathwayController {

    private final FulfilmentDispatchService fulfilmentDispatchService;

    public MarketplacePathwayController(FulfilmentDispatchService fulfilmentDispatchService) {
        this.fulfilmentDispatchService = fulfilmentDispatchService;
    }

    public record PathwayElectionDto(
            @NotNull FulfilmentPathway pathway,
            String deliveryName,
            String deliveryPhone,
            String deliveryAddress,
            Double deliveryLat,
            Double deliveryLng) {}

    public record PathwayView(String requestId, String fulfilmentPathway, boolean deliveryDetailsPresent) {}

    @PutMapping("/{requestId}/fulfilment-pathway")
    public ResponseEntity<ApiResponse<PathwayView>> electPathway(@PathVariable String requestId,
                                                                 @Valid @RequestBody PathwayElectionDto dto,
                                                                 HttpServletRequest httpReq) {
        UUID tenantId = TrustHeaderExtractor.tenantId(httpReq);
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        MarketplaceRequestEntity request = fulfilmentDispatchService.electPathway(
                requestId, tenantId, dto.pathway(),
                dto.deliveryName(), dto.deliveryPhone(), dto.deliveryAddress(),
                dto.deliveryLat(), dto.deliveryLng());
        return ResponseEntity.ok(ApiResponse.ok(new PathwayView(
                request.getRequestId(),
                request.getFulfilmentPathway() != null ? request.getFulfilmentPathway().name() : null,
                request.getDeliveryDetailsJson() != null), correlationId));
    }
}
