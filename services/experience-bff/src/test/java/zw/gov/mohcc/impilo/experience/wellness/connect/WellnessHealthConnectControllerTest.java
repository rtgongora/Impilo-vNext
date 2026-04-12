package zw.gov.mohcc.impilo.experience.wellness.connect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WellnessHealthConnectControllerTest {

    @Mock
    private WellnessHealthConnectIngestService ingestService;

    @Mock
    private WellnessHealthConnectQueryService queryService;

    @InjectMocks
    private WellnessHealthConnectController controller;

    @Test
    void manifestReturnsSupportedTypes() {
        ResponseEntity<Map<String, Object>> res = controller.manifest();
        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertTrue(data.containsKey("supportedRecordTypes"));
        assertTrue(((List<?>) data.get("supportedRecordTypes")).contains("Steps"));
    }

    @Test
    void ingestDelegatesToService() {
        when(ingestService.ingest(eq("tenant-moh-zw"), any(HealthConnectChangeSetRequest.class)))
                .thenReturn(new WellnessHealthConnectIngestService.IngestResult(2, 1, List.of()));

        HealthConnectChangeSetRequest req = new HealthConnectChangeSetRequest(
                "patient-1",
                new HealthConnectChangeSetRequest.DataOrigin("android_health_connect", "com.google.android.apps.healthdata", "1.0"),
                List.of("write.steps"),
                List.of());

        ResponseEntity<Map<String, Object>> res = controller.ingest("tenant-moh-zw", req);
        assertEquals(200, res.getStatusCode().value());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) res.getBody().get("data");
        assertEquals(2, data.get("applied"));
        assertEquals(1, data.get("skipped"));
        verify(ingestService).ingest(eq("tenant-moh-zw"), eq(req));
    }
}
