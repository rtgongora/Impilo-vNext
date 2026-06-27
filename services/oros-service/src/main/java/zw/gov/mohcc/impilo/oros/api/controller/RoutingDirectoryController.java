package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.oros.api.dto.DestinationDto;
import zw.gov.mohcc.impilo.oros.core.RoutingDirectoryService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

/**
 * Policy-governed routing directory: the internal/external destinations a diagnostic order can be
 * routed to (spec — no unguarded provider lists; access is authenticated at the trust edge).
 */
@RestController
@RequestMapping("/v1/routing")
public class RoutingDirectoryController {

    private final RoutingDirectoryService routingDirectoryService;

    public RoutingDirectoryController(RoutingDirectoryService routingDirectoryService) {
        this.routingDirectoryService = routingDirectoryService;
    }

    @GetMapping("/destinations")
    public ResponseEntity<ApiResponse<List<DestinationDto>>> destinations() {
        String correlationId = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(routingDirectoryService.destinations(), correlationId));
    }
}
