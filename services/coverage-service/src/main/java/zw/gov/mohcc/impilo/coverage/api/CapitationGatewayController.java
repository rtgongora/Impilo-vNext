package zw.gov.mohcc.impilo.coverage.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.coverage.api.dto.AdvancedDtos.*;
import zw.gov.mohcc.impilo.coverage.core.CoverageEventService;
import zw.gov.mohcc.impilo.coverage.domain.CapitationReportEntity;
import zw.gov.mohcc.impilo.coverage.domain.GatewayTransactionEntity;
import zw.gov.mohcc.impilo.coverage.repository.CapitationReportRepository;
import zw.gov.mohcc.impilo.coverage.repository.GatewayTransactionRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Capitation encounter reports (spec §5/§19) + Claims Switch / Payer Gateway (spec §5/§32).
 *
 * <p>The switch routes/validates/acknowledges transactions — it does NOT adjudicate. A technical
 * ack (received + readable) is distinct from a business ack (accepted for processing) and neither
 * is a claim approval. External payer rails (API/manual) are credential-gated: the switch records
 * the routing and returns a technical ack + a PENDING business ack rather than faking acceptance.
 */
@RestController
@RequestMapping("/internal/v1/coverage")
public class CapitationGatewayController {

    private static final Set<String> ROUTES = Set.of("INTERNAL_ADJUDICATION", "EXTERNAL_MANUAL", "EXTERNAL_API");

    private final CapitationReportRepository capitationRepository;
    private final GatewayTransactionRepository gatewayRepository;
    private final CoverageEventService eventService;

    public CapitationGatewayController(CapitationReportRepository capitationRepository,
                                       GatewayTransactionRepository gatewayRepository,
                                       CoverageEventService eventService) {
        this.capitationRepository = capitationRepository;
        this.gatewayRepository = gatewayRepository;
        this.eventService = eventService;
    }

    // ---- Capitation ----

    @GetMapping("/capitation-reports")
    public ResponseEntity<List<CapitationView>> listCapitation(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(name = "provider_id") String providerId) {
        return ResponseEntity.ok(capitationRepository
                .findByTenantIdAndProviderIdOrderByCreatedAtDesc(UUID.fromString(tenantId), providerId)
                .stream().map(CapitationView::of).toList());
    }

    @PostMapping("/capitation-reports")
    @Transactional
    public ResponseEntity<CapitationView> createCapitation(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @Valid @RequestBody CapitationRequest req) {
        UUID tid = UUID.fromString(tenantId);
        CapitationReportEntity c = new CapitationReportEntity();
        c.setId(UUID.randomUUID());
        c.setTenantId(tid);
        c.setPodId(podId);
        c.setProviderId(req.providerId());
        c.setFacilityId(req.facilityId());
        c.setPayerId(req.payerId());
        c.setPeriodStart(req.periodStart());
        c.setPeriodEnd(req.periodEnd());
        if (req.memberCount() != null) c.setMemberCount(req.memberCount());
        if (req.encounterCount() != null) c.setEncounterCount(req.encounterCount());
        if (req.capitationAmount() != null) c.setCapitationAmount(req.capitationAmount());
        capitationRepository.save(c);
        return ResponseEntity.status(HttpStatus.CREATED).body(CapitationView.of(c));
    }

    // ---- Claims Switch / Payer Gateway ----

    @GetMapping("/gateway/transactions")
    public ResponseEntity<List<GatewayView>> listGateway(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(defaultValue = "RECEIVED") String status) {
        return ResponseEntity.ok(gatewayRepository
                .findByTenantIdAndStatusOrderByCreatedAtDesc(UUID.fromString(tenantId), status.trim().toUpperCase())
                .stream().map(GatewayView::of).toList());
    }

    @PostMapping("/gateway/transactions")
    @Transactional
    public ResponseEntity<GatewayView> route(
            @RequestHeader("X-Tenant-ID") String tenantId, @RequestHeader("X-Pod-ID") String podId,
            @RequestHeader(value = "X-Correlation-ID", required = false) String correlationId,
            @Valid @RequestBody GatewayRequest req) {
        UUID tid = UUID.fromString(tenantId);
        String route = req.route() != null && ROUTES.contains(req.route().trim().toUpperCase())
                ? req.route().trim().toUpperCase() : "INTERNAL_ADJUDICATION";
        String scn = "SCN-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        GatewayTransactionEntity t = GatewayTransactionEntity.create(tid, podId, scn, req.transactionType(),
                req.senderRef(), req.receiverPayer(), route);
        t.setCorrelationId(CorrelationIds.fromHeader(correlationId));
        // Technical ack: received + structurally valid.
        t.setTechnicalAck("ACCEPTED");
        // Business ack depends on the route — INTERNAL accepts for adjudication; external rails are
        // credential-gated, so they are PENDING (never faked as accepted).
        if ("INTERNAL_ADJUDICATION".equals(route)) {
            t.setBusinessAck("ACCEPTED");
            t.setStatus("ROUTED");
        } else {
            t.setBusinessAck("PENDING");
            t.setStatus("ROUTED");
        }
        gatewayRepository.save(t);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("switch_control_number", scn);
        payload.put("transaction_type", req.transactionType());
        payload.put("route", route);
        payload.put("technical_ack", t.getTechnicalAck());
        payload.put("business_ack", t.getBusinessAck());
        payload.put("gated", !"INTERNAL_ADJUDICATION".equals(route));
        eventService.emitDomain("GATEWAY_TX", t.getId().toString(), "coverage.switch.routed",
                CorrelationIds.fromHeader(correlationId), tid, podId, scn, "GATEWAY_TX", payload);
        return ResponseEntity.status(HttpStatus.CREATED).body(GatewayView.of(t));
    }
}
