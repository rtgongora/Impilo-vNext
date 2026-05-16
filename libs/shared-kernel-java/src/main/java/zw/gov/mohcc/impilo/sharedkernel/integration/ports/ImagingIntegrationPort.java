package zw.gov.mohcc.impilo.sharedkernel.integration.ports;

import zw.gov.mohcc.impilo.sharedkernel.integration.ports.CanonicalReferences.*;

import java.util.List;

/**
 * Canonical imaging port. Adapters: DicomWebPacsAdapter, Hl7RisAdapter,
 * VendorSpecificPacsAdapter, NativeImagingAdapter.
 */
public interface ImagingIntegrationPort extends Adapter {

    OperationResult<ImagingOrderAck>     submitImagingOrder(ImagingOrder order, OperationContext ctx);
    OperationResult<List<ImagingStudy>>  listStudiesForPatient(PatientReference patient, OperationContext ctx);
    OperationResult<ImagingReport>       fetchReport(OrderReference order, OperationContext ctx);
    OperationResult<String>              launchViewerUrl(OrderReference order, OperationContext ctx);

    record ImagingOrder(OrderReference order, PatientReference patient, String modality, String bodyPart, String clinicalIndication) {}
    record ImagingOrderAck(String externalAccessionNumber, String status) {}
    record ImagingStudy(String studyInstanceUid, String modality, String performedAt, String description) {}
    record ImagingReport(String reportId, String status, String narrative, String reportedAt, String reportingProvider) {}
}
