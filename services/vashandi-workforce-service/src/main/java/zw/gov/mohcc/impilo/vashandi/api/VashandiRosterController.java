package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.RosterService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.RosterEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.ShiftEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
public class VashandiRosterController {

    private final RosterService rosterService;

    public VashandiRosterController(RosterService rosterService) {
        this.rosterService = rosterService;
    }

    @GetMapping("/v1/internal/vashandi/rosters")
    public List<RosterEntity> listRosters() {
        return rosterService.list(tenantId());
    }

    @GetMapping("/v1/internal/vashandi/rosters/{id}")
    public ResponseEntity<RosterEntity> getRoster(@PathVariable UUID id) {
        return rosterService.get(tenantId(), id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/v1/internal/vashandi/rosters/{id}/shifts")
    public List<ShiftEntity> listShifts(@PathVariable UUID id) {
        return rosterService.shifts(tenantId(), id);
    }

    @PostMapping("/v1/internal/vashandi/rosters")
    public RosterEntity createRoster(@RequestBody VashandiDtos.CreateRosterRequest request) throws Exception {
        return rosterService.create(tenantId(), request);
    }

    @PostMapping("/v1/internal/vashandi/rosters/{id}/approve")
    public RosterEntity approveRoster(@PathVariable UUID id) throws Exception {
        return rosterService.approve(tenantId(), id);
    }

    @PostMapping("/v1/internal/vashandi/shifts")
    public ShiftEntity createShift(@RequestBody VashandiDtos.CreateShiftRequest request) throws Exception {
        return rosterService.createShift(tenantId(), request);
    }

    @PatchMapping("/v1/internal/vashandi/shifts/{id}")
    public ShiftEntity updateShift(@PathVariable UUID id, @RequestBody VashandiDtos.UpdateShiftRequest request)
            throws Exception {
        return rosterService.updateShift(tenantId(), id, request);
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
