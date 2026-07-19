package zw.gov.mohcc.impilo.vito.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Internal client search — masked PII responses.
 *
 * Returns truncated names (first 2 chars + ***) and masked DOB (year only)
 * to support operator lookup without full PII exposure.
 */
@RestController
@RequestMapping("/v1/internal/clients")
public class InternalSearchController {

    private final ClientRepository clientRepository;
    private final zw.gov.mohcc.impilo.vito.core.IdentityService identityService;
    private final zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository outboxRepository;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public InternalSearchController(ClientRepository clientRepository,
                                    zw.gov.mohcc.impilo.vito.core.IdentityService identityService,
                                    zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository outboxRepository,
                                    com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.clientRepository = clientRepository;
        this.identityService = identityService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * POST /v1/internal/clients/search — search by name fragment or Impilo ID.
     * Results are PII-masked.
     */
    @PostMapping("/search")
    public ResponseEntity<?> search(@RequestBody SearchRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        UUID tenantId = ctx.tenantId();
        String q = request.query() != null ? request.query().trim() : "";
        if (q.length() < 2) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("INVALID_QUERY", "Query must be at least 2 characters", 400,
                            ctx.correlationId().toString()));
        }

        int page = request.page() != null ? request.page() : 0;
        int size = request.size() != null ? Math.min(request.size(), 100) : 20;

        // Identity Contract §6: exact Impilo-ID match resolves via the alias vault
        // (the column is encrypted, not value-queryable); name search stays in SQL.
        java.util.Optional<ClientEntity> byImpiloId = identityService.findByImpiloId(tenantId, q);
        List<Map<String, Object>> masked;
        long total;
        if (byImpiloId.isPresent()) {
            masked = List.of(maskClient(byImpiloId.get()));
            total = 1;
        } else {
            Page<ClientEntity> results = clientRepository.searchByNameOrImpiloId(
                    tenantId, q, PageRequest.of(page, size));
            masked = results.getContent().stream().map(this::maskClient).toList();
            total = results.getTotalElements();
        }

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(masked, page, size, total),
                ctx.correlationId().toString()));
    }

    /**
     * GET /v1/internal/clients/{healthId}/full — full <b>unmasked</b> record for authorized
     * operators. This is a break-glass surface: it requires a purpose of use <b>and</b> an
     * explicit access reason, and every successful open writes a dedicated
     * {@code CLIENT_RECORD_BREAK_GLASS_ACCESS} audit event (actor + purpose + reason). The
     * prior implementation claimed "audit-logged via trust headers" but captured no reason
     * and wrote no explicit audit event.
     */
    @GetMapping("/{healthId}/full")
    public ResponseEntity<?> getFull(@PathVariable UUID healthId,
                                     @RequestParam(value = "reason", required = false) String reason) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }
        if (reason == null || reason.trim().length() < 5) {
            return ResponseEntity.badRequest().body(
                    ApiResponse.error("MISSING_REASON", "An access reason (>=5 chars) is required to open an unmasked record",
                            400, ctx.correlationId().toString()));
        }

        return clientRepository.findByTenantIdAndHealthId(ctx.tenantId(), healthId)
                .map(client -> {
                    writeBreakGlassAudit(ctx, healthId, reason.trim());
                    return ResponseEntity.ok(ApiResponse.ok(client, ctx.correlationId().toString()));
                })
                .orElse(ResponseEntity.status(404).body(
                        ApiResponse.error("NOT_FOUND", "Client not found", 404,
                                ctx.correlationId().toString())));
    }

    private void writeBreakGlassAudit(TrustContext ctx, UUID healthId, String reason) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("healthId", healthId.toString());
            payload.put("actorId", ctx.actorId());
            payload.put("purposeOfUse", ctx.purposeOfUse());
            payload.put("reason", reason);
            payload.put("correlationId", ctx.correlationId() == null ? null : ctx.correlationId().toString());

            zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity event =
                    new zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity();
            event.setAggregateType("CLIENT_RECORD");
            event.setAggregateId(healthId.toString());
            event.setEventType("CLIENT_RECORD_BREAK_GLASS_ACCESS");
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(ctx.tenantId() == null ? null : ctx.tenantId().toString());
            event.setCorrelationId(ctx.correlationId() == null ? null : ctx.correlationId().toString());
            outboxRepository.save(event);
        } catch (Exception e) {
            // An audit-write failure must not silently expose the record without a trail.
            throw new IllegalStateException("Break-glass access could not be audited", e);
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private Map<String, Object> maskClient(ClientEntity c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("healthId", c.getHealthId().toString());
        m.put("impiloId", c.getImpiloId());
        m.put("givenName", maskName(c.getGivenName()));
        m.put("familyName", maskName(c.getFamilyName()));
        m.put("yearOfBirth", c.getDateOfBirth() != null ? c.getDateOfBirth().getYear() : null);
        m.put("sex", c.getSex());
        m.put("status", c.getStatus().name());
        return m;
    }

    private String maskName(String name) {
        if (name == null || name.length() < 2) return "***";
        return name.substring(0, 2) + "***";
    }

    record SearchRequest(String query, Integer page, Integer size) {}
}
