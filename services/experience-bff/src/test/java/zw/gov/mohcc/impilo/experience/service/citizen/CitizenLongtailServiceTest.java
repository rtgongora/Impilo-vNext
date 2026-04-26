package zw.gov.mohcc.impilo.experience.service.citizen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.CredentialServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.config.BffCitizenLongtailProperties;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CitizenLongtailServiceTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private BffCitizenLongtailProperties properties;

    @Mock
    private VitoServiceClient vitoServiceClient;

    @Mock
    private CredentialServiceClient credentialServiceClient;

    @InjectMocks
    private CitizenLongtailService service;

    @Test
    void issueShare_stubMode_returnsSyntheticCode() {
        when(properties.getMode()).thenReturn(BffCitizenLongtailProperties.Mode.stub);
        Map<String, Object> data = service.issueShareData(null, new CitizenLongtailDtos.IssueShareCodeRequest("care"));
        assertTrue(String.valueOf(data.get("code")).startsWith("SHARE-"));
        assertEquals("care", data.get("purpose"));
    }

    @Test
    void issueShare_liveMode_mapsVitoShareToken() throws Exception {
        when(properties.getMode()).thenReturn(BffCitizenLongtailProperties.Mode.live);
        UUID hid = UUID.randomUUID();
        JsonNode vitoData = MAPPER.readTree(
                "{\"shareRequestId\":1,\"grantId\":2,\"shareToken\":\"tok-abc\",\"otp\":\"000000\","
                        + "\"expiresAt\":\"2030-01-01T00:00:00Z\",\"allowedActions\":\"READ\"}");
        when(vitoServiceClient.createPatientShare(eq(hid), any())).thenReturn(vitoData);

        Map<String, Object> data = service.issueShareData(
                hid.toString(),
                new CitizenLongtailDtos.IssueShareCodeRequest("TREATMENT"));

        assertEquals("tok-abc", data.get("code"));
        assertEquals("TREATMENT", data.get("purpose"));
    }

    @Test
    void issueShare_liveMode_missingActor_throws() {
        when(properties.getMode()).thenReturn(BffCitizenLongtailProperties.Mode.live);
        assertThrows(IllegalArgumentException.class, () ->
                service.issueShareData(null, new CitizenLongtailDtos.IssueShareCodeRequest(null)));
    }

    @Test
    void issueShare_live_propagateOnDownstream_throws502() {
        when(properties.getMode()).thenReturn(BffCitizenLongtailProperties.Mode.live);
        when(properties.getFailurePolicy()).thenReturn(BffCitizenLongtailProperties.FailurePolicy.propagate);
        when(vitoServiceClient.createPatientShare(any(), any()))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThrows(ResponseStatusException.class, () ->
                service.issueShareData(
                        UUID.randomUUID().toString(),
                        new CitizenLongtailDtos.IssueShareCodeRequest(null)));
    }

    @Test
    void issueShare_live_stubFallbackOnDownstream_returnsStub() {
        when(properties.getMode()).thenReturn(BffCitizenLongtailProperties.Mode.live);
        when(properties.getFailurePolicy()).thenReturn(BffCitizenLongtailProperties.FailurePolicy.stub_fallback);
        when(vitoServiceClient.createPatientShare(any(), any()))
                .thenThrow(new ResourceAccessException("connection refused"));

        Map<String, Object> data = service.issueShareData(
                UUID.randomUUID().toString(),
                new CitizenLongtailDtos.IssueShareCodeRequest("x"));

        assertTrue(String.valueOf(data.get("code")).startsWith("SHARE-"));
    }

    @Test
    void verifyCredential_live_mapsValidStatus() throws Exception {
        when(properties.getMode()).thenReturn(BffCitizenLongtailProperties.Mode.live);
        JsonNode cred = MAPPER.readTree("{\"status\":\"VALID\",\"credentialType\":\"PRO\"}");
        when(credentialServiceClient.publicVerifyByToken("abc")).thenReturn(cred);

        Map<String, Object> data = service.verifyCredentialData(new CitizenLongtailDtos.VerifyCredentialRequest("abc"));
        assertEquals(true, data.get("verified"));
    }
}
