package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.ListingDtos;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingMediaEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.CatalogItemEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.RiskFrictionMappingEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingMediaRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.OfferingRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.RiskFrictionMappingRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Buyer-facing listing lifecycle: DRAFT -> SUBMITTED -> AWAITING_APPROVAL ->
 * APPROVED/REJECTED -> PUBLISHED -> UNPUBLISHED/SUSPENDED.
 *
 * Regulated/high-risk listings require a VERIFIED seller storefront before they
 * can be published (defence in depth — the policy layer also gates this). Every
 * approval/rejection/suspension/regulated publish writes an audit row. Catalog,
 * offering, order, billing, payment, stock and clinical truth all stay with their
 * owners — a listing only references them.
 */
@Service
public class ListingService {

    /** Risk classes that require a VERIFIED seller + provider/facility context to publish. */
    private static final Set<String> REGULATED_RISK = Set.of("HIGH_RISK", "REGULATED");

    /** Catalog-item kinds that are sellable and therefore must carry a price to publish. */
    private static final Set<String> SELLABLE_KINDS = Set.of("PRODUCT", "SERVICE", "ORDERABLE");

    private final ListingRepository listingRepository;
    private final ListingMediaRepository mediaRepository;
    private final CatalogItemRepository itemRepository;
    private final StorefrontService storefrontService;
    private final SourcingService sourcingService;
    private final OfferingRepository offeringRepository;
    private final RiskFrictionMappingRepository riskFrictionRepository;
    private final EventOutboxRepository outboxRepository;
    private final ListingAuditService auditService;
    private final ObjectMapper objectMapper;

    public ListingService(ListingRepository listingRepository,
                          ListingMediaRepository mediaRepository,
                          CatalogItemRepository itemRepository,
                          StorefrontService storefrontService,
                          EventOutboxRepository outboxRepository,
                          ListingAuditService auditService,
                          ObjectMapper objectMapper,
                          SourcingService sourcingService,
                          OfferingRepository offeringRepository,
                          RiskFrictionMappingRepository riskFrictionRepository) {
        this.listingRepository = listingRepository;
        this.mediaRepository = mediaRepository;
        this.itemRepository = itemRepository;
        this.storefrontService = storefrontService;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.sourcingService = sourcingService;
        this.offeringRepository = offeringRepository;
        this.riskFrictionRepository = riskFrictionRepository;
    }

    // ── Seller authoring ─────────────────────────────────────────────────────────────

    @Transactional
    public ListingDtos.ListingView create(ListingDtos.CreateListingRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        itemRepository.findById(req.catalogItemId())
                .orElseThrow(() -> new IllegalArgumentException("Catalog item not found: " + req.catalogItemId()));

        ListingEntity e = new ListingEntity();
        e.setListingId(UlidGenerator.generate());
        e.setTenantId(req.tenantId() != null ? req.tenantId() : ctx.tenantId());
        e.setStorefrontId(req.storefrontId());
        e.setCatalogItemId(req.catalogItemId());
        e.setOfferingId(req.offeringId());
        e.setSellerType(req.sellerType());
        e.setSellerId(req.sellerId());
        e.setTitle(req.title());
        e.setSummary(req.summary());
        if (req.riskClassification() != null) e.setRiskClassification(req.riskClassification());
        e.setEligibilityRuleJson(JsonSupport.toJsonSafe(objectMapper, req.eligibilityRule(), "{}"));
        e.setClinicalRoute(req.clinicalRoute());
        e.setChargeRef(req.chargeRef());
        e.setPriceDisplay(req.priceDisplay());
        e.setPriceAmount(req.priceAmount());
        if (req.priceCurrency() != null && !req.priceCurrency().isBlank()) {
            e.setPriceCurrency(req.priceCurrency());
        }
        e.setFulfilmentMode(req.fulfilmentMode());
        e.setFundoRef(req.fundoRef());
        e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        e.setStatus("DRAFT");
        e.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");
        e = listingRepository.save(e);

        emit("LISTING", e.getListingId(), "LISTING_CREATED", e, ctx);
        auditService.record(ctx, "LISTING_CREATE", e.getListingId(), null,
                e.getRiskClassification(), null,
                Map.of("title", e.getTitle(), "sellerType", e.getSellerType(), "sellerId", e.getSellerId()));
        return toView(e);
    }

    @Transactional
    public ListingDtos.ListingView update(String listingId, ListingDtos.UpdateListingRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        if (req.title() != null) e.setTitle(req.title());
        if (req.summary() != null) e.setSummary(req.summary());
        if (req.riskClassification() != null) e.setRiskClassification(req.riskClassification());
        if (req.eligibilityRule() != null) e.setEligibilityRuleJson(JsonSupport.toJsonSafe(objectMapper, req.eligibilityRule(), "{}"));
        if (req.clinicalRoute() != null) e.setClinicalRoute(req.clinicalRoute());
        if (req.chargeRef() != null) e.setChargeRef(req.chargeRef());
        if (req.priceDisplay() != null) e.setPriceDisplay(req.priceDisplay());
        if (req.priceAmount() != null) e.setPriceAmount(req.priceAmount());
        if (req.priceCurrency() != null && !req.priceCurrency().isBlank()) e.setPriceCurrency(req.priceCurrency());
        if (req.fulfilmentMode() != null) e.setFulfilmentMode(req.fulfilmentMode());
        if (req.fundoRef() != null) e.setFundoRef(req.fundoRef());
        if (req.metadata() != null) e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_UPDATED", e, ctx);
        return toView(e);
    }

    /** Seller submits a draft for moderation. */
    @Transactional
    public ListingDtos.ListingView submit(String listingId) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        e.setStatus("AWAITING_APPROVAL");
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_SUBMITTED", e, ctx);
        auditService.record(ctx, "LISTING_SUBMIT", e.getListingId(), null, e.getRiskClassification(), null,
                Map.of("status", e.getStatus()));
        return toView(e);
    }

    // ── Moderation / governance ──────────────────────────────────────────────────────

    @Transactional
    public ListingDtos.ListingView approve(String listingId, ListingDtos.ModerationRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        e.setStatus("APPROVED");
        e.setApprovedBy(ctx.actorId());
        e.setApprovedAt(OffsetDateTime.now());
        e.setModerationNotes(req != null ? req.notes() : null);
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_APPROVED", e, ctx);
        auditService.record(ctx, "LISTING_APPROVE", e.getListingId(), null, e.getRiskClassification(), "ALLOW",
                Map.of("notes", req != null && req.notes() != null ? req.notes() : ""));
        return toView(e);
    }

    @Transactional
    public ListingDtos.ListingView reject(String listingId, ListingDtos.ModerationRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        e.setStatus("REJECTED");
        e.setModerationNotes(req != null ? req.notes() : null);
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_REJECTED", e, ctx);
        auditService.record(ctx, "LISTING_REJECT", e.getListingId(), null, e.getRiskClassification(), "DENY",
                Map.of("notes", req != null && req.notes() != null ? req.notes() : ""));
        return toView(e);
    }

    @Transactional
    public ListingDtos.ListingView suspend(String listingId, ListingDtos.ModerationRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        e.setStatus("SUSPENDED");
        e.setModerationNotes(req != null ? req.notes() : null);
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_SUSPENDED", e, ctx);
        auditService.record(ctx, "LISTING_SUSPEND", e.getListingId(), null, e.getRiskClassification(), "DENY",
                Map.of("notes", req != null && req.notes() != null ? req.notes() : ""));
        return toView(e);
    }

    // ── Publish / unpublish ──────────────────────────────────────────────────────────

    @Transactional
    public ListingDtos.ListingView publish(String listingId) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        if (!"APPROVED".equals(e.getStatus()) && !"UNPUBLISHED".equals(e.getStatus())) {
            throw new IllegalStateException("Listing must be APPROVED before publish (was " + e.getStatus() + ")");
        }
        // Regulated/high-risk listings require a VERIFIED seller storefront.
        if (REGULATED_RISK.contains(e.getRiskClassification())
                && !storefrontService.isSellerVerified(e.getSellerType(), e.getSellerId())) {
            auditService.record(ctx, "POLICY_DENIED", e.getListingId(), null, e.getRiskClassification(), "DENY",
                    Map.of("reason", "SELLER_NOT_VERIFIED", "phase", "publish"));
            throw new IllegalStateException("Regulated listing requires a VERIFIED seller storefront");
        }
        // Restricted sourcing categories (e.g. medicines) require a verified
        // credentialed seller regardless of the listing's risk classification.
        Optional<CatalogItemEntity> item = itemRepository.findById(e.getCatalogItemId());
        String sourcingCategory = item.map(CatalogItemEntity::getSourcingCategoryCode).orElse(null);
        if (sourcingService.isRestrictedCategory(sourcingCategory)
                && !storefrontService.isSellerVerified(e.getSellerType(), e.getSellerId())) {
            auditService.record(ctx, "POLICY_DENIED", e.getListingId(), null, e.getRiskClassification(), "DENY",
                    Map.of("reason", "RESTRICTED_CATEGORY_SELLER_NOT_VERIFIED",
                           "category", sourcingCategory, "phase", "publish"));
            throw new IllegalStateException("Listings in restricted sourcing category " + sourcingCategory
                    + " require a VERIFIED seller storefront with the applicable credential");
        }
        // Sellable listings (PRODUCT/SERVICE/ORDERABLE via the catalog item) must
        // carry a positive vendor price — a priceless sellable listing is dead at
        // checkout, so it is rejected at the gate rather than discovered downstream.
        String itemKind = item.map(CatalogItemEntity::getKind).orElse(null);
        if (itemKind != null && SELLABLE_KINDS.contains(itemKind)
                && (e.getPriceAmount() == null || e.getPriceAmount().compareTo(BigDecimal.ZERO) <= 0)) {
            auditService.record(ctx, "POLICY_DENIED", e.getListingId(), null, e.getRiskClassification(), "DENY",
                    Map.of("reason", "PRICE_REQUIRED", "itemKind", itemKind, "phase", "publish"));
            throw new IllegalStateException("Sellable listing (" + itemKind
                    + ") requires a price_amount greater than 0 before publish; set priceAmount on the listing");
        }
        // Risk-friction seller-context gate (V004 mapping): if the risk class
        // demands provider and/or facility custody, the seller must occupy at
        // least one of the required contexts.
        Optional<RiskFrictionMappingEntity> friction = riskFrictionRepository.findById(e.getRiskClassification());
        if (friction.isPresent() && (friction.get().isRequiresProvider() || friction.get().isRequiresFacility())) {
            List<String> allowedSellerTypes = new ArrayList<>();
            if (friction.get().isRequiresProvider()) allowedSellerTypes.add("PROVIDER");
            if (friction.get().isRequiresFacility()) allowedSellerTypes.add("FACILITY");
            if (!allowedSellerTypes.contains(e.getSellerType())) {
                auditService.record(ctx, "POLICY_DENIED", e.getListingId(), null, e.getRiskClassification(), "DENY",
                        Map.of("reason", "RISK_FRICTION_SELLER_TYPE",
                               "requiredSellerTypes", String.join("|", allowedSellerTypes),
                               "sellerType", e.getSellerType(), "phase", "publish"));
                throw new IllegalStateException("Risk class " + e.getRiskClassification()
                        + " requires seller type " + String.join(" or ", allowedSellerTypes)
                        + " (was " + e.getSellerType() + ")");
            }
        }
        // A listing that references an offering must reference a real one.
        if (e.getOfferingId() != null && offeringRepository.findById(e.getOfferingId()).isEmpty()) {
            auditService.record(ctx, "POLICY_DENIED", e.getListingId(), null, e.getRiskClassification(), "DENY",
                    Map.of("reason", "OFFERING_NOT_FOUND", "offeringId", e.getOfferingId(), "phase", "publish"));
            throw new IllegalStateException("Listing references offering " + e.getOfferingId()
                    + " which does not exist");
        }
        e.setStatus("PUBLISHED");
        e.setPublishedAt(OffsetDateTime.now());
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_PUBLISHED", e, ctx);
        auditService.record(ctx, "LISTING_PUBLISH", e.getListingId(), null, e.getRiskClassification(), "ALLOW",
                Map.of("riskClassification", e.getRiskClassification()));
        return toView(e);
    }

    @Transactional
    public ListingDtos.ListingView unpublish(String listingId) {
        TrustContext ctx = TrustContextHolder.require();
        ListingEntity e = require(listingId);
        e.setStatus("UNPUBLISHED");
        e = listingRepository.save(e);
        emit("LISTING", e.getListingId(), "LISTING_UNPUBLISHED", e, ctx);
        auditService.record(ctx, "LISTING_UNPUBLISH", e.getListingId(), null, e.getRiskClassification(), null, Map.of());
        return toView(e);
    }

    // ── Media ────────────────────────────────────────────────────────────────────────

    @Transactional
    public ListingDtos.MediaView addMedia(String listingId, ListingDtos.MediaRequest req) {
        require(listingId);
        ListingMediaEntity m = new ListingMediaEntity();
        m.setMediaId(UlidGenerator.generate());
        m.setListingId(listingId);
        if (req.mediaType() != null) m.setMediaType(req.mediaType());
        m.setMediaRef(req.mediaRef());
        m.setCaption(req.caption());
        if (req.sortOrder() != null) m.setSortOrder(req.sortOrder());
        m = mediaRepository.save(m);
        return toMediaView(m);
    }

    // ── Buyer discovery ──────────────────────────────────────────────────────────────

    public Page<ListingDtos.ListingView> searchPublished(String q, String risk, String category, Pageable pageable) {
        String qn = (q == null || q.isBlank()) ? null : q;
        String rn = (risk == null || risk.isBlank()) ? null : risk;
        String cn = category == null || category.isBlank() ? null : category;
        return listingRepository.searchPublished(qn, rn, cn, pageable).map(this::toView);
    }

    /**
     * Buyer detail view. Only PUBLISHED listings are visible to a buyer; for
     * regulated/high-risk listings an audit row (REGULATED_VIEW) is written.
     */
    public ListingDtos.ListingView getForBuyer(String listingId) {
        ListingEntity e = require(listingId);
        if (!"PUBLISHED".equals(e.getStatus())) {
            throw new IllegalStateException("Listing is not published");
        }
        if (REGULATED_RISK.contains(e.getRiskClassification())) {
            TrustContext ctx = TrustContextHolder.get();
            auditService.record(ctx, "REGULATED_VIEW", e.getListingId(),
                    ctx != null ? ctx.actorId() : null, e.getRiskClassification(), null,
                    Map.of("title", e.getTitle()));
        }
        return toView(e);
    }

    /** Seller/operator detail view — any status. */
    public ListingDtos.ListingView get(String listingId) {
        return toView(require(listingId));
    }

    public List<ListingDtos.ListingView> listForSeller(String sellerType, String sellerId) {
        return listingRepository.findBySellerTypeAndSellerIdOrderByUpdatedAtDesc(sellerType, sellerId)
                .stream().map(this::toView).toList();
    }

    public Page<ListingDtos.ListingView> moderationQueue(Pageable pageable) {
        return listingRepository.findByStatusInOrderByUpdatedAtDesc(
                List.of("AWAITING_APPROVAL", "SUBMITTED"), pageable).map(this::toView);
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────

    private ListingEntity require(String listingId) {
        return listingRepository.findById(listingId)
                .orElseThrow(() -> new IllegalArgumentException("Listing not found: " + listingId));
    }

    private ListingDtos.ListingView toView(ListingEntity e) {
        List<ListingDtos.MediaView> media = mediaRepository.findByListingIdOrderBySortOrderAsc(e.getListingId())
                .stream().map(this::toMediaView).toList();
        return new ListingDtos.ListingView(
                e.getListingId(), e.getTenantId(), e.getStorefrontId(), e.getCatalogItemId(), e.getOfferingId(),
                e.getSellerType(), e.getSellerId(), e.getTitle(), e.getSummary(), e.getRiskClassification(),
                JsonSupport.parseJsonSafe(objectMapper, e.getEligibilityRuleJson(), Map.of()),
                e.getClinicalRoute(), e.getChargeRef(), e.getPriceDisplay(),
                e.getPriceAmount(), e.getPriceCurrency(), e.getFulfilmentMode(),
                e.getFulfilmentOwner(), e.getStatus(), e.getModerationNotes(), e.getApprovedBy(),
                e.getApprovedAt(), e.getPublishedAt(), e.getFundoRef(),
                JsonSupport.parseJsonSafe(objectMapper, e.getMetadataJson(), Map.of()),
                media, e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
    }

    private ListingDtos.MediaView toMediaView(ListingMediaEntity m) {
        return new ListingDtos.MediaView(m.getMediaId(), m.getListingId(), m.getMediaType(),
                m.getMediaRef(), m.getCaption(), m.getSortOrder(), m.getCreatedAt());
    }

    private void emit(String aggregateType, String aggregateId, String eventType, Object payload, TrustContext ctx) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTenantId(ctx.tenantId());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);
    }
}
