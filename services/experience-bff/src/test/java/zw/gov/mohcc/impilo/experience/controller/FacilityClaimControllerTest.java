package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrgRegistryFacilityAdminClient;
import zw.gov.mohcc.impilo.experience.client.TusoFacilityClaimClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * Unit coverage for the facility administrator claim/appointment BFF glue (IATG Wave 2, WS-E).
 * Pure-Mockito, matching {@link ProviderClaimControllerTest} conventions: mocked downstream clients,
 * headers bind the session identity, masking proven at the edge.
 */
@ExtendWith(MockitoExtension.class)
class FacilityClaimControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TENANT = "tenant-1";
    private static final String ACTOR = "11111111-2222-3333-4444-555555555555";
    private static final String REQ = "req-1";
    private static final String CORR = "corr-1";
    private static final String FACILITY = "99999999-8888-7777-6666-555555555555";

    @Mock private TusoFacilityClaimClient tusoClient;
    @Mock private OrgRegistryFacilityAdminClient orgRegistryClient;
    @Mock private DocumentServiceClient documentServiceClient;
    @Mock private zw.gov.mohcc.impilo.experience.client.IdentityAssuranceServiceClient assuranceClient;

    private FacilityClaimController controller() {
        return new FacilityClaimController(tusoClient, orgRegistryClient, documentServiceClient, assuranceClient);
    }

    private void assuranceLevel(String loa) {
        try {
            org.mockito.Mockito.lenient().when(assuranceClient.getAssuranceStatus())
                    .thenReturn(MAPPER.readTree("{\"currentLevel\":\"" + loa + "\"}"));
        } catch (Exception e) { throw new RuntimeException(e); }
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
    void eligibility_deniedWhenFacilityNotAllowedOnPlatform() throws Exception {
        when(tusoClient.eligibility(FACILITY)).thenReturn(MAPPER.readTree(
                "{\"allowedOnPlatform\":false,\"claimable\":false,\"alreadyAdministered\":false,"
                        + "\"activeAdministratorCount\":0}"));

        ResponseEntity<Map<String, Object>> resp =
                controller().eligibility(TENANT, REQ, CORR, ACTOR, FACILITY);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals(false, data.get("allowedOnPlatform"));
        assertEquals(false, data.get("claimable"));
    }

    // ── Facility-admin appointments (Facility Mode) ────────────────────────────

    @Test
    void appointments_proxiesFacilityAdminAppointments() throws Exception {
        when(tusoClient.appointments(FACILITY)).thenReturn(MAPPER.readTree(
                "[{\"id\":1,\"personHealthId\":\"hid-1\",\"approvalState\":\"APPROVED\"}]"));

        ResponseEntity<Map<String, Object>> resp =
                controller().appointments(TENANT, REQ, CORR, ACTOR, FACILITY);

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(FACILITY, data(resp).get("facilityUuid"));
        assertNotNull(data(resp).get("appointments"));
    }

    @Test
    void appointments_requiresFacilityUuid() {
        ResponseEntity<Map<String, Object>> resp =
                controller().appointments(TENANT, REQ, CORR, ACTOR, "");

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("FACILITY_UUID_REQUIRED", error(resp).get("code"));
    }

    @Test
    void eligibility_claimableWhenAllowedOnPlatform() throws Exception {
        when(tusoClient.eligibility(FACILITY)).thenReturn(MAPPER.readTree(
                "{\"allowedOnPlatform\":true,\"claimable\":true,\"alreadyAdministered\":true,"
                        + "\"activeAdministratorCount\":2}"));

        ResponseEntity<Map<String, Object>> resp =
                controller().eligibility(TENANT, REQ, CORR, ACTOR, FACILITY);

        Map<String, Object> data = data(resp);
        assertEquals(true, data.get("claimable"));
        // A facility may have many administrators — already-administered does not block.
        assertEquals(true, data.get("alreadyAdministered"));
        assertEquals(2, data.get("activeAdministratorCount"));
    }

    // ── Preview: masking proven by serialized JSON ─────────────────────────────

    @Test
    void preview_masksFacilityCode_provenBySerializedJson() throws Exception {
        when(tusoClient.preview(FACILITY)).thenReturn(MAPPER.readTree(
                "{\"facilityCode\":\"ZW010125\",\"name\":\"Bangure Clinic\",\"facilityType\":\"CLINIC\","
                        + "\"province\":\"Harare\",\"district\":\"Harare\",\"allowedOnPlatform\":true,\"claimable\":true}"));

        ResponseEntity<Map<String, Object>> resp =
                controller().preview(TENANT, REQ, CORR, ACTOR, Map.of("facilityUuid", FACILITY));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals("ZW01***", data.get("facilityCode"));

        // Serialized-JSON assertion: the raw facility code must never appear on the wire.
        String json = MAPPER.writeValueAsString(resp.getBody());
        assertTrue(json.contains("ZW01***"), "masked facility code must be present");
        assertFalse(json.contains("ZW010125"), "raw facility code must never leave the BFF");
    }

    // ── Appoint (claim) ────────────────────────────────────────────────────────

    @Test
    void appoint_bindsClaimantToSessionHealthId_masksEvidence_andRequiresConsent() throws Exception {
        assuranceLevel("LOA2");
        when(tusoClient.submitClaim(eq(FACILITY), any())).thenReturn(MAPPER.readTree(
                "{\"id\":7,\"personHealthId\":\"" + ACTOR + "\",\"role\":\"FACILITY_ADMINISTRATOR\","
                        + "\"approvalState\":\"PENDING\"}"));

        ResponseEntity<Map<String, Object>> resp = controller().appoint(
                TENANT, REQ, CORR, ACTOR,
                Map.of("facilityUuid", FACILITY, "consent", true,
                        "evidenceRef", "APPT-LETTER-12345",
                        "personHealthId", "actor-EVIL"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals(true, data.get("submitted"));
        assertEquals("PENDING", data.get("approvalState"));
        // Echoed Health ID is masked at the edge.
        assertEquals("1111***55", data.get("personHealthId"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(tusoClient).submitClaim(eq(FACILITY), captor.capture());
        Map<String, Object> sent = captor.getValue();
        assertEquals(ACTOR, sent.get("personHealthId"),
                "claimant must come from X-Actor-ID, never the body");
        assertEquals("AP***", sent.get("evidenceRef"),
                "raw evidence ref must be masked before leaving the BFF");
    }

    @Test
    void appoint_withoutConsent_is400_andNeverCallsDownstream() {
        ResponseEntity<Map<String, Object>> resp = controller().appoint(
                TENANT, REQ, CORR, ACTOR, Map.of("facilityUuid", FACILITY));

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("CONSENT_REQUIRED", error(resp).get("code"));
        verify(tusoClient, never()).submitClaim(anyString(), any());
    }

    @Test
    void appoint_belowLoa2_isForbidden_andNeverClaims() {
        assuranceLevel("LOA1");

        ResponseEntity<Map<String, Object>> resp = controller().appoint(
                TENANT, REQ, CORR, ACTOR,
                Map.of("facilityUuid", FACILITY, "consent", true, "evidenceRef", "APPT-1"));

        assertEquals(HttpStatus.FORBIDDEN, resp.getStatusCode());
        assertEquals("IDENTITY_PROOFING_REQUIRED", error(resp).get("code"));
        verify(tusoClient, never()).submitClaim(anyString(), any());
    }

    @Test
    void appoint_facilityNotAllowed_propagates409Honestly() {
        assuranceLevel("LOA2");
        when(tusoClient.submitClaim(eq(FACILITY), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.CONFLICT, "Conflict", null,
                "{\"error\":\"Facility is not allowed on the platform and cannot be claimed\"}"
                        .getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        ResponseEntity<Map<String, Object>> resp = controller().appoint(
                TENANT, REQ, CORR, ACTOR, Map.of("facilityUuid", FACILITY, "consent", true));

        assertEquals(HttpStatus.CONFLICT, resp.getStatusCode());
        String message = String.valueOf(error(resp).get("message"));
        assertTrue(message.contains("not allowed on the platform"),
                "downstream verdict must not be rewritten");
    }

    // ── Approve → ACTIVE + affiliation created ─────────────────────────────────

    @Test
    void approve_flipsAppointmentActive_andRecordsAffiliation() throws Exception {
        String orgId = "22222222-3333-4444-5555-666666666666";
        when(tusoClient.approve(eq("7"), any())).thenReturn(MAPPER.readTree(
                "{\"id\":7,\"facilityUuid\":\"" + FACILITY + "\",\"personHealthId\":\"" + ACTOR + "\","
                        + "\"approvalState\":\"ACTIVE\"}"));
        when(orgRegistryClient.recordFacilityAdminAffiliation(eq(orgId), any()))
                .thenReturn(MAPPER.readTree("{\"id\":\"aff-1\",\"status\":\"ACTIVE\"}"));

        ResponseEntity<Map<String, Object>> resp = controller().approve(
                TENANT, REQ, CORR, ACTOR,
                Map.of("appointmentId", "7", "organizationId", orgId));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        Map<String, Object> data = data(resp);
        assertEquals("ACTIVE", data.get("approvalState"));
        assertEquals(true, data.get("affiliationRecorded"));

        // TUSO flips the appointment; org-registry records the administering affiliation.
        verify(tusoClient).approve(eq("7"), any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(orgRegistryClient).recordFacilityAdminAffiliation(eq(orgId), captor.capture());
        assertEquals(FACILITY, captor.getValue().get("facilityUuid"));
    }

    @Test
    void approve_withoutOrganization_activatesButRecordsNoAffiliation() throws Exception {
        when(tusoClient.approve(eq("7"), any())).thenReturn(MAPPER.readTree(
                "{\"id\":7,\"facilityUuid\":\"" + FACILITY + "\",\"personHealthId\":\"" + ACTOR + "\","
                        + "\"approvalState\":\"ACTIVE\"}"));

        ResponseEntity<Map<String, Object>> resp = controller().approve(
                TENANT, REQ, CORR, ACTOR, Map.of("appointmentId", "7"));

        assertEquals(HttpStatus.OK, resp.getStatusCode());
        assertEquals(false, data(resp).get("affiliationRecorded"));
        verifyNoInteractions(orgRegistryClient);
    }

    // ── Honest pending lanes ───────────────────────────────────────────────────

    @Test
    void evidence_document_uploadsToDocumentServiceAndReturnsRef() throws Exception {
        when(documentServiceClient.uploadObject(any(), any(), anyString(), any()))
                .thenReturn(MAPPER.readTree(
                        "{\"objectId\":\"obj-1\",\"originalFilename\":\"letter.pdf\",\"mimeType\":\"application/pdf\",\"scanStatus\":\"CLEAN\"}"));

        MockMultipartFile file =
                new MockMultipartFile("file", "letter.pdf", "application/pdf", "bytes".getBytes(StandardCharsets.UTF_8));
        ResponseEntity<Map<String, Object>> resp = controller().evidence(TENANT, REQ, CORR, ACTOR, file);

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals("obj-1", data(resp).get("evidenceRef"));
        assertEquals("letter.pdf", data(resp).get("filename"));
    }

    @Test
    void evidence_document_rejectsEmptyFile() {
        MockMultipartFile empty = new MockMultipartFile("file", "e.pdf", "application/pdf", new byte[0]);
        ResponseEntity<Map<String, Object>> resp = controller().evidence(TENANT, REQ, CORR, ACTOR, empty);

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("EVIDENCE_FILE_REQUIRED", error(resp).get("code"));
        verifyNoInteractions(documentServiceClient);
    }

    @Test
    void orgInvitation_acceptsTokenViaOrgRegistry() throws Exception {
        when(orgRegistryClient.acceptInvitation(eq("TOK"), eq(ACTOR)))
                .thenReturn(MAPPER.readTree(
                        "{\"id\":\"inv-1\",\"organizationId\":\"org-1\",\"role\":\"FACILITY_ADMIN\",\"affiliationId\":\"aff-1\",\"status\":\"ACCEPTED\"}"));

        ResponseEntity<Map<String, Object>> resp =
                controller().orgInvitation(TENANT, REQ, CORR, ACTOR, Map.of("token", "TOK"));

        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        assertEquals(Boolean.TRUE, data(resp).get("accepted"));
        assertEquals("aff-1", data(resp).get("affiliationId"));
    }

    @Test
    void orgInvitation_requiresToken() {
        ResponseEntity<Map<String, Object>> resp =
                controller().orgInvitation(TENANT, REQ, CORR, ACTOR, Map.of());

        assertEquals(HttpStatus.BAD_REQUEST, resp.getStatusCode());
        assertEquals("INVITATION_TOKEN_REQUIRED", error(resp).get("code"));
        verifyNoInteractions(orgRegistryClient);
    }
}
