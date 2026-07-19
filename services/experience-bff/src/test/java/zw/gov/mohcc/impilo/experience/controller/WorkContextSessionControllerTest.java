package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * D-P3 / PJ5: the work-session lane proves the requested context against
 * ACTIVE Vashandi assignments BEFORE minting; unproven contexts and unlinked
 * persons get one generic denial (no context enumeration); a switch forwards
 * previousJti so the old token is revoked at issuance.
 */
@ExtendWith(MockitoExtension.class)
class WorkContextSessionControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock private VashandiServiceClient vashandiClient;
    @Mock private VarapiServiceClient varapiClient;
    @Mock private TshepoIdentityServiceClient tshepoIdentityClient;

    private WorkContextController controller;

    private final String tenantId = UUID.randomUUID().toString();
    private final String actorId = UUID.randomUUID().toString();
    private final String facilityId = UUID.randomUUID().toString();
    private final String workspaceId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        controller = new WorkContextController(vashandiClient, varapiClient, tshepoIdentityClient);
    }

    private JsonNode provider() throws Exception {
        return MAPPER.readTree("{\"providerPublicId\":\"PROVPUB0000000000000000001\"}");
    }

    private JsonNode workContextWithAssignment() throws Exception {
        return MAPPER.readTree(String.format(
                "{\"resolved\":true,\"activeAssignments\":[{\"facilityId\":\"%s\","
                        + "\"workspaceId\":\"%s\",\"roleTemplateId\":\"NURSE_GENERAL\","
                        + "\"departmentId\":\"dep-1\"}]}",
                facilityId, workspaceId));
    }

    private ResponseEntity<Map<String, Object>> start(Map<String, Object> body) {
        return controller.startWorkSession(tenantId, "req-1", "corr-1", actorId, body);
    }

    @Test
    void provenAssignmentMintsWorkToken() throws Exception {
        when(varapiClient.getProviderByHealthId(actorId)).thenReturn(provider());
        when(vashandiClient.fetchWorkContext(actorId)).thenReturn(workContextWithAssignment());
        when(tshepoIdentityClient.issueWorkContextToken(any())).thenReturn(MAPPER.readTree(
                "{\"data\":{\"token\":\"jws\",\"jti\":\"jti-1\",\"expiresAt\":\"2026-07-19T12:00:00Z\"}}"));

        ResponseEntity<Map<String, Object>> response = start(Map.of(
                "facilityId", facilityId, "workspaceId", workspaceId, "previousJti", "old-jti"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) data.get("attributes");
        assertThat(attrs.get("jti")).isEqualTo("jti-1");
        assertThat(attrs.get("roleTemplateId")).isEqualTo("NURSE_GENERAL");

        var captor = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(tshepoIdentityClient).issueWorkContextToken(captor.capture());
        assertThat(captor.getValue().get("previousJti")).isEqualTo("old-jti");
        assertThat(captor.getValue().get("providerPublicId")).isEqualTo("PROVPUB0000000000000000001");
        assertThat(captor.getValue().get("departmentId")).isEqualTo("dep-1");
    }

    @Test
    void unassignedFacilityGetsGenericDenialAndNoToken() throws Exception {
        when(varapiClient.getProviderByHealthId(actorId)).thenReturn(provider());
        when(vashandiClient.fetchWorkContext(actorId)).thenReturn(workContextWithAssignment());

        ResponseEntity<Map<String, Object>> response = start(Map.of(
                "facilityId", UUID.randomUUID().toString()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("WORK_SESSION_UNAVAILABLE");
        verify(tshepoIdentityClient, never()).issueWorkContextToken(any());
    }

    @Test
    void unlinkedPersonGetsSameGenericDenial() {
        when(varapiClient.getProviderByHealthId(actorId)).thenReturn(null);

        ResponseEntity<Map<String, Object>> response = start(Map.of("facilityId", facilityId));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.getBody().get("error");
        assertThat(error.get("code")).isEqualTo("WORK_SESSION_UNAVAILABLE");
        verify(tshepoIdentityClient, never()).issueWorkContextToken(any());
        verify(vashandiClient, never()).fetchWorkContext(any());
    }

    @Test
    void missingFacilityIsBadRequest() {
        lenient().when(varapiClient.getProviderByHealthId(any())).thenReturn(null);
        ResponseEntity<Map<String, Object>> response = start(Map.of());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
