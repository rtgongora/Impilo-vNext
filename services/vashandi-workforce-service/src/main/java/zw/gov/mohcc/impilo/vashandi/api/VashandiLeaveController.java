package zw.gov.mohcc.impilo.vashandi.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.vashandi.core.LeaveAvailabilityService;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.LeaveAvailabilityEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@RestController
public class VashandiLeaveController {

    private final LeaveAvailabilityService leaveService;

    public VashandiLeaveController(LeaveAvailabilityService leaveService) {
        this.leaveService = leaveService;
    }

    @GetMapping("/v1/internal/vashandi/availability")
    public List<LeaveAvailabilityEntity> listAvailability(
            @RequestParam(value = "workforce_profile_id", required = false) UUID workforceProfileId) {
        return leaveService.list(tenantId(), workforceProfileId);
    }

    @PostMapping("/v1/internal/vashandi/leave")
    public LeaveAvailabilityEntity createLeave(@RequestBody VashandiDtos.CreateLeaveRequest request) throws Exception {
        return leaveService.create(tenantId(), request);
    }

    @PatchMapping("/v1/internal/vashandi/leave/{id}")
    public ResponseEntity<LeaveAvailabilityEntity> updateLeave(@PathVariable UUID id,
                                                               @RequestBody VashandiDtos.UpdateLeaveRequest request)
            throws Exception {
        return leaveService.get(tenantId(), id)
                .map(existing -> {
                    try {
                        return ResponseEntity.ok(leaveService.update(tenantId(), id, request));
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private UUID tenantId() {
        TrustContext ctx = TrustContextHolder.require();
        return ctx.tenantId();
    }
}
