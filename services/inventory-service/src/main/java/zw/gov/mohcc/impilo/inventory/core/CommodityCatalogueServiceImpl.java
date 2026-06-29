package zw.gov.mohcc.impilo.inventory.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.inventory.persistence.entity.CommodityCategoryEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;
import zw.gov.mohcc.impilo.inventory.persistence.repository.CommodityCategoryRepository;
import zw.gov.mohcc.impilo.inventory.persistence.repository.ItemRepository;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.UUID;

/**
 * Implementation of the Dura commodity catalogue service.
 */
@Service
public class CommodityCatalogueServiceImpl implements CommodityCatalogueService {

    private static final Logger log = LoggerFactory.getLogger(CommodityCatalogueServiceImpl.class);

    private final CommodityCategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    public CommodityCatalogueServiceImpl(CommodityCategoryRepository categoryRepository,
                                         ItemRepository itemRepository) {
        this.categoryRepository = categoryRepository;
        this.itemRepository = itemRepository;
    }

    @Override
    @Transactional
    public CommodityCategoryEntity createCategory(String code, String name, UUID parentCategoryId,
                                                  String programmeArea, String description) {
        TrustContext ctx = TrustContextHolder.require();

        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("category code is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("category name is required");
        }
        if (categoryRepository.existsByTenantIdAndCode(ctx.tenantId(), code)) {
            throw new IllegalArgumentException("Category already exists with code: " + code);
        }
        if (parentCategoryId != null) {
            CommodityCategoryEntity parent = categoryRepository.findById(parentCategoryId)
                    .orElseThrow(() -> new IllegalArgumentException("Parent category not found: " + parentCategoryId));
            requireSameTenant(parent.getTenantId(), ctx.tenantId());
        }

        CommodityCategoryEntity category = new CommodityCategoryEntity();
        category.setTenantId(ctx.tenantId());
        category.setCode(code);
        category.setName(name);
        category.setParentCategoryId(parentCategoryId);
        category.setProgrammeArea(programmeArea);
        category.setDescription(description);

        category = categoryRepository.save(category);
        log.info("Commodity category created: code={}, name={}, programmeArea={}", code, name, programmeArea);
        return category;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommodityCategoryEntity> listCategories(String programmeArea) {
        TrustContext ctx = TrustContextHolder.require();
        if (programmeArea != null && !programmeArea.isBlank()) {
            return categoryRepository.findByTenantIdAndProgrammeAreaOrderByName(ctx.tenantId(), programmeArea);
        }
        return categoryRepository.findByTenantIdOrderByName(ctx.tenantId());
    }

    @Override
    @Transactional(readOnly = true)
    public CommodityCategoryEntity getCategory(UUID categoryId) {
        TrustContext ctx = TrustContextHolder.require();
        CommodityCategoryEntity category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found: " + categoryId));
        requireSameTenant(category.getTenantId(), ctx.tenantId());
        return category;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ItemEntity> searchCommodities(String q, UUID categoryId, String programmeArea,
                                              Boolean controlled, Boolean coldChain) {
        TrustContext ctx = TrustContextHolder.require();
        String query = (q == null || q.isBlank()) ? null : q.trim();
        String programme = (programmeArea == null || programmeArea.isBlank()) ? null : programmeArea;
        return itemRepository.searchCommodities(ctx.tenantId(), query, categoryId, programme, controlled, coldChain);
    }

    @Override
    @Transactional
    public ItemEntity updateCommodityAttributes(String itemCode, CommodityAttributes attrs) {
        TrustContext ctx = TrustContextHolder.require();
        if (itemCode == null || itemCode.isBlank()) {
            throw new IllegalArgumentException("itemCode is required");
        }
        if (attrs == null) {
            throw new IllegalArgumentException("attributes are required");
        }

        ItemEntity item = itemRepository.findByTenantIdAndItemCode(ctx.tenantId(), itemCode)
                .orElseThrow(() -> new IllegalArgumentException("Commodity not found: " + itemCode));

        if (attrs.categoryId() != null) {
            CommodityCategoryEntity category = categoryRepository.findById(attrs.categoryId())
                    .orElseThrow(() -> new IllegalArgumentException("Category not found: " + attrs.categoryId()));
            requireSameTenant(category.getTenantId(), ctx.tenantId());
            item.setCategoryId(attrs.categoryId());
        }
        if (attrs.subcategory() != null) item.setSubcategory(attrs.subcategory());
        if (attrs.genericName() != null) item.setGenericName(attrs.genericName());
        if (attrs.brandName() != null) item.setBrandName(attrs.brandName());
        if (attrs.form() != null) item.setForm(attrs.form());
        if (attrs.strength() != null) item.setStrength(attrs.strength());
        if (attrs.packSize() != null) item.setPackSize(attrs.packSize());
        if (attrs.stockingUnit() != null) item.setStockingUnit(attrs.stockingUnit());
        if (attrs.dispensingUnit() != null) item.setDispensingUnit(attrs.dispensingUnit());
        if (attrs.programmeArea() != null) item.setProgrammeArea(attrs.programmeArea());
        if (attrs.coldChainRequired() != null) item.setColdChainRequired(attrs.coldChainRequired());
        if (attrs.temperatureMin() != null) item.setTemperatureMin(attrs.temperatureMin());
        if (attrs.temperatureMax() != null) item.setTemperatureMax(attrs.temperatureMax());
        if (attrs.batchTrackingRequired() != null) item.setBatchTrackingRequired(attrs.batchTrackingRequired());
        if (attrs.expiryTrackingRequired() != null) item.setExpiryTrackingRequired(attrs.expiryTrackingRequired());
        if (attrs.serialTrackingRequired() != null) item.setSerialTrackingRequired(attrs.serialTrackingRequired());
        if (attrs.substitutionGroup() != null) item.setSubstitutionGroup(attrs.substitutionGroup());
        if (attrs.essentialMedicine() != null) item.setEssentialMedicine(attrs.essentialMedicine());
        if (attrs.regulatoryStatus() != null) item.setRegulatoryStatus(attrs.regulatoryStatus());
        if (attrs.gtin() != null) item.setGtin(attrs.gtin());
        if (attrs.externalCodes() != null) item.setExternalCodes(attrs.externalCodes());

        item = itemRepository.save(item);
        log.info("Commodity attributes updated: itemCode={}", itemCode);
        return item;
    }

    private void requireSameTenant(UUID resourceTenant, UUID ctxTenant) {
        if (resourceTenant == null || !resourceTenant.equals(ctxTenant)) {
            throw new IllegalArgumentException("Resource does not belong to the current tenant");
        }
    }
}
