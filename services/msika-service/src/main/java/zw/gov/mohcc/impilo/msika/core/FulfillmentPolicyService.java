package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.FulfillmentPolicyDtos;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.msika.persistence.entity.FulfillmentPolicyEntity;
import zw.gov.mohcc.impilo.msika.persistence.repository.CatalogItemRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.FulfillmentPolicyRepository;
import zw.gov.mohcc.impilo.msika.persistence.repository.OfferingRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;

@Service
public class FulfillmentPolicyService {

    private final FulfillmentPolicyRepository policyRepository;
    private final CatalogItemRepository itemRepository;
    private final OfferingRepository offeringRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public FulfillmentPolicyService(FulfillmentPolicyRepository policyRepository,
                                   CatalogItemRepository itemRepository,
                                   OfferingRepository offeringRepository,
                                   EventOutboxRepository outboxRepository,
                                   ObjectMapper objectMapper) {
        this.policyRepository = policyRepository;
        this.itemRepository = itemRepository;
        this.offeringRepository = offeringRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public FulfillmentPolicyDtos.FulfillmentPolicyView create(FulfillmentPolicyDtos.CreateFulfillmentPolicyRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        if (req.catalogItemId() != null) {
            itemRepository.findById(req.catalogItemId())
                    .orElseThrow(() -> new IllegalArgumentException("Catalog item not found: " + req.catalogItemId()));
        }
        if (req.offeringId() != null) {
            offeringRepository.findById(req.offeringId())
                    .orElseThrow(() -> new IllegalArgumentException("Offering not found: " + req.offeringId()));
        }
        if (req.catalogItemId() == null && req.offeringId() == null) {
            throw new IllegalArgumentException("Either catalogItemId or offeringId is required");
        }

        FulfillmentPolicyEntity e = new FulfillmentPolicyEntity();
        e.setPolicyId(UlidGenerator.generate());
        e.setTenantId(req.tenantId() != null ? req.tenantId() : ctx.tenantId());
        e.setCatalogItemId(req.catalogItemId());
        e.setOfferingId(req.offeringId());
        e.setAllowedFulfillmentModesJson(JsonSupport.toJsonSafe(objectMapper, req.allowedFulfillmentModes(), "[]"));
        e.setIdentityRequirementsJson(JsonSupport.toJsonSafe(objectMapper, req.identityRequirements(), "{}"));
        e.setDeliveryConstraintsJson(JsonSupport.toJsonSafe(objectMapper, req.deliveryConstraints(), "{}"));
        e.setSubstitutionPolicyJson(JsonSupport.toJsonSafe(objectMapper, req.substitutionPolicy(), "{}"));
        e.setCustodyRequirementsJson(JsonSupport.toJsonSafe(objectMapper, req.custodyRequirements(), "{}"));
        e.setProofRequirementsJson(JsonSupport.toJsonSafe(objectMapper, req.proofRequirements(), "{}"));
        e.setRestrictionsSummary(req.restrictionsSummary());
        if (req.active() != null) e.setActive(req.active());
        e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));
        e.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        e = policyRepository.save(e);
        emit("POLICY", e.getPolicyId(), "FULFILLMENT_POLICY_CREATED", e, ctx);
        return toView(e);
    }

    @Transactional
    public FulfillmentPolicyDtos.FulfillmentPolicyView update(String policyId, FulfillmentPolicyDtos.UpdateFulfillmentPolicyRequest req) {
        TrustContext ctx = TrustContextHolder.require();
        FulfillmentPolicyEntity e = policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId));

        if (req.allowedFulfillmentModes() != null) e.setAllowedFulfillmentModesJson(JsonSupport.toJsonSafe(objectMapper, req.allowedFulfillmentModes(), "[]"));
        if (req.identityRequirements() != null) e.setIdentityRequirementsJson(JsonSupport.toJsonSafe(objectMapper, req.identityRequirements(), "{}"));
        if (req.deliveryConstraints() != null) e.setDeliveryConstraintsJson(JsonSupport.toJsonSafe(objectMapper, req.deliveryConstraints(), "{}"));
        if (req.substitutionPolicy() != null) e.setSubstitutionPolicyJson(JsonSupport.toJsonSafe(objectMapper, req.substitutionPolicy(), "{}"));
        if (req.custodyRequirements() != null) e.setCustodyRequirementsJson(JsonSupport.toJsonSafe(objectMapper, req.custodyRequirements(), "{}"));
        if (req.proofRequirements() != null) e.setProofRequirementsJson(JsonSupport.toJsonSafe(objectMapper, req.proofRequirements(), "{}"));
        if (req.restrictionsSummary() != null) e.setRestrictionsSummary(req.restrictionsSummary());
        if (req.active() != null) e.setActive(req.active());
        if (req.metadata() != null) e.setMetadataJson(JsonSupport.toJsonSafe(objectMapper, req.metadata(), "{}"));

        e = policyRepository.save(e);
        emit("POLICY", e.getPolicyId(), "FULFILLMENT_POLICY_UPDATED", e, ctx);
        return toView(e);
    }

    public FulfillmentPolicyDtos.FulfillmentPolicyView get(String policyId) {
        return toView(policyRepository.findById(policyId)
                .orElseThrow(() -> new IllegalArgumentException("Policy not found: " + policyId)));
    }

    public List<FulfillmentPolicyDtos.FulfillmentPolicyView> listForItem(String itemId, boolean activeOnly) {
        return policyRepository.findByCatalogItemIdAndActiveOrderByUpdatedAtDesc(itemId, activeOnly)
                .stream().map(this::toView).toList();
    }

    public List<FulfillmentPolicyDtos.FulfillmentPolicyView> listForOffering(String offeringId, boolean activeOnly) {
        return policyRepository.findByOfferingIdAndActiveOrderByUpdatedAtDesc(offeringId, activeOnly)
                .stream().map(this::toView).toList();
    }

    private FulfillmentPolicyDtos.FulfillmentPolicyView toView(FulfillmentPolicyEntity e) {
        return new FulfillmentPolicyDtos.FulfillmentPolicyView(
                e.getPolicyId(),
                e.getTenantId(),
                e.getCatalogItemId(),
                e.getOfferingId(),
                JsonSupport.parseJsonSafe(objectMapper, e.getAllowedFulfillmentModesJson(), List.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getIdentityRequirementsJson(), java.util.Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getDeliveryConstraintsJson(), java.util.Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getSubstitutionPolicyJson(), java.util.Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getCustodyRequirementsJson(), java.util.Map.of()),
                JsonSupport.parseJsonSafe(objectMapper, e.getProofRequirementsJson(), java.util.Map.of()),
                e.getRestrictionsSummary(),
                e.isActive(),
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

