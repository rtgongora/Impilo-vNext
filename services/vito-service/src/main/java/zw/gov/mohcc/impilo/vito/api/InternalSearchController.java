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

    public InternalSearchController(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
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

        Page<ClientEntity> results = clientRepository.searchByNameOrImpiloId(
                tenantId, q, PageRequest.of(page, size));

        List<Map<String, Object>> masked = results.getContent().stream()
                .map(this::maskClient)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(
                PagedResponse.of(masked, page, size, results.getTotalElements()),
                ctx.correlationId().toString()));
    }

    /**
     * GET /v1/internal/clients/{healthId}/full — full record for authorized operators.
     * Unmasked — audit-logged via trust headers.
     */
    @GetMapping("/{healthId}/full")
    public ResponseEntity<?> getFull(@PathVariable UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        if (ctx.mode() != AccessMode.INTERNAL) {
            return ResponseEntity.status(403).body(
                    ApiResponse.error("FORBIDDEN", "INTERNAL_ONLY", 403,
                            ctx.correlationId().toString()));
        }

        return clientRepository.findByTenantIdAndHealthId(ctx.tenantId(), healthId)
                .map(client -> ResponseEntity.ok(
                        ApiResponse.ok(client, ctx.correlationId().toString())))
                .orElse(ResponseEntity.status(404).body(
                        ApiResponse.error("NOT_FOUND", "Client not found", 404,
                                ctx.correlationId().toString())));
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
