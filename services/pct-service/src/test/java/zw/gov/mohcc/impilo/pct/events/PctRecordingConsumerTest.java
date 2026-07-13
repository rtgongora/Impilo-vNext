package zw.gov.mohcc.impilo.pct.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.pct.core.TelemedicineOrchestrationService;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class PctRecordingConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void attributes_a_pct_recording_to_its_referral_via_owningRef() {
        TelemedicineOrchestrationService svc = mock(TelemedicineOrchestrationService.class);
        PctRecordingConsumer consumer = new PctRecordingConsumer(svc, mapper);

        String event = """
            {"eventType":"impilo.rtc.recording.available.v1","tenantId":"t-1",
             "payload":{"owningService":"PCT","owningRef":"ref-9","sessionId":"sess-1",
                        "egressId":"egr-5","storageKey":"s3://b/rec.mp4"}}""";
        consumer.onRecordingAvailable(event);

        verify(svc).attachRecording(eq("ref-9"), eq("egr-5"), eq("s3://b/rec.mp4"));
    }

    @Test
    void ignores_recordings_owned_by_other_services() {
        TelemedicineOrchestrationService svc = mock(TelemedicineOrchestrationService.class);
        PctRecordingConsumer consumer = new PctRecordingConsumer(svc, mapper);

        String event = """
            {"payload":{"owningService":"LIVE","owningRef":"evt-1","sessionId":"s"}}""";
        consumer.onRecordingAvailable(event);

        verify(svc, never()).attachRecording(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void poison_message_does_not_throw() {
        TelemedicineOrchestrationService svc = mock(TelemedicineOrchestrationService.class);
        PctRecordingConsumer consumer = new PctRecordingConsumer(svc, mapper);
        consumer.onRecordingAvailable("not json");
        verify(svc, never()).attachRecording(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
