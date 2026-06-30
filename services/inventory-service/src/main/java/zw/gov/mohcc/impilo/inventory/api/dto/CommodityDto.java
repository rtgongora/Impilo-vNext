package zw.gov.mohcc.impilo.inventory.api.dto;

import zw.gov.mohcc.impilo.inventory.persistence.entity.ItemEntity;

import java.util.UUID;

/**
 * Dura commodity DTO — the catalogue view enriched with the stock-aware
 * attributes Dura uses (pack/dispensing units, programme area, cold-chain,
 * tracking requirements, regulatory status, GTIN, external code mappings).
 */
public record CommodityDto(
        UUID itemId,
        String itemCode,
        String name,
        String genericName,
        String brandName,
        String uom,
        String form,
        String strength,
        String packSize,
        String stockingUnit,
        String dispensingUnit,
        String barcode,
        String gtin,
        String category,
        UUID categoryId,
        String subcategory,
        String programmeArea,
        boolean controlled,
        boolean coldChainRequired,
        Double temperatureMin,
        Double temperatureMax,
        boolean batchTrackingRequired,
        boolean expiryTrackingRequired,
        boolean serialTrackingRequired,
        String substitutionGroup,
        boolean essentialMedicine,
        String regulatoryStatus,
        String externalCodes,
        boolean active
) {
    public static CommodityDto from(ItemEntity e) {
        return new CommodityDto(
                e.getItemId(),
                e.getItemCode(),
                e.getName(),
                e.getGenericName(),
                e.getBrandName(),
                e.getUom(),
                e.getForm(),
                e.getStrength(),
                e.getPackSize(),
                e.getStockingUnit(),
                e.getDispensingUnit(),
                e.getBarcode(),
                e.getGtin(),
                e.getCategory(),
                e.getCategoryId(),
                e.getSubcategory(),
                e.getProgrammeArea(),
                e.isControlled(),
                e.isColdChainRequired(),
                e.getTemperatureMin(),
                e.getTemperatureMax(),
                e.isBatchTrackingRequired(),
                e.isExpiryTrackingRequired(),
                e.isSerialTrackingRequired(),
                e.getSubstitutionGroup(),
                e.isEssentialMedicine(),
                e.getRegulatoryStatus(),
                e.getExternalCodes(),
                e.isActive()
        );
    }
}
