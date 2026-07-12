package zw.gov.mohcc.impilo.msika.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msika.api.dto.*;
import zw.gov.mohcc.impilo.msika.domain.UlidGenerator;
import zw.gov.mohcc.impilo.msika.persistence.entity.*;
import zw.gov.mohcc.impilo.msika.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

@Service
public class ItemService {

    private static final Logger log = LoggerFactory.getLogger(ItemService.class);

    private final CatalogItemRepository itemRepository;
    private final CatalogRepository catalogRepository;
    private final ProductDetailRepository productDetailRepository;
    private final ServiceDetailRepository serviceDetailRepository;
    private final OrderableDetailRepository orderableDetailRepository;
    private final ChargeableDetailRepository chargeableDetailRepository;
    private final EventOutboxRepository outboxRepository;
    private final ChangeLogService changeLogService;
    private final ObjectMapper objectMapper;

    public ItemService(CatalogItemRepository itemRepository,
                       CatalogRepository catalogRepository,
                       ProductDetailRepository productDetailRepository,
                       ServiceDetailRepository serviceDetailRepository,
                       OrderableDetailRepository orderableDetailRepository,
                       ChargeableDetailRepository chargeableDetailRepository,
                       EventOutboxRepository outboxRepository,
                       ChangeLogService changeLogService,
                       ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.catalogRepository = catalogRepository;
        this.productDetailRepository = productDetailRepository;
        this.serviceDetailRepository = serviceDetailRepository;
        this.orderableDetailRepository = orderableDetailRepository;
        this.chargeableDetailRepository = chargeableDetailRepository;
        this.outboxRepository = outboxRepository;
        this.changeLogService = changeLogService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CatalogItemView createItem(String catalogId, CreateItemRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        CatalogEntity catalog = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + catalogId));

        if ("PUBLISHED".equals(catalog.getStatus())) {
            throw new IllegalStateException("Cannot add items to a PUBLISHED catalog");
        }

        // Check uniqueness
        itemRepository.findByCatalogIdAndCanonicalCode(catalogId, request.canonicalCode())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Item with code " + request.canonicalCode() + " already exists in catalog");
                });

        String itemId = UlidGenerator.generate();

        CatalogItemEntity item = new CatalogItemEntity();
        item.setItemId(itemId);
        item.setCatalogId(catalogId);
        item.setKind(request.kind());
        item.setCanonicalCode(request.canonicalCode());
        item.setDisplayName(request.displayName());
        item.setDescription(request.description());
        item.setSynonyms(request.synonyms());
        item.setTags(request.tags());
        item.setSourcingCategoryCode(request.sourcingCategoryCode());
        item.setCreatedBy(ctx.actorId() != null ? ctx.actorId() : "SYSTEM");

        try {
            if (request.restrictions() != null) item.setRestrictions(objectMapper.writeValueAsString(request.restrictions()));
            if (request.ziboBindings() != null) item.setZiboBindings(objectMapper.writeValueAsString(request.ziboBindings()));
            if (request.metadata() != null) item.setMetadata(objectMapper.writeValueAsString(request.metadata()));
        } catch (Exception e) {
            log.warn("Failed to serialize item JSON fields: {}", e.getMessage());
        }

        item = itemRepository.save(item);

        // Save kind-specific details
        if (request.product() != null) saveProductDetail(itemId, request.product());
        if (request.service() != null) saveServiceDetail(itemId, request.service());
        if (request.orderable() != null) saveOrderableDetail(itemId, request.orderable());
        if (request.chargeable() != null) saveChargeableDetail(itemId, request.chargeable());

        emitOutboxEvent("ITEM", itemId, "ITEM_CREATED", item, ctx);
        changeLogService.record("CREATE", "ITEM", itemId, request);
        log.info("Created item {} ({}) in catalog {}", itemId, request.canonicalCode(), catalogId);

        return toView(item);
    }

    @Transactional
    public CatalogItemView updateItem(String itemId, UpdateItemRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        CatalogItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));

        if (request.lockVersion() != item.getLockVersion()) {
            throw new jakarta.persistence.OptimisticLockException(
                    "Concurrent modification detected. Expected version " + request.lockVersion()
                    + " but found " + item.getLockVersion());
        }

        if (request.displayName() != null) item.setDisplayName(request.displayName());
        if (request.description() != null) item.setDescription(request.description());
        if (request.synonyms() != null) item.setSynonyms(request.synonyms());
        if (request.tags() != null) item.setTags(request.tags());
        if (request.sourcingCategoryCode() != null) item.setSourcingCategoryCode(request.sourcingCategoryCode());

        try {
            if (request.restrictions() != null) item.setRestrictions(objectMapper.writeValueAsString(request.restrictions()));
            if (request.ziboBindings() != null) item.setZiboBindings(objectMapper.writeValueAsString(request.ziboBindings()));
            if (request.metadata() != null) item.setMetadata(objectMapper.writeValueAsString(request.metadata()));
        } catch (Exception e) {
            log.warn("Failed to serialize item JSON fields: {}", e.getMessage());
        }

        item = itemRepository.save(item);

        // Update kind-specific details
        if (request.product() != null) saveProductDetail(itemId, request.product());
        if (request.service() != null) saveServiceDetail(itemId, request.service());
        if (request.orderable() != null) saveOrderableDetail(itemId, request.orderable());
        if (request.chargeable() != null) saveChargeableDetail(itemId, request.chargeable());

        emitOutboxEvent("ITEM", itemId, "ITEM_UPDATED", item, ctx);
        changeLogService.record("UPDATE", "ITEM", itemId, request);
        return toView(item);
    }

    public CatalogItemView getItem(String itemId) {
        CatalogItemEntity item = itemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Item not found: " + itemId));
        return toView(item);
    }

    public Page<CatalogItemView> listItems(String catalogId, String kind, Pageable pageable) {
        Page<CatalogItemEntity> page;
        if (kind != null) {
            page = itemRepository.findByCatalogIdAndKind(catalogId, kind, pageable);
        } else {
            page = itemRepository.findByCatalogId(catalogId, pageable);
        }
        return page.map(this::toView);
    }

    public CatalogItemView toView(CatalogItemEntity item) {
        ProductDetailDto productDto = null;
        ServiceDetailDto serviceDto = null;
        OrderableDetailDto orderableDto = null;
        ChargeableDetailDto chargeableDto = null;

        productDetailRepository.findById(item.getItemId()).ifPresent(pd ->
            { /* loaded below */ });

        var productOpt = productDetailRepository.findById(item.getItemId());
        if (productOpt.isPresent()) {
            var pd = productOpt.get();
            productDto = new ProductDetailDto(pd.getForm(), pd.getStrength(), pd.getRoute(),
                    pd.getUom(), pd.getPackSize(), pd.getBarcode(), pd.isBatchTracked(),
                    pd.isExpiryTracked(), pd.isColdChain(), pd.isControlled(),
                    pd.getManufacturer(), pd.getAtcCode());
        }

        var serviceOpt = serviceDetailRepository.findById(item.getItemId());
        if (serviceOpt.isPresent()) {
            var sd = serviceOpt.get();
            serviceDto = new ServiceDetailDto(sd.getServiceCategory(), sd.getDurationMinutes(),
                    sd.isRequiresSchedule(), sd.isRequiresReferral(),
                    sd.getFacilityLevelMin(), sd.getSpecialtyRequired());
        }

        var orderableOpt = orderableDetailRepository.findById(item.getItemId());
        if (orderableOpt.isPresent()) {
            var od = orderableOpt.get();
            orderableDto = new OrderableDetailDto(od.getOrderType(), od.getTargetKind(),
                    od.getTargetItemId(), od.getSpecimenType(), od.getBodySite(),
                    od.getInstructionsTemplate(), parseJson(od.getCriticalResultPolicy()));
        }

        var chargeableOpt = chargeableDetailRepository.findById(item.getItemId());
        if (chargeableOpt.isPresent()) {
            var cd = chargeableOpt.get();
            chargeableDto = new ChargeableDetailDto(cd.getTargetKind(), cd.getTargetItemId(),
                    cd.getTariffCode(), parseJson(cd.getPricingRefs()),
                    cd.getCostMethodRef(), parseJson(cd.getBillableRules()));
        }

        return new CatalogItemView(
                item.getItemId(), item.getCatalogId(), item.getKind(),
                item.getCanonicalCode(), item.getDisplayName(), item.getDescription(),
                item.getSynonyms(), item.getTags(),
            item.getSourcingCategoryCode(),
                parseJson(item.getRestrictions()), parseJson(item.getZiboBindings()),
                parseJson(item.getMetadata()), item.isActive(),
                item.getCreatedAt(), item.getUpdatedAt(), item.getLockVersion(),
                productDto, serviceDto, orderableDto, chargeableDto
        );
    }

    private void saveProductDetail(String itemId, ProductDetailDto dto) {
        ProductDetailEntity pd = productDetailRepository.findById(itemId).orElse(new ProductDetailEntity());
        pd.setItemId(itemId);
        if (dto.form() != null) pd.setForm(dto.form());
        if (dto.strength() != null) pd.setStrength(dto.strength());
        if (dto.route() != null) pd.setRoute(dto.route());
        if (dto.uom() != null) pd.setUom(dto.uom());
        if (dto.packSize() != null) pd.setPackSize(dto.packSize());
        if (dto.barcode() != null) pd.setBarcode(dto.barcode());
        if (dto.batchTracked() != null) pd.setBatchTracked(dto.batchTracked());
        if (dto.expiryTracked() != null) pd.setExpiryTracked(dto.expiryTracked());
        if (dto.coldChain() != null) pd.setColdChain(dto.coldChain());
        if (dto.controlled() != null) pd.setControlled(dto.controlled());
        if (dto.manufacturer() != null) pd.setManufacturer(dto.manufacturer());
        if (dto.atcCode() != null) pd.setAtcCode(dto.atcCode());
        productDetailRepository.save(pd);
    }

    private void saveServiceDetail(String itemId, ServiceDetailDto dto) {
        ServiceDetailEntity sd = serviceDetailRepository.findById(itemId).orElse(new ServiceDetailEntity());
        sd.setItemId(itemId);
        if (dto.serviceCategory() != null) sd.setServiceCategory(dto.serviceCategory());
        if (dto.durationMinutes() != null) sd.setDurationMinutes(dto.durationMinutes());
        if (dto.requiresSchedule() != null) sd.setRequiresSchedule(dto.requiresSchedule());
        if (dto.requiresReferral() != null) sd.setRequiresReferral(dto.requiresReferral());
        if (dto.facilityLevelMin() != null) sd.setFacilityLevelMin(dto.facilityLevelMin());
        if (dto.specialtyRequired() != null) sd.setSpecialtyRequired(dto.specialtyRequired());
        serviceDetailRepository.save(sd);
    }

    private void saveOrderableDetail(String itemId, OrderableDetailDto dto) {
        OrderableDetailEntity od = orderableDetailRepository.findById(itemId).orElse(new OrderableDetailEntity());
        od.setItemId(itemId);
        if (dto.orderType() != null) od.setOrderType(dto.orderType());
        if (dto.targetKind() != null) od.setTargetKind(dto.targetKind());
        if (dto.targetItemId() != null) od.setTargetItemId(dto.targetItemId());
        if (dto.specimenType() != null) od.setSpecimenType(dto.specimenType());
        if (dto.bodySite() != null) od.setBodySite(dto.bodySite());
        if (dto.instructionsTemplate() != null) od.setInstructionsTemplate(dto.instructionsTemplate());
        try {
            if (dto.criticalResultPolicy() != null) od.setCriticalResultPolicy(objectMapper.writeValueAsString(dto.criticalResultPolicy()));
        } catch (Exception ignored) {}
        orderableDetailRepository.save(od);
    }

    private void saveChargeableDetail(String itemId, ChargeableDetailDto dto) {
        ChargeableDetailEntity cd = chargeableDetailRepository.findById(itemId).orElse(new ChargeableDetailEntity());
        cd.setItemId(itemId);
        if (dto.targetKind() != null) cd.setTargetKind(dto.targetKind());
        if (dto.targetItemId() != null) cd.setTargetItemId(dto.targetItemId());
        if (dto.tariffCode() != null) cd.setTariffCode(dto.tariffCode());
        if (dto.costMethodRef() != null) cd.setCostMethodRef(dto.costMethodRef());
        try {
            if (dto.pricingRefs() != null) cd.setPricingRefs(objectMapper.writeValueAsString(dto.pricingRefs()));
            if (dto.billableRules() != null) cd.setBillableRules(objectMapper.writeValueAsString(dto.billableRules()));
        } catch (Exception ignored) {}
        chargeableDetailRepository.save(cd);
    }

    private void emitOutboxEvent(String aggregateType, String aggregateId, String eventType, Object payload, TrustContext ctx) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setTenantId(ctx.tenantId());
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            event.setPayload("{}");
        }
        outboxRepository.save(event);
    }

    private Object parseJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception e) {
            return json;
        }
    }
}
