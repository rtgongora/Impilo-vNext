package zw.gov.mohcc.impilo.pharmacy.api.dto;

/**
 * DTO representing the current stock position for an item at a store.
 *
 * @param itemCode       the drug/item code
 * @param storeId        the store identifier
 * @param currentBalance the current stock balance (sum of all movements)
 */
public record StockPositionDto(
        String itemCode,
        String storeId,
        int currentBalance
) {}
