package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.CreateReferralRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.ReferralValidityView;
import zw.gov.mohcc.impilo.coverage.api.dto.AuthorisationDtos.ReferralView;
import zw.gov.mohcc.impilo.coverage.core.ReferralService;
import zw.gov.mohcc.impilo.coverage.core.ReferralService.ReferralValidity;
import zw.gov.mohcc.impilo.coverage.domain.ReferralEntity;

import java.util.List;
import java.util.UUID;

/** Referral / gatekeeping endpoints (spec §16). */
@RestController
@RequestMapping("/internal/v1/coverage/referrals")
public class ReferralController {

    private final ReferralService service;

    public ReferralController(ReferralService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<List<ReferralView>> list(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "member_cpid") String memberCpid) {
        UUID tid = UUID.fromString(tenantId);
        return ResponseEntity.ok(service.forMember(tid, memberCpid).stream().map(ReferralView::of).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReferralView> get(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id) {
        return ResponseEntity.ok(ReferralView.of(service.get(UUID.fromString(tenantId), id)));
    }

    @PostMapping
    public ResponseEntity<ReferralView> create(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody CreateReferralRequest req) {
        UUID tid = UUID.fromString(tenantId);
        ReferralEntity r = service.create(tid, podId, req.coverageId(), req.memberCpid(),
                req.referringProviderId(), req.referredProviderId(), req.referredFacilityId(),
                req.clinicalReferralRef(), req.scope(), req.reason(), req.permittedVisits(), req.validTo(),
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.status(HttpStatus.CREATED).body(ReferralView.of(r));
    }

    @GetMapping("/{id}/validity")
    public ResponseEntity<ReferralValidityView> validity(
            @RequestHeader("X-Tenant-ID") String tenantId, @PathVariable UUID id,
            @RequestParam(name = "emergency", defaultValue = "false") boolean emergency) {
        ReferralValidity v = service.validate(UUID.fromString(tenantId), id, emergency);
        return ResponseEntity.ok(new ReferralValidityView(v.valid(), v.reason(), v.visitsRemaining()));
    }

    @PostMapping("/{id}/consume")
    public ResponseEntity<ReferralView> consume(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @PathVariable UUID id) {
        ReferralEntity r = service.consumeVisit(UUID.fromString(tenantId), podId, id,
                CorrelationIds.fromHeader(correlationId));
        return ResponseEntity.ok(ReferralView.of(r));
    }
}
