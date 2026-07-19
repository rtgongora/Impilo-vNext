package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.experience.client.TshepoAuditServiceClient;
import zw.gov.mohcc.impilo.experience.client.TshepoConsentServiceClient;
import zw.gov.mohcc.impilo.experience.service.SubjectResolutionService;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CitizenConsentCenterController (CJ13)")
class CitizenConsentCenterControllerTest {

    private static final String ACTOR = "11111111-1111-1111-1111-111111111111"; // citizen actorId == healthId
    private static final String CPID = "cpid-abc";

    private final ObjectMapper mapper = new ObjectMapper();
    private TshepoConsentServiceClient consent;
    private TshepoAuditServiceClient audit;
    private SubjectResolutionService subjects;
    private CitizenConsentCenterController controller;

    @BeforeEach
    void setUp() {
        consent = mock(TshepoConsentServiceClient.class);
        audit = mock(TshepoAuditServiceClient.class);
        subjects = mock(SubjectResolutionService.class);
        controller = new CitizenConsentCenterController(consent, audit, subjects);
    }

    @Test
    @DisplayName("composes my-consents + CPID-resolved access-log")
    void composesBoth() throws Exception {
        when(consent.myConsents(0, 20)).thenReturn(mapper.readTree("[{\"id\":\"c1\"}]"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn(CPID);
        when(audit.getAccessHistory(CPID, 0, 20)).thenReturn(mapper.readTree("[{\"actor\":\"dr-x\"}]"));

        ResponseEntity<Map<String, Object>> r = controller.consentCenter(ACTOR, 0, 20);
        assertThat(r.getBody().get("consents").toString()).contains("c1");
        assertThat(r.getBody().get("accessLog").toString()).contains("dr-x");
        // The access-log was fetched by the SERVER-resolved CPID, not any client-supplied id.
        verify(audit).getAccessHistory(CPID, 0, 20);
    }

    @Test
    @DisplayName("no CPID resolved → access-log omitted, consents still returned")
    void degradesWhenNoCpid() throws Exception {
        when(consent.myConsents(0, 20)).thenReturn(mapper.readTree("[{\"id\":\"c1\"}]"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn(null);

        ResponseEntity<Map<String, Object>> r = controller.consentCenter(ACTOR, 0, 20);
        assertThat(r.getBody().get("consents").toString()).contains("c1");
        assertThat(r.getBody().get("accessLog")).isNull();
        verify(audit, never()).getAccessHistory(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("audit source down → consents still returned (partial degrade)")
    void degradesWhenAuditDown() throws Exception {
        when(consent.myConsents(0, 20)).thenReturn(mapper.readTree("[{\"id\":\"c1\"}]"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn(CPID);
        when(audit.getAccessHistory(CPID, 0, 20)).thenThrow(new RuntimeException("audit down"));

        ResponseEntity<Map<String, Object>> r = controller.consentCenter(ACTOR, 0, 20);
        assertThat(r.getBody().get("consents").toString()).contains("c1");
        assertThat(r.getBody().get("accessLog")).isNull();
    }

    @Test
    @DisplayName("revoke passes through to the ownership-verified portal endpoint")
    void revokePassesThrough() throws Exception {
        UUID id = UUID.randomUUID();
        when(consent.revokeMyConsent(id)).thenReturn(mapper.readTree("{\"status\":\"REVOKED\"}"));
        ResponseEntity<Map<String, Object>> r = controller.revoke(id);
        assertThat(r.getBody().get("status")).isEqualTo("REVOKED");
        verify(consent).revokeMyConsent(id);
    }
}
