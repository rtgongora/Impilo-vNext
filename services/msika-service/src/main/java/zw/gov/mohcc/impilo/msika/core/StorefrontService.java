package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.StorefrontDtos;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.StorefrontEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.StorefrontRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Seller storefront lifecycle. Seller identity (provider/facility/site/programme/
 * vendor) is validated upstream against the owning registries; this service records
 * the validation outcome and emits an audit row for the (regulated, sensitive)
 * verification decision. It never creates provider/facility records.
 */
@Service
public class StorefrontService {

    private final StorefrontRepository storefrontRepository;
    private final EventOutboxRepository outboxRepository;
    private final ListingAuditService auditService;
    private final ObjectMapper objectMapper;

    public StorefrontService(StorefrontRepository storefrontRepository,
                             EventOutboxRepository outboxRepository,
                             ListingAuditService auditService,
                             ObjectMapper objectMapper) {
        this.storefrontRepository = storefrontRepository;
        this.outboxRepository = outboxRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public StorefrontDtos.StorefrontView create(StorefrontDtos.CreateStorefrontRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        StorefrontEntity e = new StorefrontEntity();
        e.setStorefrontId(UlidGenerator.generate());
        e.setTenantId(req.tenantId() != null ? req.tenantId() : ctx.tenantId());
        e.setSellerType(req.sellerType());
        e.setSellerId(req.sellerId());
        e.setDisplayName(req.displayName());
        e.setDescription(req.description());
        e.setContactRefsJson(JsonSupport.toJsonSafe(objectMapper, req.contactRefs(), "{}"));
        e.setServiceAreaJson(JsonSupport.toJsonSafe(objectMapper, req.serviceArea(), "{}"));
        e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        e.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");
        e = storefrontRepository.save(e);
        emit("STOREFRONT", e.getStorefrontId(), "STOREFRONT_CREATED", e, ctx);
        return toView(e);
    }

    /**
     * Record a seller-identity validation outcome. This is a governed action — the
     * caller has already validated against Varapi/Tuso/Indawo. We persist the
     * outcome and write an audit row (SELLER_VALIDATE).
     */
    @Transactional
    public StorefrontDtos.StorefrontView verify(String storefrontId, StorefrontDtos.VerifyStorefrontRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        StorefrontEntity e = storefrontRepository.findById(storefrontId)
                .orElseThrow(() -> new IllegalArgumentException("Storefront not found: " + storefrontId));
        e.setVerificationStatus(req.verificationStatus());
        e.setVerifiedBy(ctx.actorId());
        e.setVerifiedAt(OffsetDateTime.now());
        e = storefrontRepository.save(e);
        emit("STOREFRONT", e.getStorefrontId(), "STOREFRONT_VERIFICATION_RECORDED", e, ctx);
        auditService.record(ctx, "SELLER_VALIDATE", null, e.getStorefrontId(), null,
                "VERIFIED".equals(req.verificationStatus()) ? "ALLOW" : "DENY",
                Map.of("storefrontId", e.getStorefrontId(),
                        "sellerType", e.getSellerType(),
                        "sellerId", e.getSellerId(),
                        "verificationStatus", e.getVerificationStatus(),
                        "notes", req.notes() != null ? req.notes() : ""));
        return toView(e);
    }

    public StorefrontDtos.StorefrontView get(String storefrontId) {
        return toView(storefrontRepository.findById(storefrontId)
                .orElseThrow(() -> new IllegalArgumentException("Storefront not found: " + storefrontId)));
    }

    public List<StorefrontDtos.StorefrontView> listBySellerType(String sellerType) {
        return storefrontRepository.findBySellerType(sellerType).stream().map(this::toView).toList();
    }

    /** Whether a seller has a VERIFIED storefront — gate for publishing regulated listings. */
    public boolean isSellerVerified(String sellerType, String sellerId) {
        return storefrontRepository.findBySellerTypeAndSellerId(sellerType, sellerId)
                .map(s -> "VERIFIED".equals(s.getVerificationStatus()))
                .orElse(false);
    }

    private StorefrontDtos.StorefrontView toView(StorefrontEntity e) {
        return new StorefrontDtos.StorefrontView(
                e.getStorefrontId(), e.getTenantId(), e.getSellerType(), e.getSellerId(),
                e.getDisplayName(), e.getDescription(), e.getVerificationStatus(),
                e.getVerifiedBy(), e.getVerifiedAt(),
                JsonSupport.parseJsonSafe(objectMapper, e.getContactRefsJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getServiceAreaJson(), Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getMetadataJson(), Map.of()),
                e.getCreatedBy(), e.getCreatedAt(), e.getUpdatedAt());
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
