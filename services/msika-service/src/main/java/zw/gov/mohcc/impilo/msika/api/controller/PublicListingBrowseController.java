package zw.gov.mohcc.impilo.msika.api.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.msika.api.dto.ListingDtos;
import zw.gov.mohcc.impilo.msika.core.ListingService;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public (anonymous, no-auth) marketplace listing browse API.
 *
 * <p>Mirrors the buyer-discovery slice of {@link ListingController#search} and
 * {@link ListingController#getForBuyer}, but returns a strictly allow-listed
 * redacted view suitable for unauthenticated consumers. Only PUBLISHED listings
 * are ever surfaced (the underlying service methods enforce this).</p>
 *
 * <p>Never exposes internal refs: tenantId, sellerId, createdBy, moderationNotes,
 * approvedBy, chargeRef, eligibilityRule, clinicalRoute, offeringId, storefrontId.</p>
 *
 * <p>Trust context: the anon lane forwards a synthesized public tenant. The reused
 * service methods do not require a tenant argument (search filters on PUBLISHED
 * status; detail reads by id and only touches TrustContextHolder.get() for optional
 * regulated-view audit), so the read works whether or not a tenant is present.</p>
 */
@RestController
@RequestMapping("/v1/public/marketplace")
public class PublicListingBrowseController {

    private static final Logger log = LoggerFactory.getLogger(PublicListingBrowseController.class);

    private final ListingService listingService;

    public PublicListingBrowseController(ListingService listingService) {
        this.listingService = listingService;
    }

    /** Correlation id for the ApiResponse envelope; resilient on the anon lane. */
    private String corr() {
        TrustContext ctx = TrustContextHolder.get();
        return ctx != null ? ctx.correlationId().toString() : java.util.UUID.randomUUID().toString();
    }

    @GetMapping("/listings")
    public ResponseEntity<ApiResponse<Map<String, Object>>> browse(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String risk,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);

        List<Map<String, Object>> results = List.of();
        long total = 0;
        int outPage = safePage;
        int outSize = safeSize;

        try {
            // Same service method ListingController.search uses; signature (q, risk, category, pageable).
            Page<ListingDtos.ListingView> found = listingService.searchPublished(
                    q, risk, category, PageRequest.of(safePage, safeSize));
            results = found.getContent().stream().map(this::toPublicView).toList();
            total = found.getTotalElements();
            outPage = found.getNumber();
            outSize = found.getSize();
        } catch (Exception ex) {
            log.warn("Public marketplace browse failed [q={}, category={}, risk={}]: {}",
                    q, category, risk, ex.toString());
            results = List.of();
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("results", results);
        body.put("total", total);
        body.put("page", outPage);
        body.put("size", outSize);
        return ResponseEntity.ok(ApiResponse.ok(body, corr()));
    }

    @GetMapping("/listings/{listingId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> detail(@PathVariable String listingId) {
        try {
            // Reuses buyer-detail path: only PUBLISHED listings are returned; else throws.
            ListingDtos.ListingView view = listingService.getForBuyer(listingId);
            if (view == null) {
                throw new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Listing not found: " + listingId);
            }
            return ResponseEntity.ok(ApiResponse.ok(toPublicView(view), corr()));
        } catch (org.springframework.web.server.ResponseStatusException rse) {
            throw rse;
        } catch (Exception ex) {
            log.warn("Public marketplace detail miss [listingId={}]: {}", listingId, ex.toString());
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Listing not found: " + listingId);
        }
    }

    /**
     * Allow-listed, redacted public view. Field names track the {@link ListingDtos.ListingView}
     * record accessors exactly. Internal refs are never copied in.
     */
    private Map<String, Object> toPublicView(ListingDtos.ListingView v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.listingId());                 // opaque id for detail links
        m.put("title", v.title());
        m.put("summary", v.summary());
        m.put("riskClassification", v.riskClassification());
        m.put("priceDisplay", v.priceDisplay());
        m.put("priceAmount", v.priceAmount());
        m.put("priceCurrency", v.priceCurrency());
        m.put("fulfilmentMode", v.fulfilmentMode());
        m.put("media", v.media());
        m.put("publishedAt", v.publishedAt());
        m.put("status", v.status());
        return m;
    }
}
