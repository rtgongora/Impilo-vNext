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
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceEmploymentMatchClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for the provider self-service claim/recovery BFF glue
 * (IATG WS-F). Pure-Mockito, matching {@link CitizenStepUpControllerTest}
 * conventions: mocked downstream clients, headers bind the session identity.
 */
@ExtendWith(MockitoExtension.class)
class ProviderClaimControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "11111111-2222-3333-4444-555555555555";
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";

    @Mock private VarapiServiceClient varapiClient;
    @Mock private WorkforceEmploymentMatchClient employmentMatchClient;

    private ProviderClaimController controller(boolean ecMatchingEnabled) {
        return new ProviderClaimController(varapiClient, employmentMatchClient, ecMatchingEnabled);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> data(ResponseEntity<Map<String, Object>> resp) {
        assertNotNull(resp.getBody());
        return (Map<String, Object>) resp.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> error(ResponseEntity<Map<String, Object>> resp) {
        assertNotNull(resp.getBody());
        return (Map<String, Object>) resp.getBody().get("error");
    }

    // ── Eligibility ───────────────────────────────────────────────────────────

    @Test
    void eligibility_noLinkedProvider_isEligibleForClaim() {
        when(varapiClient.getProviderByHealthId(ACTOR)).thenReturn(null);

        ResponseEntity<Map<String, Object>> resp =
                controller(false).eligibility(TENANT, REQ, CORR, ACTOR);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals(false, data.get("alreadyLinked"));
        assertEquals(true, data.get("eligibleForClaim"));
    }

    @Test
    void eligibility_linkedProvider_returnsMaskedIdOnly() throws Exception {
        when(varapiClient.getProviderByHealthId(ACTOR))
                .thenReturn(MAPPER.readTree("{\"providerPublicId\":\"ABCDEF1234567890\"}"));

        ResponseEntity<Map<String, Object>> resp =
                controller(false).eligibility(TENANT, REQ, CORR, ACTOR);

        Map<String, Object> data = data(resp);
        assertEquals(true, data.get("alreadyLinked"));
        assertEquals(false, data.get("eligibleForClaim"));
        assertEquals("ABCD***90", data.get("providerPublicId"), "provider id must be masked");
    }

    // ── Preview ───────────────────────────────────────────────────────────────

    @Test
    void preview_returnsMaskedSummary() throws Exception {
        when(varapiClient.claimPreview(any())).thenReturn(MAPPER.readTree(
                "{\"providerPublicId\":\"ABCDEF1234567890\",\"givenName\":\"Tariro\","
                        + "\"familyName\":\"Moyo\",\"profession\":\"DOCTOR\",\"lifecycleStatus\":\"PRELOADED\"}"));

        ResponseEntity<Map<String, Object>> resp = controller(false)
                .preview(TENANT, REQ, CORR, ACTOR, Map.of("claimToken", "tok-1"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals("ABCD***90", data.get("providerPublicId"));
        assertEquals("T.", data.get("givenNameInitial"));
        assertEquals("Moyo", data.get("familyName"));
        assertEquals("PRELOADED", data.get("lifecycleStatus"));
    }

    @Test
    void preview_unknownToken_is404WithoutLeak() {
        when(varapiClient.claimPreview(any()))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        ResponseEntity<Map<String, Object>> resp = controller(false)
                .preview(TENANT, REQ, CORR, ACTOR, Map.of("claimToken", "bogus"));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("TOKEN_NOT_RECOGNISED", error(resp).get("code"));
    }

    // ── Claim ─────────────────────────────────────────────────────────────────

    @Test
    void claim_bindsClaimantToSessionHealthId_andRequiresConsent() throws Exception {
        when(varapiClient.claim(any())).thenReturn(MAPPER.readTree(
                "{\"providerPublicId\":\"ABCDEF1234567890\",\"lifecycleStatus\":\"CLAIMED\"}"));

        ResponseEntity<Map<String, Object>> resp = controller(false).claim(
                TENANT, REQ, CORR, ACTOR,
                Map.of("claimToken", "tok-1", "consent", true, "claimantHealthId", "actor-EVIL"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals(true, data.get("linked"));
        assertEquals("ABCD***90", data.get("providerPublicId"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(varapiClient).claim(captor.capture());
        assertEquals(ACTOR, captor.getValue().get("claimantHealthId"),
                "claimant must come from X-Actor-ID, never the body");
    }

    @Test
    void claim_withoutConsent_is400_andNeverCallsDownstream() {
        ResponseEntity<Map<String, Object>> resp = controller(false).claim(
                TENANT, REQ, CORR, ACTOR, Map.of("claimToken", "tok-1"));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("CONSENT_REQUIRED", error(resp).get("code"));
        verify(varapiClient, never()).claim(any());
    }

    @Test
    void claim_downstream409_propagatesHonestly() {
        when(varapiClient.claim(any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", null,
                "{\"error\":\"Claim token is invalid, expired, or already used\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> resp = controller(false).claim(
                TENANT, REQ, CORR, ACTOR, Map.of("claimToken", "tok-1", "consent", true));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        String message = String.valueOf(error(resp).get("message"));
        assertTrue(message.contains("already used"), "downstream verdict must not be rewritten");
    }

    // ── Evidence ──────────────────────────────────────────────────────────────

    @Test
    void evidence_councilNumber_recordsMaskedRefOnTrustLedger() throws Exception {
        when(varapiClient.resolveCouncilNumber("EC-1001", "MDPCZ")).thenReturn(MAPPER.readTree(
                "{\"resolved\":true,\"providerRef\":{\"providerPublicId\":\"ABCDEF1234567890\"}}"));
        when(varapiClient.recordVerificationAttempt(anyString(), any()))
                .thenReturn(MAPPER.readTree("{\"outcome\":\"PENDING\"}"));

        ResponseEntity<Map<String, Object>> resp = controller(false).evidence(
                TENANT, REQ, CORR, ACTOR,
                Map.of("type", "COUNCIL_NUMBER", "value", "EC-1001", "councilCode", "MDPCZ"));

        assertEquals(HttpStatus.ACCEPTED, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals("RECORDED", data.get("status"));
        assertEquals("EC***", data.get("evidenceRef"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(varapiClient).recordVerificationAttempt(eq("ABCDEF1234567890"), captor.capture());
        Map<String, Object> attempt = captor.getValue();
        assertEquals("COUNCIL_RECORD", attempt.get("source"));
        assertEquals("EC***", attempt.get("ref"), "raw council number must never reach the ledger");
    }

    @Test
    void evidence_councilNumber_unresolved_is404() throws Exception {
        when(varapiClient.resolveCouncilNumber("EC-9999", null))
                .thenReturn(MAPPER.readTree("{\"resolved\":false,\"providerRef\":null}"));

        ResponseEntity<Map<String, Object>> resp = controller(false).evidence(
                TENANT, REQ, CORR, ACTOR, Map.of("type", "COUNCIL_NUMBER", "value", "EC-9999"));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("COUNCIL_NUMBER_UNRESOLVED", error(resp).get("code"));
        verify(varapiClient, never()).recordVerificationAttempt(anyString(), any());
    }

    @Test
    void evidence_ecNumber_flagOff_is501FeaturePending_neverCallsWsD() {
        ResponseEntity<Map<String, Object>> resp = controller(false).evidence(
                TENANT, REQ, CORR, ACTOR, Map.of("type", "EC_NUMBER", "value", "EC-77"));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        assertEquals("FEATURE_PENDING", error(resp).get("code"));
        verifyNoInteractions(employmentMatchClient);
    }

    @Test
    void evidence_ecNumber_flagOn_callsEmploymentMatchWithSessionHealthId() throws Exception {
        when(employmentMatchClient.matchEmployment(any()))
                .thenReturn(MAPPER.readTree("{\"matchStatus\":\"MATCHED\"}"));

        ResponseEntity<Map<String, Object>> resp = controller(true).evidence(
                TENANT, REQ, CORR, ACTOR, Map.of("type", "EC_NUMBER", "value", "EC-77"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals("MATCHED", data(resp).get("matchStatus"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(employmentMatchClient).matchEmployment(captor.capture());
        assertEquals(ACTOR, captor.getValue().get("healthId"));
        assertEquals("EC-77", captor.getValue().get("ecNumber"));
    }

    @Test
    void evidence_document_is501FeaturePending() {
        ResponseEntity<Map<String, Object>> resp = controller(false).evidence(
                TENANT, REQ, CORR, ACTOR, Map.of("type", "DOCUMENT", "value", "doc-ref"));

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        assertEquals("FEATURE_PENDING", error(resp).get("code"));
    }

    // ── Recover ───────────────────────────────────────────────────────────────

    @Test
    void recover_returnsSameProviderPublicIdMasked_neverMints() throws Exception {
        when(varapiClient.recoveryInitiate(any())).thenReturn(MAPPER.readTree(
                "{\"providerPublicId\":\"ABCDEF1234567890\",\"recoveryToken\":\"raw-tok\","
                        + "\"expiresAt\":\"2026-07-06T00:00:00Z\"}"));
        when(varapiClient.recoveryComplete(any())).thenReturn(MAPPER.readTree(
                "{\"providerPublicId\":\"ABCDEF1234567890\",\"impiloHealthId\":\"" + ACTOR + "\","
                        + "\"lifecycleStatus\":\"CLAIMED\"}"));

        ResponseEntity<Map<String, Object>> resp = controller(false).recover(
                TENANT, REQ, CORR, ACTOR,
                Map.of("evidence", Map.of("type", "COUNCIL_NUMBER", "value", "EC-1001")));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals(true, data.get("recovered"));
        // The SAME provider identifier pre/post recovery, masked.
        assertEquals("ABCD***90", data.get("providerPublicId"));

        // initiate binds to the session Health ID; evidence ref is masked at the edge.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(varapiClient).recoveryInitiate(captor.capture());
        assertEquals(ACTOR, captor.getValue().get("healthId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> evidence = (Map<String, Object>) captor.getValue().get("evidence");
        assertEquals("EC***", evidence.get("ref"));
        verify(varapiClient).recoveryComplete(eq(Map.of("token", "raw-tok")));
    }

    @Test
    void recover_identifierMismatch_failsClosed() throws Exception {
        when(varapiClient.recoveryInitiate(any())).thenReturn(MAPPER.readTree(
                "{\"providerPublicId\":\"ABCDEF1234567890\",\"recoveryToken\":\"raw-tok\"}"));
        when(varapiClient.recoveryComplete(any())).thenReturn(MAPPER.readTree(
                "{\"providerPublicId\":\"DIFFERENT-ID-000\",\"lifecycleStatus\":\"CLAIMED\"}"));

        ResponseEntity<Map<String, Object>> resp = controller(false).recover(
                TENANT, REQ, CORR, ACTOR,
                Map.of("evidence", Map.of("type", "COUNCIL_NUMBER", "value", "EC-1001")));

        assertEquals(HttpStatus.BAD_GATEWAY, resp.getStatusCode());
        assertEquals("RECOVERY_INTEGRITY", error(resp).get("code"));
    }

    @Test
    void recover_unknownHealthId_is404NoLinkedProfile() {
        when(varapiClient.recoveryInitiate(any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.NOT_FOUND, "Not Found", null, null, null));

        ResponseEntity<Map<String, Object>> resp = controller(false).recover(
                TENANT, REQ, CORR, ACTOR,
                Map.of("evidence", Map.of("type", "COUNCIL_NUMBER", "value", "EC-1001")));

        assertEquals(HttpStatus.NOT_FOUND, resp.getStatusCode());
        assertEquals("NO_LINKED_PROFILE", error(resp).get("code"));
    }

    // ── Org invitation (Channel C placeholder) ────────────────────────────────

    @Test
    void orgInvitation_is501FeaturePending() {
        ResponseEntity<Map<String, Object>> resp =
                controller(false).orgInvitation(TENANT, REQ, CORR, ACTOR);

        assertEquals(HttpStatus.NOT_IMPLEMENTED, resp.getStatusCode());
        assertEquals("FEATURE_PENDING", error(resp).get("code"));
    }
}
