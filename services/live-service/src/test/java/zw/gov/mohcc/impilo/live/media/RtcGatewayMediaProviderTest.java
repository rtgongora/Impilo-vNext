package zw.gov.mohcc.impilo.live.media;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.live.integration.DocumentServiceClient;
import zw.gov.mohcc.impilo.live.integration.RtcGatewayClient;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * W1: the recording methods are real rtc-gateway calls (no fabricated refs) and
 * playback resolves the catalogued document object to a signed URL.
 */
@ExtendWith(MockitoExtension.class)
class RtcGatewayMediaProviderTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String ROOM_ID = "rtc-session-1";

    @Mock private RtcGatewayClient rtcGatewayClient;
    @Mock private DocumentServiceClient documentServiceClient;

    private RtcGatewayMediaProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RtcGatewayMediaProvider(rtcGatewayClient, documentServiceClient);
        TrustContextHolder.set(new TrustContext(TENANT_ID, "provider-77", "PROVIDER", null,
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void startRecording_callsRecordingStartWithActorContext_andReturnsRecordingId() {
        when(rtcGatewayClient.startRecording(any(), eq(ROOM_ID), any()))
                .thenReturn(Map.of("data", Map.of(
                        "recordingId", "rec-123", "egressId", "egress-9", "status", "ACTIVE")));

        String ref = provider.startRecording(ROOM_ID);

        assertThat(ref).isEqualTo("rec-123");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> request = ArgumentCaptor.forClass(Map.class);
        verify(rtcGatewayClient).startRecording(any(), eq(ROOM_ID), request.capture());
        assertThat(request.getValue())
                .containsEntry("startedBy", "provider-77")
                .containsEntry("startedByRole", "PROVIDER");
    }

    @Test
    void startRecording_fallsBackToEgressIdWhenNoRecordingId() {
        when(rtcGatewayClient.startRecording(any(), eq(ROOM_ID), any()))
                .thenReturn(Map.of("data", Map.of("egressId", "egress-9", "status", "ACTIVE")));

        assertThat(provider.startRecording(ROOM_ID)).isEqualTo("egress-9");
    }

    @Test
    void stopRecording_callsRecordingStopEndpoint() {
        provider.stopRecording(ROOM_ID, "rec-123");

        verify(rtcGatewayClient).stopRecording(any(), eq(ROOM_ID));
        verifyNoInteractions(documentServiceClient);
    }

    @Test
    void getPlaybackUrl_resolvesDocumentObjectRefToSignedUrl() {
        UUID documentObjectId = UUID.randomUUID();
        when(documentServiceClient.getSignedUrl(TENANT_ID, documentObjectId.toString()))
                .thenReturn(Map.of("data", Map.of(
                        "url", "https://minio.local/signed/recording.mp4",
                        "token", "abc", "expiresAt", "2026-07-04T12:00:00Z")));

        String url = provider.getPlaybackUrl(ROOM_ID, documentObjectId.toString());

        assertThat(url).isEqualTo("https://minio.local/signed/recording.mp4");
    }

    @Test
    void getPlaybackUrl_nonDocumentRef_returnsNullWithoutCallingDocumentService() {
        assertThat(provider.getPlaybackUrl(ROOM_ID, "auto-recording-xyz")).isNull();
        assertThat(provider.getPlaybackUrl(ROOM_ID, null)).isNull();
        verifyNoInteractions(documentServiceClient);
    }
}
