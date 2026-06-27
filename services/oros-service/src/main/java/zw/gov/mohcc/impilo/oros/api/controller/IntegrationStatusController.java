package zw.gov.mohcc.impilo.oros.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.oros.api.dto.IntegrationStatusDto;
import zw.gov.mohcc.impilo.oros.core.IntegrationStatusService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.List;

/**
 * Admin surface reporting honest configured/not-configured status for each external integration
 * adapter (brief §27 #11 / §28). No fake integrations: an adapter is configured only when a real
 * endpoint or feature flag is set.
 */
@RestController
@RequestMapping("/v1/integrations")
public class IntegrationStatusController {

    private final IntegrationStatusService integrationStatusService;

    public IntegrationStatusController(IntegrationStatusService integrationStatusService) {
        this.integrationStatusService = integrationStatusService;
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<IntegrationStatusDto>>> status() {
        String cid = TrustContextHolder.require().correlationId().toString();
        return ResponseEntity.ok(ApiResponse.ok(integrationStatusService.statuses(), cid));
    }
}
