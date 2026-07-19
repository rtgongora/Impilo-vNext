package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AdvancedDtos.CobRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.AdvancedDtos.CobView;
import zw.gov.mohcc.impilo.coverage.core.CobService;

import java.util.List;
import java.util.UUID;

/** Coordination of Benefits (spec §15). */
@RestController
@RequestMapping("/internal/v1/coverage/cob")
public class CobController {

    private final CobService service;

    public CobController(CobService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<CobView>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        return ResponseEntity.ok(service.forMember(UUID.fromString(tenantId), memberCpid)
                .stream().map(CobView::of).toList());
    }

    @PostMapping("/coordinate")
    public ResponseEntity<CobView> coordinate(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody CobRequest req) {
        CobView view = CobView.of(service.coordinate(UUID.fromString(tenantId), podId, req.memberCpid(),
                req.benefitCode(), req.standardCharge(), CorrelationIds.fromHeader(correlationId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(view);
    }
}
