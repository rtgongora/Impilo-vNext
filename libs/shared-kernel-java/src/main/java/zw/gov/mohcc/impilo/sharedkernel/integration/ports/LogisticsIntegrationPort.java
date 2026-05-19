package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.util.List;

/**
 * Canonical logistics port. Adapters: ExternalElmisAdapter, StockEventAdapter,
 * NativeLogisticsAdapter.
 */
public interface LogisticsIntegrationPort extends Adapter {

    OperationResult<List<StockItem>>     listStock(FacilityReference facility, OperationContext ctx);
    OperationResult<StockMovementAck>    recordMovement(StockMovement movement, OperationContext ctx);
    OperationResult<List<Requisition>>   listRequisitions(FacilityReference facility, OperationContext ctx);

    record StockItem(String itemCode, String description, String unitOfMeasure, double quantityOnHand, String expiryDate, String batchNumber) {}
    record StockMovement(FacilityReference facility, String itemCode, String movementType, double quantity, String batchNumber, String reason) {}
    record StockMovementAck(String movementId, String status) {}
    record Requisition(String requisitionId, FacilityReference facility, String status, List<RequisitionLine> lines) {}
    record RequisitionLine(String itemCode, double quantityRequested, double quantityApproved) {}
}
