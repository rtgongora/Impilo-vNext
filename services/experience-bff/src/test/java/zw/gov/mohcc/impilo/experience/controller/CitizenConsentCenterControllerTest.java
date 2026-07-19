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
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";

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

        Map<String, Object> data = data(controller.consentCenter(ACTOR, REQ, CORR, 0, 20));
        assertThat(data.get("consents").toString()).contains("c1");
        assertThat(data.get("accessLog").toString()).contains("dr-x");
        // The access-log was fetched by the SERVER-resolved CPID, not any client-supplied id.
        verify(audit).getAccessHistory(CPID, 0, 20);
    }

    @Test
    @DisplayName("no CPID resolved → access-log omitted, consents still returned")
    void degradesWhenNoCpid() throws Exception {
        when(consent.myConsents(0, 20)).thenReturn(mapper.readTree("[{\"id\":\"c1\"}]"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn(null);

        Map<String, Object> data = data(controller.consentCenter(ACTOR, REQ, CORR, 0, 20));
        assertThat(data.get("consents").toString()).contains("c1");
        assertThat(data.get("accessLog")).isNull();
        verify(audit, never()).getAccessHistory(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("audit source down → consents still returned (partial degrade)")
    void degradesWhenAuditDown() throws Exception {
        when(consent.myConsents(0, 20)).thenReturn(mapper.readTree("[{\"id\":\"c1\"}]"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn(CPID);
        when(audit.getAccessHistory(CPID, 0, 20)).thenThrow(new RuntimeException("audit down"));

        Map<String, Object> data = data(controller.consentCenter(ACTOR, REQ, CORR, 0, 20));
        assertThat(data.get("consents").toString()).contains("c1");
        assertThat(data.get("accessLog")).isNull();
    }

    @Test
    @DisplayName("response uses the house {data, meta} envelope the shell expects")
    void usesEnvelope() throws Exception {
        when(consent.myConsents(0, 20)).thenReturn(mapper.readTree("[]"));
        when(subjects.cpidForHealthId(ACTOR)).thenReturn(null);
        ResponseEntity<Map<String, Object>> r = controller.consentCenter(ACTOR, REQ, CORR, 0, 20);
        assertThat(r.getBody()).containsKeys("data", "meta");
        assertThat(((Map<?, ?>) r.getBody().get("meta")).get("correlation_id")).isEqualTo(CORR);
    }

    @Test
    @DisplayName("revoke passes through to the ownership-verified portal endpoint")
    void revokePassesThrough() throws Exception {
        UUID id = UUID.randomUUID();
        when(consent.revokeMyConsent(id)).thenReturn(mapper.readTree("{\"status\":\"REVOKED\"}"));
        Map<String, Object> data = data(controller.revoke(id, REQ, CORR));
        assertThat(data.get("status")).isEqualTo("REVOKED");
        verify(consent).revokeMyConsent(id);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> r) {
        return (Map<String, Object>) r.getBody().get("data");
    }
}
