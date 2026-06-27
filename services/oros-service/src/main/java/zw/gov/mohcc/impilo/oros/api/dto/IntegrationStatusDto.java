package zw.gov.mohcc.impilo.oros.api.dto;

/**
 * Honest configured/not-configured status of an external integration adapter (brief §27 #11 —
 * never claim an integration that isn't actually wired and testable).
 *
 * @param adapter    stable adapter key (e.g. {@code FHIR_DIAGNOSTIC_REPORT_OUTBOUND})
 * @param category   FHIR / HL7 / DICOM / DIRECTORY
 * @param direction  INBOUND / OUTBOUND
 * @param configured whether a real endpoint/flag is set for this adapter
 * @param detail     human-readable status note (endpoint present, or why it is not configured)
 */
public record IntegrationStatusDto(
        String adapter,
        String category,
        String direction,
        boolean configured,
        String detail
) {}
