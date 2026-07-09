package zw.gov.mohcc.impilo.experience.workforceintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceAuditHelper;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceDtos;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernancePolicyService;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceResponses;
import zw.gov.mohcc.impilo.experience.admingovernance.GovernanceInvitationService;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiDtos;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Workforce Intake BFF orchestration: upload cap, match preview (dedupe +
 * identifier-contradiction ambiguity), idempotent partial-execution resume,
 * and honest BLOCKED_MISSING_CONTACT for rows without an email.
 */
@ExtendWith(MockitoExtension.class)
class WorkforceIntakeServiceTest {

    @Mock private WorkforceGovernanceClient workforceGovernanceClient;
    @Mock private TshepoIdentityServiceClient tshepoIdentityClient;
    @Mock private VarapiServiceClient varapiClient;
    @Mock private VashandiServiceClient vashandiClient;
    @Mock private GovernanceInvitationService governanceInvitationService;
    @Mock private AdminGovernancePolicyService policyService;
    @Mock private AdminGovernanceAuditHelper auditHelper;
    @Mock private SessionExperienceService sessionExperienceService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private WorkforceIntakeService service;

    private static final String TENANT = "00000000-0000-0000-0000-000000000001";
    private static final String ACTOR = "admin-actor";
    private static final String ORG_ID = "7c1f8a1e-1111-4222-8333-944445555666";
    private static final String BATCH_ID = "b0a7c1d2-0000-4111-8222-933334444555";
    private static final String IMPORTS = "/v1/internal/governance/imports";

    @BeforeEach
    void setUp() {
        service = new WorkforceIntakeService(workforceGovernanceClient, tshepoIdentityClient, varapiClient,
                vashandiClient, governanceInvitationService, policyService, auditHelper,
                sessionExperienceService, objectMapper);
        lenient().when(sessionExperienceService.buildExperienceContract(anyString(), any(), any(), anyBoolean()))
                .thenReturn(Map.of("visibleManagementWorkspaces", List.of("mohcc_head_office_user_management")));
        lenient().when(policyService.evaluate(anyMap(), any())).thenReturn(allowed());
        lenient().when(auditHelper.emit(anyString(), anyString(), anyString(), anyMap()))
                .thenReturn(AdminGovernanceResponses.auditIngested("corr-1"));
    }

    private static AdminGovernanceDtos.PolicyDecision allowed() {
        return new AdminGovernanceDtos.PolicyDecision(true, "impilo.admin", "v1", "d-1", List.of(), List.of());
    }

    private static AdminGovernanceDtos.PolicyDecision denied(String warning) {
        return new AdminGovernanceDtos.PolicyDecision(false, "impilo.admin", "v1", "d-2", List.of(warning), List.of());
    }

    private JsonNode json(Object value) {
        return objectMapper.valueToTree(value);
    }

    private Map<String, Object> row(String id, int number, Map<String, String> payload, Map<String, Object> extra) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("rowNumber", number);
        row.put("validationStatus", "validated");
        row.put("outcome", "ready_to_invite");
        try {
            row.put("normalizedPayloadJson", objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        row.putAll(extra);
        return row;
    }

    // ── upload ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("upload happy path stages a wgv batch from CSV")
    void uploadStagesBatch() {
        when(workforceGovernanceClient.postJson(eq(IMPORTS), anyMap()))
                .thenReturn(json(Map.of("id", BATCH_ID, "stage", "UPLOADED", "rowCount", 1)));

        String csv = "nationalId,givenName,familyName,profession,email\n63-123456A63,Rudo,Moyo,NURSE,rudo@mohcc.gov.zw\n";
        AdminGovernanceDtos.LookupEnvelope result = service.createBatch(ACTOR, null, false, ORG_ID, "staff.csv", csv, null);

        assertThat(result.integrationStatus()).isEqualTo("live");
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(eq(IMPORTS), captor.capture());
        assertThat(captor.getValue())
                .containsEntry("importType", "workforce_intake")
                .containsEntry("organisationId", ORG_ID);
        assertThat(String.valueOf(captor.getValue().get("csvContent"))).contains("63-123456A63");
        verify(auditHelper).emit(eq("workforce_intake.batch_uploaded"), eq(ACTOR), eq(BATCH_ID), anyMap());
    }

    @Test
    @DisplayName("upload rejects batches over the 2000-row cap with a clear error")
    void uploadRejectsOversizedBatch() {
        StringBuilder csv = new StringBuilder("nationalId,givenName,familyName,profession\n");
        for (int i = 0; i < 2001; i++) {
            csv.append("63-").append(i).append("A63,Given,Family,NURSE\n");
        }
        AdminGovernanceDtos.LookupEnvelope result = service.createBatch(ACTOR, null, false, ORG_ID, "big.csv", csv.toString(), null);

        assertThat(result.integrationStatus()).isEqualTo("pending_backend");
        assertThat(result.friendlyMessage()).contains("2001 rows").contains("2000");
        verify(workforceGovernanceClient, never()).postJson(anyString(), anyMap());
    }

    @Test
    @DisplayName("upload denied by policy never reaches workforce governance")
    void uploadDeniedByPolicy() {
        when(policyService.evaluate(anyMap(), any())).thenReturn(denied("No authority for workforce intake."));

        AdminGovernanceDtos.LookupEnvelope result = service.createBatch(ACTOR, null, false, ORG_ID, "staff.csv",
                "nationalId,givenName\n63-1,Rudo\n", null);

        assertThat(result.integrationStatus()).isEqualTo("pending_backend");
        assertThat(result.friendlyMessage()).isEqualTo("No authority for workforce intake.");
        verify(workforceGovernanceClient, never()).postJson(anyString(), anyMap());
    }

    // ── match preview ────────────────────────────────────────────────────────

    @Test
    @DisplayName("match flags in-batch nationalId duplicates and resolves Health IDs")
    void matchDetectsDuplicatesAndResolvesHealthIds() {
        Map<String, String> alice = Map.of("nationalid", "63-111111A11", "givenname", "Alice", "familyname", "Banda",
                "profession", "NURSE", "healthid", "aa000000-0000-4000-8000-000000000001");
        Map<String, String> aliceDup = Map.of("nationalid", "63-111111A11", "givenname", "Alice", "familyname", "Banda",
                "profession", "NURSE");
        Map<String, String> newStaff = Map.of("nationalid", "63-222222B22", "givenname", "Brian", "familyname", "Chirwa",
                "profession", "PHARMACIST");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID))
                .thenReturn(json(Map.of("id", BATCH_ID, "stage", "VALIDATED", "organisationId", ORG_ID)));
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows")).thenReturn(json(List.of(
                row("row-1", 1, alice, Map.of()),
                row("row-2", 2, aliceDup, Map.of()),
                row("row-3", 3, newStaff, Map.of()))));
        when(workforceGovernanceClient.patchJson(startsWith(IMPORTS + "/" + BATCH_ID + "/rows/"), anyMap()))
                .thenReturn(json(Map.of("ok", true)));
        when(workforceGovernanceClient.patchJson(eq(IMPORTS + "/" + BATCH_ID + "/stage"), anyMap()))
                .thenReturn(json(Map.of("stage", "MATCHED")));
        when(tshepoIdentityClient.resolveIdentifier(anyMap())).thenAnswer(inv -> {
            Map<String, Object> req = inv.getArgument(0);
            if ("HEALTH_ID".equals(req.get("kind"))) {
                return json(Map.of("resolved", true, "personRef",
                        Map.of("healthId", "aa000000-0000-4000-8000-000000000001")));
            }
            return json(Map.of("resolved", false));
        });

        AdminGovernanceDtos.LookupEnvelope result = service.match(ACTOR, null, false, TENANT, BATCH_ID);

        assertThat(result.integrationStatus()).isEqualTo("live");
        Map<String, Object> summary = (Map<String, Object>) result.data().get("summary");
        assertThat(summary)
                .containsEntry("matched", 1)
                .containsEntry("duplicatesInBatch", 1)
                .containsEntry("noMatch", 1)
                .containsEntry("ambiguous", 0);

        ArgumentCaptor<Map<String, Object>> rowPatch = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).patchJson(eq(IMPORTS + "/" + BATCH_ID + "/rows/row-2"), rowPatch.capture());
        assertThat(rowPatch.getValue())
                .containsEntry("matchStatus", "DUPLICATE_IN_BATCH")
                .containsEntry("duplicateOfRowId", "row-1");
    }

    @Test
    @DisplayName("row identifiers resolving to different people is AMBIGUOUS, never auto-matched")
    void matchAmbiguityWhenIdentifiersContradict() {
        Map<String, String> contradiction = Map.of(
                "nationalid", "63-333333C33", "givenname", "Chipo", "familyname", "Dube", "profession", "DOCTOR",
                "healthid", "aa000000-0000-4000-8000-000000000001",
                "email", "chipo.dube@mohcc.gov.zw");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID))
                .thenReturn(json(Map.of("id", BATCH_ID, "stage", "VALIDATED", "organisationId", ORG_ID)));
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(row("row-1", 1, contradiction, Map.of()))));
        when(workforceGovernanceClient.patchJson(anyString(), anyMap())).thenReturn(json(Map.of("stage", "MATCHED")));
        when(tshepoIdentityClient.resolveIdentifier(anyMap())).thenAnswer(inv -> {
            Map<String, Object> req = inv.getArgument(0);
            String healthId = "HEALTH_ID".equals(req.get("kind"))
                    ? "aa000000-0000-4000-8000-000000000001"
                    : "bb000000-0000-4000-8000-000000000002";
            return json(Map.of("resolved", true, "personRef", Map.of("healthId", healthId)));
        });

        AdminGovernanceDtos.LookupEnvelope result = service.match(ACTOR, null, false, TENANT, BATCH_ID);

        Map<String, Object> summary = (Map<String, Object>) result.data().get("summary");
        assertThat(summary).containsEntry("ambiguous", 1).containsEntry("matched", 0);
        ArgumentCaptor<Map<String, Object>> rowPatch = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).patchJson(eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1"), rowPatch.capture());
        assertThat(rowPatch.getValue()).containsEntry("matchStatus", "AMBIGUOUS");
    }

    // ── execute ──────────────────────────────────────────────────────────────

    private void stubBatch(String stage) {
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID))
                .thenReturn(json(Map.of("id", BATCH_ID, "stage", stage, "organisationId", ORG_ID)));
        when(workforceGovernanceClient.patchJson(eq(IMPORTS + "/" + BATCH_ID + "/stage"), anyMap()))
                .thenAnswer(inv -> {
                    Map<String, Object> body = inv.getArgument(1);
                    return json(Map.of("stage", body.get("stage")));
                });
        lenient().when(workforceGovernanceClient.postJson(startsWith(IMPORTS + "/" + BATCH_ID + "/rows/"), anyMap()))
                .thenReturn(json(Map.of("ok", true)));
    }

    private VashandiDtos.UpstreamResult bridgeOk(String profileId, String assignmentId) {
        return VashandiDtos.UpstreamResult.live(json(Map.of(
                "profilesImported", 1,
                "assignmentsImported", assignmentId != null ? 1 : 0,
                "profileIds", List.of(profileId),
                "assignmentIds", assignmentId != null ? List.of(assignmentId) : List.of(),
                "status", "submitted")));
    }

    @Test
    @DisplayName("execute happy path issues Provider ID, bridges Vashandi, dispatches invitation, records DONE")
    void executeHappyPath() {
        Map<String, String> staff = Map.of("nationalid", "63-444444D44", "givenname", "Danai", "familyname", "Gutu",
                "profession", "NURSE", "email", "danai.gutu@mohcc.gov.zw", "cadre", "RGN");
        stubBatch("APPROVED");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(row("row-1", 1, staff, Map.of("matchStatus", "NO_MATCH")))));
        when(varapiClient.createProvider(anyMap())).thenReturn(json(Map.of(
                "providerPublicId", "PRV-2026-000777",
                "impiloHealthId", "cc000000-0000-4000-8000-000000000003")));
        when(vashandiClient.post(eq("/workforce-profiles/import-bridge"), any()))
                .thenReturn(bridgeOk("11111111-2222-4333-8444-555555555555", null));
        when(governanceInvitationService.deliverImportRowInvitation(eq(ORG_ID), eq(BATCH_ID),
                eq("danai.gutu@mohcc.gov.zw"), eq("Danai Gutu"), eq("workforce_intake")))
                .thenReturn(GovernanceInvitationService.InvitationDeliveryResult.sent(
                        "inv-1", "kc-user-1", "2026-07-16T00:00:00Z", "ingested", "Invitation sent."));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        assertThat(result.integrationStatus()).isEqualTo("live");
        assertThat(result.data()).containsEntry("stage", "COMPLETED");
        Map<String, Integer> tally = (Map<String, Integer>) result.data().get("tally");
        assertThat(tally).containsEntry("DONE", 1);

        ArgumentCaptor<Map<String, Object>> recorded = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(
                eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1/execution-result"), recorded.capture());
        assertThat(recorded.getValue())
                .containsEntry("executionStatus", "DONE")
                .containsEntry("providerPublicId", "PRV-2026-000777")
                .containsEntry("vashandiProfileId", "11111111-2222-4333-8444-555555555555")
                .containsEntry("invitationId", "inv-1");

        // The Vashandi bridge is called with the VARAPI-issued anchor, one row per call.
        ArgumentCaptor<Object> bridgeBody = ArgumentCaptor.forClass(Object.class);
        verify(vashandiClient).post(eq("/workforce-profiles/import-bridge"), bridgeBody.capture());
        JsonNode bridgeJson = json(bridgeBody.getValue());
        assertThat(bridgeJson.path("rows")).hasSize(1);
        assertThat(bridgeJson.path("rows").get(0).path("healthId").asText())
                .isEqualTo("cc000000-0000-4000-8000-000000000003");
        assertThat(bridgeJson.path("rows").get(0).path("providerWorkerId").asText())
                .isEqualTo("PRV-2026-000777");
    }

    @Test
    @DisplayName("execute reuses an existing provider for a matched Health ID instead of re-issuing")
    void executeReusesExistingProvider() {
        Map<String, String> staff = Map.of("nationalid", "63-555555E55", "givenname", "Enia", "familyname", "Hove",
                "profession", "NURSE", "email", "enia.hove@mohcc.gov.zw");
        stubBatch("APPROVED");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(row("row-1", 1, staff, Map.of(
                        "matchStatus", "MATCHED",
                        "matchedHealthId", "dd000000-0000-4000-8000-000000000004")))));
        when(varapiClient.getProviderByHealthId("dd000000-0000-4000-8000-000000000004"))
                .thenReturn(json(Map.of("providerPublicId", "PRV-2025-000111")));
        when(vashandiClient.post(eq("/workforce-profiles/import-bridge"), any()))
                .thenReturn(bridgeOk("22222222-3333-4444-8555-666666666666", null));
        when(governanceInvitationService.deliverImportRowInvitation(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GovernanceInvitationService.InvitationDeliveryResult.sent(
                        "inv-2", "kc-user-2", "2026-07-16T00:00:00Z", "ingested", "Invitation sent."));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        assertThat(result.data()).containsEntry("stage", "COMPLETED");
        verify(varapiClient, never()).createProvider(anyMap());
        ArgumentCaptor<Map<String, Object>> recorded = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(
                eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1/execution-result"), recorded.capture());
        assertThat(recorded.getValue()).containsEntry("providerPublicId", "PRV-2025-000111");
    }

    @Test
    @DisplayName("partial resume: varapi succeeded, vashandi failed → re-execute skips issuance and only redoes the bridge")
    void executePartialResumeSkipsDoneWork() {
        Map<String, String> staff = Map.of("nationalid", "63-666666F66", "givenname", "Farai", "familyname", "Jera",
                "profession", "NURSE", "email", "farai.jera@mohcc.gov.zw");
        stubBatch("PARTIAL");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(
                        row("row-1", 1, staff, Map.of(
                                "matchStatus", "MATCHED",
                                "matchedHealthId", "ee000000-0000-4000-8000-000000000005",
                                "executionStatus", "FAILED_VASHANDI",
                                "providerPublicId", "PRV-2026-000888")),
                        row("row-2", 2, staff, Map.of("executionStatus", "DONE")))));
        when(vashandiClient.post(eq("/workforce-profiles/import-bridge"), any()))
                .thenReturn(bridgeOk("33333333-4444-4555-8666-777777777777", null));
        when(governanceInvitationService.deliverImportRowInvitation(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(GovernanceInvitationService.InvitationDeliveryResult.sent(
                        "inv-3", "kc-user-3", "2026-07-16T00:00:00Z", "ingested", "Invitation sent."));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        assertThat(result.data()).containsEntry("stage", "COMPLETED");
        Map<String, Integer> tally = (Map<String, Integer>) result.data().get("tally");
        assertThat(tally).containsEntry("DONE", 2);

        // Provider issuance is never repeated for the resumed row; the DONE row is untouched.
        verify(varapiClient, never()).createProvider(anyMap());
        verify(varapiClient, never()).getProviderByHealthId(anyString());
        verify(vashandiClient).post(eq("/workforce-profiles/import-bridge"), any());
        ArgumentCaptor<Map<String, Object>> recorded = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(
                eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1/execution-result"), recorded.capture());
        assertThat(recorded.getValue())
                .containsEntry("executionStatus", "DONE")
                .containsEntry("providerPublicId", "PRV-2026-000888")
                .containsEntry("vashandiProfileId", "33333333-4444-4555-8666-777777777777");
    }

    @Test
    @DisplayName("vashandi failure records FAILED_VASHANDI with the issued Provider ID and ends PARTIAL")
    void executeVashandiFailureIsPartial() {
        Map<String, String> staff = Map.of("nationalid", "63-777777G77", "givenname", "Gamu", "familyname", "Karonga",
                "profession", "NURSE", "email", "gamu.karonga@mohcc.gov.zw");
        stubBatch("APPROVED");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(row("row-1", 1, staff, Map.of("matchStatus", "NO_MATCH")))));
        when(varapiClient.createProvider(anyMap())).thenReturn(json(Map.of(
                "providerPublicId", "PRV-2026-000999",
                "impiloHealthId", "ff000000-0000-4000-8000-000000000006")));
        when(vashandiClient.post(eq("/workforce-profiles/import-bridge"), any()))
                .thenReturn(VashandiDtos.UpstreamResult.unavailable("connection refused"));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        assertThat(result.data()).containsEntry("stage", "PARTIAL");
        ArgumentCaptor<Map<String, Object>> recorded = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(
                eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1/execution-result"), recorded.capture());
        assertThat(recorded.getValue())
                .containsEntry("executionStatus", "FAILED_VASHANDI")
                .containsEntry("providerPublicId", "PRV-2026-000999");
        verify(governanceInvitationService, never())
                .deliverImportRowInvitation(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("row without email completes provider + profile but is BLOCKED_MISSING_CONTACT, not failed")
    void executeMissingEmailIsBlockedHonestly() {
        Map<String, String> staff = Map.of("nationalid", "63-888888H88", "givenname", "Hama", "familyname", "Lunga",
                "profession", "NURSE");
        stubBatch("APPROVED");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(row("row-1", 1, staff, Map.of("matchStatus", "NO_MATCH")))));
        when(varapiClient.createProvider(anyMap())).thenReturn(json(Map.of(
                "providerPublicId", "PRV-2026-001000",
                "impiloHealthId", "aa110000-0000-4000-8000-000000000007")));
        when(vashandiClient.post(eq("/workforce-profiles/import-bridge"), any()))
                .thenReturn(bridgeOk("44444444-5555-4666-8777-888888888888", null));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        // Missing contact is not a failure — the batch still completes.
        assertThat(result.data()).containsEntry("stage", "COMPLETED");
        Map<String, Integer> tally = (Map<String, Integer>) result.data().get("tally");
        assertThat(tally).containsEntry("BLOCKED_MISSING_CONTACT", 1);
        verify(governanceInvitationService, never())
                .deliverImportRowInvitation(anyString(), anyString(), anyString(), anyString(), anyString());
        ArgumentCaptor<Map<String, Object>> recorded = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(
                eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1/execution-result"), recorded.capture());
        assertThat(recorded.getValue())
                .containsEntry("executionStatus", "BLOCKED_MISSING_CONTACT")
                .containsEntry("providerPublicId", "PRV-2026-001000");
    }

    @Test
    @DisplayName("ambiguous rows are blocked from execution and the batch is PARTIAL")
    void executeBlocksAmbiguousRows() {
        Map<String, String> staff = Map.of("nationalid", "63-999999I99", "givenname", "Ida", "familyname", "Mhlanga",
                "profession", "NURSE", "email", "ida.mhlanga@mohcc.gov.zw");
        stubBatch("APPROVED");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(row("row-1", 1, staff, Map.of("matchStatus", "AMBIGUOUS")))));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        assertThat(result.data()).containsEntry("stage", "PARTIAL");
        verify(varapiClient, never()).createProvider(anyMap());
        ArgumentCaptor<Map<String, Object>> recorded = ArgumentCaptor.forClass(Map.class);
        verify(workforceGovernanceClient).postJson(
                eq(IMPORTS + "/" + BATCH_ID + "/rows/row-1/execution-result"), recorded.capture());
        assertThat(recorded.getValue()).containsEntry("executionStatus", "BLOCKED_AMBIGUOUS_MATCH");
    }

    @Test
    @DisplayName("execute refuses a batch that is not APPROVED or PARTIAL")
    void executeRequiresApprovedStage() {
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID))
                .thenReturn(json(Map.of("id", BATCH_ID, "stage", "MATCHED", "organisationId", ORG_ID)));

        AdminGovernanceDtos.LookupEnvelope result = service.execute(ACTOR, null, false, BATCH_ID);

        assertThat(result.integrationStatus()).isEqualTo("pending_backend");
        assertThat(result.friendlyMessage()).contains("APPROVED").contains("MATCHED");
        verify(varapiClient, never()).createProvider(anyMap());
    }

    // ── status ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("status rolls up execution and match tallies for the tracker")
    void statusRollsUpTallies() {
        Map<String, String> staff = Map.of("nationalid", "63-000000J00", "givenname", "Jose", "familyname", "Ncube",
                "profession", "NURSE");
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID))
                .thenReturn(json(Map.of("id", BATCH_ID, "stage", "PARTIAL", "organisationId", ORG_ID)));
        when(workforceGovernanceClient.getJson(IMPORTS + "/" + BATCH_ID + "/rows"))
                .thenReturn(json(List.of(
                        row("row-1", 1, staff, Map.of("matchStatus", "MATCHED", "executionStatus", "DONE")),
                        row("row-2", 2, staff, Map.of("matchStatus", "NO_MATCH", "executionStatus", "FAILED_VASHANDI")))));

        AdminGovernanceDtos.LookupEnvelope result = service.status(BATCH_ID);

        assertThat(result.integrationStatus()).isEqualTo("live");
        assertThat(result.data()).containsEntry("stage", "PARTIAL");
        Map<String, Integer> executionTally = (Map<String, Integer>) result.data().get("executionTally");
        assertThat(executionTally).containsEntry("DONE", 1).containsEntry("FAILED_VASHANDI", 1);
        Map<String, Integer> matchTally = (Map<String, Integer>) result.data().get("matchTally");
        assertThat(matchTally).containsEntry("MATCHED", 1).containsEntry("NO_MATCH", 1);
    }
}
