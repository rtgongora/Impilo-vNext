package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.FulfillmentPolicyDtos;
import zw.gov.mohcc.impilo.msika.api.dto.OfferingDtos;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.ListingEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.OfferingEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.ListingRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.OfferingRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

@Service
public class OfferingService {

    private final OfferingRepository offeringRepository;
    private final CatalogItemRepository itemRepository;
    private final ListingRepository listingRepository;
    private final FulfillmentPolicyService fulfillmentPolicyService;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OfferingService(OfferingRepository offeringRepository,
                           CatalogItemRepository itemRepository,
                           ListingRepository listingRepository,
                           FulfillmentPolicyService fulfillmentPolicyService,
                           EventOutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.offeringRepository = offeringRepository;
        this.itemRepository = itemRepository;
        this.listingRepository = listingRepository;
        this.fulfillmentPolicyService = fulfillmentPolicyService;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public OfferingDtos.OfferingView create(OfferingDtos.CreateOfferingRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        itemRepository.findById(req.catalogItemId())
                .orElseThrow(() -> new IllegalArgumentException("Catalog item not found: " + req.catalogItemId()));

        OfferingEntity e = new OfferingEntity();
        e.setOfferingId(UlidGenerator.generate());
        e.setTenantId(req.tenantId() != null ? req.tenantId() : ctx.tenantId());
        e.setCatalogItemId(req.catalogItemId());
        e.setOfferingType(req.offeringType() != null ? req.offeringType() : "VENDOR");
        e.setProviderType(req.providerType());
        e.setProviderRef(req.providerRef());
        e.setFacilityRef(req.facilityRef());
        e.setVendorRef(req.vendorRef());
        if (req.active() != null) e.setActive(req.active());
        if (req.availabilityState() != null) e.setAvailabilityState(req.availabilityState());
        e.setInventoryMode(req.inventoryMode());
        e.setBookingMode(req.bookingMode());
        e.setPickupModesSupportedJson(JsonSupport.toJsonSafe(objectMapper, req.pickupModesSupported(), "[]"));
        e.setDeliveryModesSupportedJson(JsonSupport.toJsonSafe(objectMapper, req.deliveryModesSupported(), "[]"));
        e.setLeadTimeMinutes(req.leadTimeMinutes());
        e.setGeographicCoverageJson(JsonSupport.toJsonSafe(objectMapper, req.geographicCoverage(), "{}"));
        e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        e.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        e = offeringRepository.save(e);
        emit("OFFERING", e.getOfferingId(), "OFFERING_CREATED", e, ctx);
        if (e.isActive()) {
            emit("OFFERING", e.getOfferingId(), "OFFERING_ACTIVATED", e, ctx);
        }
        return toView(e);
    }

    @Transactional
    public OfferingDtos.OfferingView update(String offeringId, OfferingDtos.UpdateOfferingRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        OfferingEntity e = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("Offering not found: " + offeringId));

        if (req.active() != null) e.setActive(req.active());
        if (req.availabilityState() != null) e.setAvailabilityState(req.availabilityState());
        if (req.inventoryMode() != null) e.setInventoryMode(req.inventoryMode());
        if (req.bookingMode() != null) e.setBookingMode(req.bookingMode());
        if (req.pickupModesSupported() != null) e.setPickupModesSupportedJson(JsonSupport.toJsonSafe(objectMapper, req.pickupModesSupported(), "[]"));
        if (req.deliveryModesSupported() != null) e.setDeliveryModesSupportedJson(JsonSupport.toJsonSafe(objectMapper, req.deliveryModesSupported(), "[]"));
        if (req.leadTimeMinutes() != null) e.setLeadTimeMinutes(req.leadTimeMinutes());
        if (req.geographicCoverage() != null) e.setGeographicCoverageJson(JsonSupport.toJsonSafe(objectMapper, req.geographicCoverage(), "{}"));
        if (req.metadata() != null) e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));

        e = offeringRepository.save(e);
        emit("OFFERING", e.getOfferingId(), "OFFERING_UPDATED", e, ctx);
        return toView(e);
    }

    public OfferingDtos.OfferingView get(String offeringId) {
        OfferingEntity e = offeringRepository.findById(offeringId)
                .orElseThrow(() -> new IllegalArgumentException("Offering not found: " + offeringId));
        return toView(e);
    }

    public List<OfferingDtos.OfferingView> listForItem(String itemId, boolean activeOnly) {
        return offeringRepository.findByCatalogItemIdAndActiveOrderByUpdatedAtDesc(itemId, activeOnly)
                .stream().map(this::toView).toList();
    }

    public List<OfferingDtos.OfferingView> listForTenant(UUID tenantId, boolean activeOnly) {
        return offeringRepository.findByTenantIdAndActiveOrderByUpdatedAtDesc(tenantId, activeOnly)
                .stream().map(this::toView).toList();
    }

    /**
     * Resolve the fulfilable offering for a listing or catalog item, plus its
     * effective fulfillment policy (offering-level policy wins, item-level is
     * the fallback; null when neither is configured). 404 (IllegalArgument)
     * when no offering resolves — msika-flow must never fulfil against thin air.
     */
    public OfferingDtos.ResolvedOfferingView resolve(String catalogItemId, String listingId) {
        OfferingEntity offering;
        if (listingId != null && !listingId.isBlank()) {
            ListingEntity listing = listingRepository.findById(listingId)
                    .orElseThrow(() -> new IllegalArgumentException("Listing not found: " + listingId));
            if (listing.getOfferingId() == null) {
                throw new IllegalArgumentException("Listing " + listingId + " has no offering attached");
            }
            offering = offeringRepository.findById(listing.getOfferingId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Offering not found: " + listing.getOfferingId() + " (referenced by listing " + listingId + ")"));
        } else if (catalogItemId != null && !catalogItemId.isBlank()) {
            offering = offeringRepository.findByCatalogItemIdAndActiveOrderByUpdatedAtDesc(catalogItemId, true)
                    .stream().findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "No active offering for catalog item: " + catalogItemId));
        } else {
            throw new IllegalArgumentException("Either catalogItemId or listingId is required");
        }

        FulfillmentPolicyDtos.FulfillmentPolicyView policy = fulfillmentPolicyService
                .listForOffering(offering.getOfferingId(), true).stream().findFirst()
                .orElseGet(() -> fulfillmentPolicyService
                        .listForItem(offering.getCatalogItemId(), true).stream().findFirst()
                        .orElse(null));
        return new OfferingDtos.ResolvedOfferingView(toView(offering), policy);
    }

    private OfferingDtos.OfferingView toView(OfferingEntity e) {
        return new OfferingDtos.OfferingView(
                e.getOfferingId(),
                e.getTenantId(),
                e.getCatalogItemId(),
                e.getOfferingType(),
                e.getProviderType(),
                e.getProviderRef(),
                e.getFacilityRef(),
                e.getVendorRef(),
                e.isActive(),
                e.getAvailabilityState(),
                e.getInventoryMode(),
                e.getBookingMode(),
                JsonSupport.parseJsonSafe(objectMapper, e.getPickupModesSupportedJson(), List.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getDeliveryModesSupportedJson(), List.of()),
                e.getLeadTimeMinutes(),
                JsonSupport.parseJsonSafe(objectMapper, e.getGeographicCoverageJson(), java.util.Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getMetadataJson(), java.util.Map.of()),
                e.getCreatedBy(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
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
        } catch (Exception e) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);
    }
}

