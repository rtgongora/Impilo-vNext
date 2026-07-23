package zw.gov.mohcc.impilo.msikaflow.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msikaflow.api.TrustHeaderExtractor;
import zw.gov.mohcc.impilo.msikaflow.api.dto.ApiResponse;
import zw.gov.mohcc.impilo.msikaflow.core.VendorService;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorProfileEntity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Anonymous (no-auth) read of the approved/operational vendors directory.
 *
 * <p>Public, tenant-scoped: honours a forwarded {@code X-Tenant-Id} when the gateway
 * synthesises the public tenant; otherwise falls back to the care-plane public tenant.
 * Only {@code ACTIVE} vendors are exposed, and each is projected through a strict
 * allow-list so no internal binding, tenant, actor, or audit fields ever leave the wall.
 * Reuses {@link VendorService#listVendors} — the same method the ops
 * {@code GET /v1/vendors} directory uses.
 */
@RestController
@RequestMapping("/v1/public/vendors")
public class PublicVendorController {

    /** Care-plane public tenant used when no tenant header is forwarded on an anon call. */
    private static final UUID PUBLIC_CARE_PLANE_TENANT =
            UUID.fromString("00000000-0000-4000-8000-000000000001");

    private final VendorService vendorService;

    public PublicVendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    /** Paged public directory of operational (ACTIVE) vendors. */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listApprovedVendors(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest httpReq) {
        String correlationId = TrustHeaderExtractor.correlationId(httpReq);
        Map<String, Object> body = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        long total = 0;
        try {
            UUID tenantId = resolveTenant(httpReq);
            Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, size));
            Page<VendorProfileEntity> vendors =
                    vendorService.listVendors(tenantId, VendorStatus.ACTIVE, pageable);
            total = vendors.getTotalElements();
            for (VendorProfileEntity v : vendors.getContent()) {
                results.add(toPublicView(v));
            }
        } catch (RuntimeException ex) {
            // Fail closed to an empty directory — never leak internal detail on a public read.
            results.clear();
            total = 0;
        }
        body.put("results", results);
        body.put("total", total);
        body.put("page", page);
        body.put("size", size);
        return ResponseEntity.ok(ApiResponse.ok(body, correlationId));
    }

    /** Resolve tenant from a forwarded header, else the care-plane public tenant. */
    private UUID resolveTenant(HttpServletRequest req) {
        String val = req.getHeader(TrustHeaderExtractor.H_TENANT_ID);
        if (val == null || val.isBlank()) {
            return PUBLIC_CARE_PLANE_TENANT;
        }
        try {
            return UUID.fromString(val.trim());
        } catch (IllegalArgumentException ex) {
            return PUBLIC_CARE_PLANE_TENANT;
        }
    }

    /** Strict allow-list projection — drops tenant, actorBinding, audit, and internals. */
    private Map<String, Object> toPublicView(VendorProfileEntity v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getVendorId());
        m.put("name", v.getName());
        m.put("type", v.getType() != null ? v.getType().name() : null);
        m.put("status", v.getStatus() != null ? v.getStatus().name() : null);
        m.put("coverage", v.getCoverage());
        return m;
    }
}
