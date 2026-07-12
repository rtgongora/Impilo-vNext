package zw.gov.mohcc.impilo.msika.api.controller;

import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.msika.api.dto.ListingDtos;
import zw.gov.mohcc.impilo.msika.core.FavouriteService;
import zw.gov.mohcc.impilo.msika.core.ListingService;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.shared.response.PagedResponse;

import java.util.List;

/**
 * Msika storefront-lane listing API.
 *
 * Buyer discovery (search/detail/favourites) is auth-only; seller authoring is
 * gated to seller roles; moderation (approve/reject/suspend/publish) is gated to
 * the marketplace operator/governance roles. Policy (impilo.msika) is enforced at
 * the BFF/Tshepo gate; these role gates are defence in depth.
 */
@RestController
@RequestMapping("/v1/listings")
public class ListingController {

    private final ListingService listingService;
    private final FavouriteService favouriteService;

    public ListingController(ListingService listingService, FavouriteService favouriteService) {
        this.listingService = listingService;
        this.favouriteService = favouriteService;
    }

    private String corr() {
        return TrustContextHolder.require().correlationId().toString();
    }

    // ── Buyer discovery ───────────────────────────────────────────────────────────────

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<PagedResponse<ListingDtos.ListingView>>> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String risk,
            @RequestParam(required = false) String category,
            Pageable pageable) {
        Page<ListingDtos.ListingView> page = listingService.searchPublished(q, risk, category, pageable);
        PagedResponse<ListingDtos.ListingView> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, corr()));
    }

    @GetMapping("/{listingId}")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> getForBuyer(@PathVariable String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.getForBuyer(listingId), corr()));
    }

    @GetMapping("/favourites")
    public ResponseEntity<ApiResponse<List<String>>> myFavourites() {
        return ResponseEntity.ok(ApiResponse.ok(favouriteService.listMine(), corr()));
    }

    @PutMapping("/{listingId}/favourite")
    public ResponseEntity<ApiResponse<String>> favourite(@PathVariable String listingId) {
        favouriteService.add(listingId);
        return ResponseEntity.ok(ApiResponse.ok("FAVOURITED", corr()));
    }

    @DeleteMapping("/{listingId}/favourite")
    public ResponseEntity<ApiResponse<String>> unfavourite(@PathVariable String listingId) {
        favouriteService.remove(listingId);
        return ResponseEntity.ok(ApiResponse.ok("UNFAVOURITED", corr()));
    }

    // ── Seller authoring ──────────────────────────────────────────────────────────────

    @PostMapping
    @PreAuthorize("hasAnyRole('MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> create(@Valid @RequestBody ListingDtos.CreateListingRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(listingService.create(req), corr()));
    }

    @PatchMapping("/{listingId}")
    @PreAuthorize("hasAnyRole('MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> update(@PathVariable String listingId,
                                                                       @RequestBody ListingDtos.UpdateListingRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.update(listingId, req), corr()));
    }

    @PostMapping("/{listingId}/submit")
    @PreAuthorize("hasAnyRole('MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> submit(@PathVariable String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.submit(listingId), corr()));
    }

    @PostMapping("/{listingId}/media")
    @PreAuthorize("hasAnyRole('MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.MediaView>> addMedia(@PathVariable String listingId,
                                                                       @Valid @RequestBody ListingDtos.MediaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(listingService.addMedia(listingId, req), corr()));
    }

    @GetMapping("/seller/{sellerType}/{sellerId}")
    @PreAuthorize("hasAnyRole('MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','CATALOG_ADMIN','MARKETPLACE_OPERATOR','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<List<ListingDtos.ListingView>>> listForSeller(@PathVariable String sellerType,
                                                                                    @PathVariable String sellerId) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.listForSeller(sellerType, sellerId), corr()));
    }

    // ── Moderation / governance ───────────────────────────────────────────────────────

    @GetMapping("/moderation/queue")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','CATALOG_REVIEWER','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<PagedResponse<ListingDtos.ListingView>>> moderationQueue(Pageable pageable) {
        Page<ListingDtos.ListingView> page = listingService.moderationQueue(pageable);
        PagedResponse<ListingDtos.ListingView> paged = PagedResponse.of(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
        return ResponseEntity.ok(ApiResponse.ok(paged, corr()));
    }

    @PostMapping("/{listingId}/approve")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','CATALOG_REVIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> approve(@PathVariable String listingId,
                                                                        @RequestBody(required = false) ListingDtos.ModerationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.approve(listingId, req), corr()));
    }

    @PostMapping("/{listingId}/reject")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','CATALOG_REVIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> reject(@PathVariable String listingId,
                                                                       @RequestBody(required = false) ListingDtos.ModerationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.reject(listingId, req), corr()));
    }

    @PostMapping("/{listingId}/suspend")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','REGULATORY_VIEWER','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> suspend(@PathVariable String listingId,
                                                                        @RequestBody(required = false) ListingDtos.ModerationRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.suspend(listingId, req), corr()));
    }

    @PostMapping("/{listingId}/publish")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> publish(@PathVariable String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.publish(listingId), corr()));
    }

    @PostMapping("/{listingId}/unpublish")
    @PreAuthorize("hasAnyRole('MARKETPLACE_OPERATOR','MARKETPLACE_SELLER','PROVIDER','FACILITY_ADMIN','SYSTEM_ADMIN','DEVELOPER')")
    public ResponseEntity<ApiResponse<ListingDtos.ListingView>> unpublish(@PathVariable String listingId) {
        return ResponseEntity.ok(ApiResponse.ok(listingService.unpublish(listingId), corr()));
    }
}
