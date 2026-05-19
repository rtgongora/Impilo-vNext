package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.util.List;

/**
 * Canonical laboratory port. Adapters: ExternalLimsAdapter, Hl7v2LimsAdapter,
 * FhirLimsAdapter, NativeLabAdapter.
 */
public interface LaboratoryIntegrationPort extends Adapter {

    OperationResult<LabOrderAck>      submitOrder(LabOrder order, OperationContext ctx);
    OperationResult<List<LabResult>>  listResultsForPatient(PatientReference patient, OperationContext ctx);
    OperationResult<LabResult>        fetchResult(OrderReference order, OperationContext ctx);
    OperationResult<Void>             cancelOrder(OrderReference order, String reason, OperationContext ctx);

    record LabOrder(OrderReference order, PatientReference patient, String panelCode, String specimenType, String clinicalQuestion) {}
    record LabOrderAck(String externalOrderId, String status) {}
    record LabResult(String resultId, String orderId, String analyteCode, String value, String unit, String referenceRange,
                     String status, String reportedAt, String labReference) {}
}
