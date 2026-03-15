package zw.gov.mohcc.impilo.support.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.support.api.dto.DashboardStatsResponse;
import zw.gov.mohcc.impilo.support.core.SupportService;

import java.util.UUID;

@RestController
public class DashboardController {

    private final SupportService supportService;

    public DashboardController(SupportService supportService) { this.supportService = supportService; }

    @GetMapping("/internal/v1/support/dashboard/stats")
    public ResponseEntity<DashboardStatsResponse> getDashboardStats() {
        RequestContext ctx = RequestContextHolder.require();
        DashboardStatsResponse stats = supportService.getDashboardStats(UUID.fromString(ctx.tenantId()));
        return ResponseEntity.ok(stats);
    }
}
