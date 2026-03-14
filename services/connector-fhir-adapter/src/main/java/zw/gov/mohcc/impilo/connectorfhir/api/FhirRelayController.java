package zw.gov.mohcc.impilo.connectorfhir.api;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelope;
import zw.gov.mohcc.impilo.connectorfhir.api.dto.*;
import zw.gov.mohcc.impilo.connectorfhir.core.FhirRelayService;
import zw.gov.mohcc.impilo.connectorfhir.domain.RelayAuditEntity;
import zw.gov.mohcc.impilo.connectorfhir.domain.RelayDestinationEntity;

import java.util.*;

@RestController
public class FhirRelayController {

    private final FhirRelayService relayService;

    public FhirRelayController(FhirRelayService relayService) {
        this.relayService = relayService;
    }

    @PostMapping("/internal/v1/fhir/relay")
    public ResponseEntity<?> relayBundle(@Valid @RequestBody RelayBundleRequest request,
                                           jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        try {
            RelayAuditEntity audit = relayService.relayBundle(UUID.fromString(ctx.tenantId()),
                    ctx.podId(), ctx.correlationId(), idempotencyKey, request,
                    ctx.principal() != null ? ctx.principal().getName() : "system");
            return ResponseEntity.status(HttpStatus.CREATED).body(toAuditResponse(audit));
        } catch (FhirRelayService.DestinationNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ErrorEnvelope.of("NOT_FOUND", e.getMessage(), ctx.requestId(), ctx.correlationId()));
        }
    }

    @PostMapping("/internal/v1/fhir/destinations")
    public ResponseEntity<?> createDestination(@Valid @RequestBody CreateDestinationRequest request,
                                                 jakarta.servlet.http.HttpServletRequest httpRequest) {
        RequestContext ctx = RequestContextHolder.require();
        String idempotencyKey = httpRequest.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        RelayDestinationEntity dest = relayService.createDestination(UUID.fromString(ctx.tenantId()),
                ctx.podId(), ctx.correlationId(), idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDestResponse(dest));
    }

    @GetMapping("/internal/v1/fhir/destinations")
    public ResponseEntity<?> listDestinations() {
        RequestContext ctx = RequestContextHolder.require();
        List<RelayDestinationEntity> dests = relayService.listDestinations(UUID.fromString(ctx.tenantId()));
        return ResponseEntity.ok(Map.of("items", dests.stream().map(this::toDestResponse).toList(),
                "count", dests.size()));
    }

    @GetMapping("/internal/v1/fhir/audit")
    public ResponseEntity<?> listAuditLog(@RequestParam(defaultValue = "0") int cursor,
                                            @RequestParam(defaultValue = "50") int limit) {
        RequestContext ctx = RequestContextHolder.require();
        Page<RelayAuditEntity> page = relayService.listAuditLog(UUID.fromString(ctx.tenantId()), cursor, limit);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", page.getContent().stream().map(this::toAuditResponse).toList());
        body.put("cursor", page.hasNext() ? String.valueOf(cursor + 1) : null);
        body.put("limit", limit);
        body.put("total_elements", page.getTotalElements());
        body.put("has_more", page.hasNext());
        return ResponseEntity.ok(body);
    }

    private RelayAuditResponse toAuditResponse(RelayAuditEntity a) {
        return new RelayAuditResponse(a.getAuditId(), a.getTenantId(), a.getBundleId(),
                a.getResourceType(), a.getResourceCount(), a.getDestinationId(), a.getDestinationUrl(),
                a.getDecision(), a.getOutcome(), a.getErrorMessage(), a.getActorRef(),
                a.getCreatedAt(), a.getCompletedAt());
    }

    private DestinationResponse toDestResponse(RelayDestinationEntity d) {
        return new DestinationResponse(d.getDestinationId(), d.getTenantId(), d.getName(),
                d.getEndpointUrl(), d.getResourceTypes(), d.getAuthType(), d.isEnabled(),
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
