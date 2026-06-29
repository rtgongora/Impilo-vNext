package zw.gov.mohcc.impilo.inventory.core;

import zw.gov.mohcc.impilo.inventory.persistence.entity.CommodityCategoryEntity;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;

import java.util.List;
import java.util.UUID;

/**
 * Dura commodity catalogue service.
 *
 * <p>Manages commodity categories and the Dura enrichment attributes on
 * commodities (pack/dispensing units, programme area, cold-chain, tracking
 * requirements, regulatory status, GTIN, external code mappings). Commodity
 * creation itself remains with {@link ItemService}; this service owns
 * classification and the stock-aware attributes that drive Dura decisions.</p>
 *
 * <p>All operations are tenant-scoped via the trust context.</p>
 */
public interface CommodityCatalogueService {

    /**
     * Create a commodity category. Fails if a category with the same code
     * already exists for the tenant.
     */
    CommodityCategoryEntity createCategory(String code, String name, UUID parentCategoryId,
                                           String programmeArea, String description);

    /**
     * List categories for the current tenant, optionally filtered by programme area.
     *
     * @param programmeArea optional programme filter ({@code null} = all)
     */
    List<CommodityCategoryEntity> listCategories(String programmeArea);

    /**
     * Get a single category by id (tenant-checked).
     */
    CommodityCategoryEntity getCategory(UUID categoryId);

    /**
     * Search the commodity catalogue. All filters are optional ({@code null} ignored).
     */
    List<ItemEntity> searchCommodities(String q, UUID categoryId, String programmeArea,
                                       Boolean controlled, Boolean coldChain);

    /**
     * Apply Dura enrichment attributes to an existing commodity (by item code).
     * Only non-null fields in {@code attrs} are applied.
     */
    ItemEntity updateCommodityAttributes(String itemCode, CommodityAttributes attrs);

    /**
     * Carrier for Dura commodity enrichment. Null fields are left unchanged.
     */
    record CommodityAttributes(
            UUID categoryId,
            String subcategory,
            String genericName,
            String brandName,
            String form,
            String strength,
            String packSize,
            String stockingUnit,
            String dispensingUnit,
            String programmeArea,
            Boolean coldChainRequired,
            Double temperatureMin,
            Double temperatureMax,
            Boolean batchTrackingRequired,
            Boolean expiryTrackingRequired,
            Boolean serialTrackingRequired,
            String substitutionGroup,
            Boolean essentialMedicine,
            String regulatoryStatus,
            String gtin,
            String externalCodes
    ) {}
}
