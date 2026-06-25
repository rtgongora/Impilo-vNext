package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.client.TshepoAuthzServiceClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CitizenStepUpControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private TshepoAuthzServiceClient authzClient;

    private CitizenStepUpController controller() {
        return new CitizenStepUpController(authzClient);
    }

    @Test
    void challenge_bindsActorAndTenantFromHeaders_ignoringBody() throws Exception {
        when(authzClient.issueStepUpChallenge(any()))
                .thenReturn(MAPPER.readTree("{\"challengeId\":\"c1\",\"status\":\"PENDING\"}"));

        ResponseEntity<Map<String, Object>> resp = controller().challenge(
                "tenant-real", "actor-real", "req-1", "corr-1",
                Map.of("challengeType", "TOTP", "actorId", "actor-EVIL", "tenantId", "tenant-EVIL"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(authzClient).issueStepUpChallenge(captor.capture());
        Map<String, Object> sent = captor.getValue();
        assertEquals("actor-real", sent.get("actorId"), "actorId must come from X-Actor-ID, not the body");
        assertEquals("tenant-real", sent.get("tenantId"), "tenantId must come from X-Tenant-ID, not the body");
        assertEquals("TOTP", sent.get("challengeType"));
    }

    @Test
    void verify_bindsActorAndTenantFromHeaders() throws Exception {
        when(authzClient.verifyStepUpChallenge(any()))
                .thenReturn(MAPPER.readTree("{\"challengeId\":\"c1\",\"status\":\"COMPLETED\"}"));

        ResponseEntity<Map<String, Object>> resp = controller().verify(
                "tenant-real", "actor-real", "req-1", "corr-1",
                Map.of("challengeId", "c1", "verificationCode", "123456", "actorId", "actor-EVIL"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(authzClient).verifyStepUpChallenge(captor.capture());
        Map<String, Object> sent = captor.getValue();
        assertEquals("actor-real", sent.get("actorId"));
        assertEquals("tenant-real", sent.get("tenantId"));
        assertEquals("c1", sent.get("challengeId"));
        assertEquals("123456", sent.get("verificationCode"));
    }

    @Test
    void verify_invalidCode_passesThroughUpstream400() {
        when(authzClient.verifyStepUpChallenge(any()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.BAD_REQUEST, "Bad Request", null, null, null));

        ResponseEntity<Map<String, Object>> resp = controller().verify(
                "tenant-real", "actor-real", "req-1", "corr-1",
                Map.of("challengeId", "c1", "verificationCode", "wrong"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertNotNull(resp.getBody());
        assertTrue(resp.getBody().containsKey("error"));
    }
}
