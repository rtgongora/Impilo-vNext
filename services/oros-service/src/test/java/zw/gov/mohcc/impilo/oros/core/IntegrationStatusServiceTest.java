package zw.gov.mohcc.impilo.oros.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.oros.api.dto.IntegrationStatusDto;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link IntegrationStatusService} — honest configured/not-configured reporting for
 * both branches (endpoint present vs. contract-seam-only).
 */
class IntegrationStatusServiceTest {

    private Map<String, IntegrationStatusDto> byAdapter(IntegrationStatusService service) {
        return service.statuses().stream()
                .collect(Collectors.toMap(IntegrationStatusDto::adapter, Function.identity()));
    }

    @Test
    @DisplayName("URL-backed adapters are configured when an endpoint is present, not otherwise")
    void urlAdaptersReflectEndpoint() {
        IntegrationStatusService configured = new IntegrationStatusService(
                "http://localhost:8090", "http://localhost:8083", "http://pacs:8042",
                false, false, false, false, "OFF");
        Map<String, IntegrationStatusDto> map = byAdapter(configured);
        assertThat(map.get("FHIR_DIAGNOSTIC_REPORT_OUTBOUND").configured()).isTrue();
        assertThat(map.get("PROVIDER_DIRECTORY").configured()).isTrue();
        assertThat(map.get("PACS_DICOMWEB").configured()).isTrue();

        IntegrationStatusService blank = new IntegrationStatusService(
                "  ", "", "", false, false, false, false, "OFF");
        Map<String, IntegrationStatusDto> blankMap = byAdapter(blank);
        assertThat(blankMap.get("FHIR_DIAGNOSTIC_REPORT_OUTBOUND").configured()).isFalse();
        assertThat(blankMap.get("PACS_DICOMWEB").detail()).contains("NOT_LIVE");
    }

    @Test
    @DisplayName("flag-only adapters report not-configured by default (contract seams)")
    void flagAdaptersDefaultNotConfigured() {
        IntegrationStatusService service = new IntegrationStatusService(
                "", "", "", false, false, false, false, "OFF");
        Map<String, IntegrationStatusDto> map = byAdapter(service);

        List<String> seams = List.of("FHIR_SERVICE_REQUEST_INBOUND", "FHIR_IMAGING_STUDY_OUTBOUND",
                "HL7_ORM_INBOUND", "HL7_ORU_OUTBOUND", "DICOM_MWL_OUTBOUND");
        for (String seam : seams) {
            assertThat(map.get(seam).configured()).as(seam).isFalse();
            assertThat(map.get(seam).detail()).contains("NOT_LIVE");
        }
    }

    @Test
    @DisplayName("an explicitly-enabled flag adapter reports configured")
    void flagAdapterEnabled() {
        IntegrationStatusService service = new IntegrationStatusService(
                "", "", "", true, false, false, false, "OFF");
        assertThat(byAdapter(service).get("FHIR_SERVICE_REQUEST_INBOUND").configured()).isTrue();
    }

    @Test
    @DisplayName("DICOM MWL reports configured when the mode is not OFF")
    void dicomMwlModeReflectsConfig() {
        IntegrationStatusService rest = new IntegrationStatusService(
                "", "", "", false, false, false, false, "REST");
        assertThat(byAdapter(rest).get("DICOM_MWL_OUTBOUND").configured()).isTrue();
        assertThat(byAdapter(rest).get("DICOM_MWL_OUTBOUND").detail()).contains("REST");
    }
}
