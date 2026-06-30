package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.core.CommodityCatalogueService.CommodityAttributes;

import java.util.UUID;

/**
 * Request to apply Dura enrichment attributes to a commodity.
 * Null fields are left unchanged.
 */
public record UpdateCommodityAttributesRequest(
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
) {
    public CommodityAttributes toAttributes() {
        return new CommodityAttributes(
                categoryId, subcategory, genericName, brandName, form, strength, packSize,
                stockingUnit, dispensingUnit, programmeArea, coldChainRequired, temperatureMin,
                temperatureMax, batchTrackingRequired, expiryTrackingRequired, serialTrackingRequired,
                substitutionGroup, essentialMedicine, regulatoryStatus, gtin, externalCodes);
    }
}
