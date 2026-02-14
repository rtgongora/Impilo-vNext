package zw.gov.mohcc.impilo.integration.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.federation.FederationAuthority;
import zw.gov.mohcc.impilo.integration.api.dto.RouteRequest;
import zw.gov.mohcc.impilo.integration.api.dto.RouteResponse;
import zw.gov.mohcc.impilo.integration.service.RouteService;

import java.util.List;

@RestController
@RequestMapping("/internal/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping
    public ResponseEntity<RouteResponse> upsertRoute(@Valid @RequestBody RouteRequest request) {
        FederationAuthority.requireNational();
        RequestContext ctx = RequestContextHolder.require();

        RouteResponse response = routeService.upsertRoute(request, ctx);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<RouteResponse>> listRoutes() {
        RequestContext ctx = RequestContextHolder.require();

        List<RouteResponse> routes = routeService.listRoutes(ctx.tenantId());
        return ResponseEntity.ok(routes);
    }
}
