package zw.gov.mohcc.impilo.gl.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.gl.core.TrialBalanceService;
import zw.gov.mohcc.impilo.gl.persistence.entity.TrialBalanceSnapshotEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/gl/trial-balance")
public class GlTrialBalanceController {

    private final TrialBalanceService trialBalanceService;

    public GlTrialBalanceController(TrialBalanceService trialBalanceService) {
        this.trialBalanceService = trialBalanceService;
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> trialBalance(@RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(trialBalanceService.trialBalance(tenant, periodId));
    }

    @PostMapping("/snapshot")
    public ResponseEntity<TrialBalanceSnapshotEntity> snapshot(@RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(trialBalanceService.snapshotTrialBalance(tenant, periodId));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<List<TrialBalanceSnapshotEntity>> snapshots(@RequestParam("period_id") UUID periodId) {
        UUID tenant = UUID.fromString(RequestContextHolder.require().tenantId());
        return ResponseEntity.ok(trialBalanceService.listSnapshots(tenant, periodId));
    }
}
