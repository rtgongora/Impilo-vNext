package zw.gov.mohcc.impilo.oros.core;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.api.dto.IntegrationStatusDto;

import java.util.List;

/**
 * Reports the honest configured/not-configured state of each external integration adapter the
 * diagnostics journey can use (brief §27 #11). An adapter is {@code configured} only when a real
 * endpoint URL is present or its feature flag is explicitly enabled — otherwise it is reported as
 * NOT configured rather than silently implied. Backs the admin integration-status screen and the
 * final-report honesty requirement (§28).
 *
 * <p>Built-in, already-wired integrations (Butano FHIR DiagnosticReport writeback, VARAPI provider
 * directory) report configured when their base URLs are set. The FHIR-inbound / HL7 / DICOM-MWL
 * adapters are contract seams that are NOT implemented and report not-configured unless explicitly
 * flagged in a deployment that has wired them.</p>
 */
@Service
public class IntegrationStatusService {

    private final String butanoBaseUrl;
    private final String varapiBaseUrl;
    private final String pacsDicomwebBaseUrl;
    private final boolean fhirServiceRequestInbound;
    private final boolean fhirImagingStudyOutbound;
    private final boolean hl7OrmInbound;
    private final boolean hl7OruOutbound;
    private final boolean dicomMwlOutbound;

    public IntegrationStatusService(
            @Value("${oros.integration.butano.base-url:}") String butanoBaseUrl,
            @Value("${oros.integration.varapi.base-url:}") String varapiBaseUrl,
            @Value("${oros.integration.pacs.dicomweb.base-url:}") String pacsDicomwebBaseUrl,
            @Value("${oros.integration.fhir.servicerequest-inbound.enabled:false}") boolean fhirServiceRequestInbound,
            @Value("${oros.integration.fhir.imagingstudy-outbound.enabled:false}") boolean fhirImagingStudyOutbound,
            @Value("${oros.integration.hl7.orm-inbound.enabled:false}") boolean hl7OrmInbound,
            @Value("${oros.integration.hl7.oru-outbound.enabled:false}") boolean hl7OruOutbound,
            @Value("${oros.integration.dicom.mwl-outbound.enabled:false}") boolean dicomMwlOutbound) {
        this.butanoBaseUrl = trim(butanoBaseUrl);
        this.varapiBaseUrl = trim(varapiBaseUrl);
        this.pacsDicomwebBaseUrl = trim(pacsDicomwebBaseUrl);
        this.fhirServiceRequestInbound = fhirServiceRequestInbound;
        this.fhirImagingStudyOutbound = fhirImagingStudyOutbound;
        this.hl7OrmInbound = hl7OrmInbound;
        this.hl7OruOutbound = hl7OruOutbound;
        this.dicomMwlOutbound = dicomMwlOutbound;
    }

    public List<IntegrationStatusDto> statuses() {
        return List.of(
                urlAdapter("FHIR_DIAGNOSTIC_REPORT_OUTBOUND", "FHIR", "OUTBOUND", butanoBaseUrl,
                        "Butano SHR DiagnosticReport writeback"),
                urlAdapter("PROVIDER_DIRECTORY", "DIRECTORY", "OUTBOUND", varapiBaseUrl,
                        "VARAPI provider directory lookup"),
                urlAdapter("PACS_DICOMWEB", "DICOM", "OUTBOUND", pacsDicomwebBaseUrl,
                        "External PACS DICOMweb (QIDO/WADO/STOW)"),
                flagAdapter("FHIR_SERVICE_REQUEST_INBOUND", "FHIR", "INBOUND", fhirServiceRequestInbound,
                        "FHIR ServiceRequest inbound adapter"),
                flagAdapter("FHIR_IMAGING_STUDY_OUTBOUND", "FHIR", "OUTBOUND", fhirImagingStudyOutbound,
                        "FHIR ImagingStudy outbound adapter"),
                flagAdapter("HL7_ORM_INBOUND", "HL7", "INBOUND", hl7OrmInbound,
                        "HL7 v2 ORM inbound adapter"),
                flagAdapter("HL7_ORU_OUTBOUND", "HL7", "OUTBOUND", hl7OruOutbound,
                        "HL7 v2 ORU outbound adapter"),
                flagAdapter("DICOM_MWL_OUTBOUND", "DICOM", "OUTBOUND", dicomMwlOutbound,
                        "DICOM Modality Worklist outbound adapter"));
    }

    private IntegrationStatusDto urlAdapter(String adapter, String category, String direction,
                                            String url, String label) {
        boolean configured = !url.isBlank();
        String detail = configured ? label + " — endpoint configured"
                : label + " — no endpoint configured (NOT_LIVE)";
        return new IntegrationStatusDto(adapter, category, direction, configured, detail);
    }

    private IntegrationStatusDto flagAdapter(String adapter, String category, String direction,
                                             boolean enabled, String label) {
        String detail = enabled ? label + " — enabled"
                : label + " — contract seam only, not implemented (NOT_LIVE)";
        return new IntegrationStatusDto(adapter, category, direction, enabled, detail);
    }

    private static String trim(String s) {
        return s == null ? "" : s.trim();
    }
}
