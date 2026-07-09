package zw.gov.mohcc.impilo.experience.workforceintake;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceAuditHelper;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceDtos;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernancePolicyService;
import zw.gov.mohcc.impilo.experience.admingovernance.AdminGovernanceResponses;
import zw.gov.mohcc.impilo.experience.admingovernance.CsvImportParser;
import zw.gov.mohcc.impilo.experience.admingovernance.GovernanceInvitationService;
import zw.gov.mohcc.impilo.experience.client.TshepoIdentityServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;
import zw.gov.mohcc.impilo.experience.client.VashandiServiceClient;
import zw.gov.mohcc.impilo.experience.client.WorkforceGovernanceClient;
import zw.gov.mohcc.impilo.experience.session.SessionExperienceService;
import zw.gov.mohcc.impilo.experience.vashandi.VashandiDtos;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Workforce Intake — staged MoHCC staff bootstrap journey.
 *
 * <p>Stateless composition only. The staged batch model (stage, column mapping,
 * per-row match + execution results) is owned by workforce-governance-service
 * ({@code wgv_import_batch}/{@code wgv_import_row}); Provider IDs are issued by
 * VARAPI; workforce profiles/assignments are owned by Vashandi; activation
 * invitations go through {@link GovernanceInvitationService} (Keycloak +
 * notification-service).</p>
 *
 * <p>Pipeline: upload → column mapping + validation → in-batch dedupe +
 * Health-ID matching → approve → per-row idempotent execution → status tracker.</p>
 */
@Service
public class WorkforceIntakeService {

    private static final Logger log = LoggerFactory.getLogger(WorkforceIntakeService.class);

    static final String IMPORT_TYPE = "workforce_intake";
    static final int MAX_ROWS = 2000;
    static final String SOURCE_PAGE = "/admin/workforce-intake";
    private static final String IMPORTS = "/v1/internal/governance/imports";

    // Row execution statuses (terminal per execution attempt).
    static final String EXEC_DONE = "DONE";
    static final String EXEC_BLOCKED_MISSING_CONTACT = "BLOCKED_MISSING_CONTACT";
    static final String EXEC_BLOCKED_AMBIGUOUS = "BLOCKED_AMBIGUOUS_MATCH";
    static final String EXEC_SKIPPED_DUPLICATE = "SKIPPED_DUPLICATE_IN_BATCH";
    static final String EXEC_SKIPPED_INVALID = "SKIPPED_INVALID_ROW";
    static final String EXEC_FAILED_VARAPI = "FAILED_VARAPI";
    static final String EXEC_FAILED_VASHANDI = "FAILED_VASHANDI";
    static final String EXEC_FAILED_INVITATION = "FAILED_INVITATION";

    private final WorkforceGovernanceClient workforceGovernanceClient;
    private final TshepoIdentityServiceClient tshepoIdentityClient;
    private final VarapiServiceClient varapiClient;
    private final VashandiServiceClient vashandiClient;
    private final GovernanceInvitationService governanceInvitationService;
    private final AdminGovernancePolicyService policyService;
    private final AdminGovernanceAuditHelper auditHelper;
    private final SessionExperienceService sessionExperienceService;
    private final ObjectMapper objectMapper;

    public WorkforceIntakeService(WorkforceGovernanceClient workforceGovernanceClient,
                                  TshepoIdentityServiceClient tshepoIdentityClient,
                                  VarapiServiceClient varapiClient,
                                  VashandiServiceClient vashandiClient,
                                  GovernanceInvitationService governanceInvitationService,
                                  AdminGovernancePolicyService policyService,
                                  AdminGovernanceAuditHelper auditHelper,
                                  SessionExperienceService sessionExperienceService,
                                  ObjectMapper objectMapper) {
        this.workforceGovernanceClient = workforceGovernanceClient;
        this.tshepoIdentityClient = tshepoIdentityClient;
        this.varapiClient = varapiClient;
        this.vashandiClient = vashandiClient;
        this.governanceInvitationService = governanceInvitationService;
        this.policyService = policyService;
        this.auditHelper = auditHelper;
        this.sessionExperienceService = sessionExperienceService;
        this.objectMapper = objectMapper;
    }

    // ── 1. Upload ────────────────────────────────────────────────────────────

    public AdminGovernanceDtos.LookupEnvelope createBatch(String actorId, String providerId, boolean hasFacility,
                                                          String organisationId, String fileName,
                                                          String csvContent, List<Map<String, String>> jsonRows) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility,
                "WORKFORCE_INTAKE_UPLOAD", organisationId);
        if (!decision.allowed()) {
            return unavailable(deniedMessage(decision, "Workforce intake upload blocked by policy."));
        }

        List<Map<String, String>> rows = jsonRows != null && !jsonRows.isEmpty()
                ? normalizeJsonRows(jsonRows)
                : CsvImportParser.parseCsv(csvContent);
        if (rows.isEmpty()) {
            return unavailable("No staff rows found. Provide a CSV with a header row or a JSON rows array.");
        }
        if (rows.size() > MAX_ROWS) {
            return unavailable("Batch too large: " + rows.size() + " rows. The workforce intake journey caps a "
                    + "single batch at " + MAX_ROWS + " rows — split the file and upload in parts.");
        }

        Map<String, Object> body = Map.of(
                "organisationId", organisationId,
                "uploadedByUserId", actorId,
                "importType", IMPORT_TYPE,
                "sourceFileName", fileName != null ? fileName : "workforce-intake.csv",
                "csvContent", toCanonicalCsv(rows)
        );
        JsonNode batch = workforceGovernanceClient.postJson(IMPORTS, body);
        if (batch == null || batch.isNull()) {
            return unavailable("Workforce governance is unavailable — the batch was not staged. Retry when the service is reachable.");
        }
        var audit = auditHelper.emit("workforce_intake.batch_uploaded", actorId, batch.path("id").asText(),
                Map.of("organisationId", organisationId, "rowCount", rows.size()));
        return live(Map.of(
                "batch", toMap(batch),
                "sourceOfRecordStatus", "workforce_governance",
                "auditStatus", audit.auditStatus()
        ));
    }

    // ── 2. Column mapping + validation report ───────────────────────────────

    public AdminGovernanceDtos.LookupEnvelope applyMapping(String actorId, String providerId, boolean hasFacility,
                                                           String batchId, Map<String, String> columnMapping) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility,
                "WORKFORCE_INTAKE_MAPPING", batchId);
        if (!decision.allowed()) {
            return unavailable(deniedMessage(decision, "Column mapping blocked by policy."));
        }
        JsonNode batch = workforceGovernanceClient.patchJson(IMPORTS + "/" + batchId + "/column-mapping",
                Map.of("columnMapping", columnMapping != null ? columnMapping : Map.of()));
        if (batch == null || batch.isNull()) {
            return unavailable("Column mapping could not be applied — workforce governance rejected it or is unavailable.");
        }
        JsonNode rows = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId + "/rows");
        JsonNode exceptions = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId + "/exceptions");

        List<Map<String, Object>> rowReport = new ArrayList<>();
        int valid = 0;
        int invalid = 0;
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                boolean rowValid = "validated".equals(row.path("validationStatus").asText(""));
                if (rowValid) valid++; else invalid++;
                rowReport.add(Map.of(
                        "rowId", row.path("id").asText(),
                        "rowNumber", row.path("rowNumber").asInt(),
                        "validationStatus", row.path("validationStatus").asText(""),
                        "outcome", row.path("outcome").asText(""),
                        "payload", parsePayload(row)
                ));
            }
        }
        var audit = auditHelper.emit("workforce_intake.column_mapping_applied", actorId, batchId,
                Map.of("validRows", valid, "invalidRows", invalid));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batch", toMap(batch));
        data.put("validationReport", Map.of(
                "totalRows", valid + invalid,
                "validRows", valid,
                "invalidRows", invalid,
                "rows", rowReport,
                "exceptions", exceptions != null && exceptions.isArray() ? toList(exceptions) : List.of()
        ));
        data.put("auditStatus", audit.auditStatus());
        return live(data);
    }

    // ── 3. Duplicate detection + Health-ID matching preview ─────────────────

    public AdminGovernanceDtos.LookupEnvelope match(String actorId, String providerId, boolean hasFacility,
                                                    String tenantId, String batchId) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility,
                "WORKFORCE_INTAKE_MATCH", batchId);
        if (!decision.allowed()) {
            return unavailable(deniedMessage(decision, "Match preview blocked by policy."));
        }
        JsonNode batch = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId);
        if (batch == null || batch.isNull()) {
            return unavailable("Import batch unavailable.");
        }
        String stage = batch.path("stage").asText("");
        if (!"VALIDATED".equals(stage) && !"MATCHED".equals(stage)) {
            return unavailable("Matching requires a validated batch (current stage: " + stage + "). Apply column mapping first.");
        }
        JsonNode rows = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId + "/rows");
        if (rows == null || !rows.isArray()) {
            return unavailable("Import rows unavailable.");
        }

        // In-batch duplicate detection: first occurrence of a nationalId/email wins.
        Map<String, String> firstRowByNationalId = new LinkedHashMap<>();
        Map<String, String> firstRowByEmail = new LinkedHashMap<>();
        int matched = 0;
        int noMatch = 0;
        int ambiguous = 0;
        int duplicates = 0;
        int invalid = 0;
        List<Map<String, Object>> preview = new ArrayList<>();

        for (JsonNode row : rows) {
            String rowId = row.path("id").asText();
            Map<String, String> payload = parsePayload(row);
            if (!"validated".equals(row.path("validationStatus").asText(""))) {
                invalid++;
                preview.add(previewRow(row, payload, "INVALID", null, null));
                continue;
            }

            String duplicateOfRowId = null;
            String nationalId = normalizeKeyValue(payload.get("nationalid"));
            String email = normalizeKeyValue(payload.get("email"));
            if (nationalId != null) {
                String first = firstRowByNationalId.putIfAbsent(nationalId, rowId);
                if (first != null) duplicateOfRowId = first;
            }
            if (duplicateOfRowId == null && email != null) {
                String first = firstRowByEmail.putIfAbsent(email, rowId);
                if (first != null) duplicateOfRowId = first;
            }

            String matchStatus;
            String matchedHealthId = null;
            if (duplicateOfRowId != null) {
                matchStatus = "DUPLICATE_IN_BATCH";
                duplicates++;
            } else {
                MatchResult result = matchRow(tenantId, payload);
                matchStatus = result.status();
                matchedHealthId = result.healthId();
                switch (matchStatus) {
                    case "MATCHED" -> matched++;
                    case "AMBIGUOUS" -> ambiguous++;
                    default -> noMatch++;
                }
            }

            Map<String, Object> patch = new LinkedHashMap<>();
            patch.put("matchStatus", matchStatus);
            patch.put("matchedHealthId", matchedHealthId);
            patch.put("duplicateOfRowId", duplicateOfRowId);
            JsonNode saved = workforceGovernanceClient.patchJson(IMPORTS + "/" + batchId + "/rows/" + rowId, patch);
            if (saved == null) {
                return unavailable("Match result for row " + row.path("rowNumber").asInt()
                        + " could not be persisted — matching aborted, re-run when workforce governance is reachable.");
            }
            preview.add(previewRow(row, payload, matchStatus, matchedHealthId, duplicateOfRowId));
        }

        if ("VALIDATED".equals(stage)) {
            JsonNode staged = workforceGovernanceClient.patchJson(IMPORTS + "/" + batchId + "/stage",
                    Map.of("stage", "MATCHED"));
            if (staged == null) {
                return unavailable("Row matches were saved but the batch stage could not advance to MATCHED. Re-run matching.");
            }
        }
        var audit = auditHelper.emit("workforce_intake.batch_matched", actorId, batchId, Map.of(
                "matched", matched, "noMatch", noMatch, "ambiguous", ambiguous,
                "duplicatesInBatch", duplicates, "invalid", invalid));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", Map.of(
                "matched", matched,
                "noMatch", noMatch,
                "ambiguous", ambiguous,
                "duplicatesInBatch", duplicates,
                "invalid", invalid
        ));
        data.put("rows", preview);
        data.put("stage", "MATCHED");
        data.put("auditStatus", audit.auditStatus());
        return live(data);
    }

    /**
     * Resolves a person anchor for a staff row through the C3 silent identifier
     * resolution chain, trying every identifier the row carries: an explicit
     * Health ID, a council registration number (resolves the professional profile's
     * person anchor via VARAPI), and email. National-ID resolution has no platform
     * surface (VITO stores NID as an internal identifier without a lookup endpoint),
     * so a row carrying only a national ID resolves to NO_MATCH and is issued a new
     * anchor at execution time.
     *
     * <p>AMBIGUOUS means the row's identifiers resolved to <b>different</b> people —
     * that contradiction needs human review, never automatic issuance.</p>
     */
    private MatchResult matchRow(String tenantId, Map<String, String> payload) {
        List<String> resolvedHealthIds = new ArrayList<>();
        resolveKind(tenantId, "HEALTH_ID", payload.get("healthid"), resolvedHealthIds);
        resolveKind(tenantId, "COUNCIL_NUMBER", payload.get("councilnumber"), resolvedHealthIds);
        resolveKind(tenantId, "EMAIL", payload.get("email"), resolvedHealthIds);

        List<String> distinct = resolvedHealthIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return new MatchResult("NO_MATCH", null);
        }
        if (distinct.size() > 1) {
            return new MatchResult("AMBIGUOUS", null);
        }
        return new MatchResult("MATCHED", distinct.get(0));
    }

    private void resolveKind(String tenantId, String kind, String value, List<String> resolvedHealthIds) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            JsonNode response = tshepoIdentityClient.resolveIdentifier(Map.of(
                    "tenantId", tenantId,
                    "kind", kind,
                    "value", value.trim()));
            if (response != null && response.path("resolved").asBoolean(false)) {
                String healthId = response.path("personRef").path("healthId").asText(null);
                if (healthId != null && !healthId.isBlank()) {
                    resolvedHealthIds.add(healthId);
                }
            }
        } catch (Exception e) {
            log.warn("Identifier resolution ({}) failed during workforce intake match: {}", kind, e.getMessage());
        }
    }

    // ── 4. Approve ───────────────────────────────────────────────────────────

    public AdminGovernanceDtos.ActionResponse approve(String actorId, String providerId, boolean hasFacility,
                                                      String batchId) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility,
                "WORKFORCE_INTAKE_APPROVE", batchId);
        if (!decision.allowed()) {
            return AdminGovernanceResponses.denied(deniedMessage(decision, "Approval blocked by policy."), decision);
        }
        JsonNode batch = workforceGovernanceClient.postJson(IMPORTS + "/" + batchId + "/approve",
                Map.of("actorId", actorId));
        if (batch == null) {
            return AdminGovernanceResponses.pendingBackend("Approval could not be persisted.", decision);
        }
        var audit = auditHelper.emit("workforce_intake.batch_approved", actorId, batchId, Map.of());
        return AdminGovernanceResponses.of(
                "completed", batchId, null, null, "Batch approved",
                "The staged staff list is approved. Execution issues Provider IDs, creates workforce profiles and dispatches activation invitations.",
                List.of("Execute intake"), null, decision, audit, "live", Map.of("batch", toMap(batch)));
    }

    // ── 5. Execute (idempotent, resumable) ───────────────────────────────────

    public AdminGovernanceDtos.LookupEnvelope execute(String actorId, String providerId, boolean hasFacility,
                                                      String batchId) {
        AdminGovernanceDtos.PolicyDecision decision = precheck(actorId, providerId, hasFacility,
                "WORKFORCE_INTAKE_EXECUTE", batchId);
        if (!decision.allowed()) {
            return unavailable(deniedMessage(decision, "Execution blocked by policy."));
        }
        JsonNode batch = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId);
        if (batch == null || batch.isNull()) {
            return unavailable("Import batch unavailable.");
        }
        String stage = batch.path("stage").asText("");
        if (!"APPROVED".equals(stage) && !"PARTIAL".equals(stage)) {
            return unavailable("Execution requires an APPROVED batch (current stage: " + stage + ").");
        }
        String organisationId = batch.path("organisationId").asText("");
        if (organisationId.isBlank()) {
            return unavailable("Batch organisation is missing — cannot attribute workforce membership.");
        }
        JsonNode rows = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId + "/rows");
        if (rows == null || !rows.isArray()) {
            return unavailable("Import rows unavailable.");
        }
        JsonNode executing = workforceGovernanceClient.patchJson(IMPORTS + "/" + batchId + "/stage",
                Map.of("stage", "EXECUTING"));
        if (executing == null) {
            return unavailable("Batch could not enter EXECUTING stage.");
        }

        Map<String, Integer> tally = new LinkedHashMap<>();
        List<Map<String, Object>> results = new ArrayList<>();
        int failures = 0;
        for (JsonNode row : rows) {
            Map<String, Object> outcome = executeRow(batchId, organisationId, row);
            String status = String.valueOf(outcome.get("executionStatus"));
            tally.merge(status, 1, Integer::sum);
            if (status.startsWith("FAILED_") || EXEC_BLOCKED_AMBIGUOUS.equals(status)) {
                failures++;
            }
            results.add(outcome);
        }

        String finalStage = failures == 0 ? "COMPLETED" : "PARTIAL";
        JsonNode finished = workforceGovernanceClient.patchJson(IMPORTS + "/" + batchId + "/stage",
                Map.of("stage", finalStage));
        var audit = auditHelper.emit("workforce_intake.batch_executed", actorId, batchId, Map.of(
                "stage", finalStage, "tally", tally));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", finished != null ? finished.path("stage").asText(finalStage) : finalStage);
        data.put("tally", tally);
        data.put("rows", results);
        data.put("auditStatus", audit.auditStatus());
        return live(data);
    }

    /**
     * Executes one staff row: (a) VARAPI Provider ID issuance, (b) Vashandi
     * profile/membership/assignment via the single-row import bridge, (c)
     * activation invitation, (d) execution-result write-back. Idempotent —
     * a row already DONE is skipped, and a re-run resumes from the first step
     * that has no recorded artefact (an issued Provider ID is never re-issued).
     */
    private Map<String, Object> executeRow(String batchId, String organisationId, JsonNode row) {
        String rowId = row.path("id").asText();
        int rowNumber = row.path("rowNumber").asInt();
        String existingStatus = row.path("executionStatus").asText("");
        Map<String, String> payload = parsePayload(row);

        if (EXEC_DONE.equals(existingStatus) || EXEC_BLOCKED_MISSING_CONTACT.equals(existingStatus)) {
            return rowResult(rowId, rowNumber, existingStatus, "Already executed — skipped.", row.path("providerPublicId").asText(null));
        }
        if (!"validated".equals(row.path("validationStatus").asText(""))) {
            recordResult(batchId, rowId, Map.of("executionStatus", EXEC_SKIPPED_INVALID));
            return rowResult(rowId, rowNumber, EXEC_SKIPPED_INVALID, "Row failed validation and was not executed.", null);
        }
        String matchStatus = row.path("matchStatus").asText("");
        if ("DUPLICATE_IN_BATCH".equals(matchStatus)) {
            recordResult(batchId, rowId, Map.of("executionStatus", EXEC_SKIPPED_DUPLICATE));
            return rowResult(rowId, rowNumber, EXEC_SKIPPED_DUPLICATE, "Duplicate of another row in this batch.", null);
        }
        if ("AMBIGUOUS".equals(matchStatus)) {
            recordResult(batchId, rowId, Map.of(
                    "executionStatus", EXEC_BLOCKED_AMBIGUOUS,
                    "executionError", "Row identifiers resolved to different people — resolve manually before execution."));
            return rowResult(rowId, rowNumber, EXEC_BLOCKED_AMBIGUOUS, "Identifiers resolved to different people.", null);
        }

        String matchedHealthId = row.path("matchedHealthId").asText(null);

        // (a) Provider ID issuance — reuse before create, never re-issue.
        String providerPublicId = blankToNull(row.path("providerPublicId").asText(null));
        String personHealthId = matchedHealthId;
        if (providerPublicId == null) {
            try {
                if (matchedHealthId != null && !matchedHealthId.isBlank()) {
                    JsonNode existing = varapiClient.getProviderByHealthId(matchedHealthId);
                    if (existing != null && !existing.isNull() && existing.hasNonNull("providerPublicId")) {
                        providerPublicId = existing.path("providerPublicId").asText();
                    }
                }
                if (providerPublicId == null) {
                    JsonNode created = varapiClient.createProvider(providerCreateRequest(matchedHealthId, payload));
                    if (created == null || created.isNull() || !created.hasNonNull("providerPublicId")) {
                        throw new IllegalStateException("VARAPI returned no providerPublicId");
                    }
                    providerPublicId = created.path("providerPublicId").asText();
                    String issuedHealthId = created.path("impiloHealthId").asText(null);
                    if (personHealthId == null && issuedHealthId != null && !issuedHealthId.isBlank()) {
                        personHealthId = issuedHealthId;
                    }
                }
            } catch (Exception e) {
                recordResult(batchId, rowId, Map.of(
                        "executionStatus", EXEC_FAILED_VARAPI,
                        "executionError", "Provider issuance failed: " + safeMessage(e)));
                return rowResult(rowId, rowNumber, EXEC_FAILED_VARAPI, safeMessage(e), null);
            }
        } else if (personHealthId == null) {
            JsonNode existing = varapiClient.getProvider(providerPublicId);
            if (existing != null && existing.hasNonNull("impiloHealthId")) {
                personHealthId = existing.path("impiloHealthId").asText();
            }
        }

        // (b) Vashandi profile + membership (+ facility assignment when resolvable) — one row per call.
        String vashandiProfileId = blankToNull(row.path("vashandiProfileId").asText(null));
        String assignmentId = blankToNull(row.path("assignmentId").asText(null));
        if (vashandiProfileId == null) {
            VashandiDtos.UpstreamResult bridge = vashandiClient.post("/workforce-profiles/import-bridge",
                    importBridgeRequest(batchId, organisationId, personHealthId, providerPublicId, payload));
            if (bridge.status() != VashandiDtos.IntegrationStatus.LIVE || bridge.data() == null) {
                recordResult(batchId, rowId, mapOfNullable(
                        "executionStatus", EXEC_FAILED_VASHANDI,
                        "executionError", "Vashandi import bridge failed: " + (bridge.message() != null ? bridge.message() : "unavailable"),
                        "providerPublicId", providerPublicId));
                return rowResult(rowId, rowNumber, EXEC_FAILED_VASHANDI, bridge.message(), providerPublicId);
            }
            JsonNode profileIds = bridge.data().path("profileIds");
            JsonNode assignmentIds = bridge.data().path("assignmentIds");
            vashandiProfileId = profileIds.isArray() && profileIds.size() > 0 ? profileIds.get(0).asText() : null;
            assignmentId = assignmentIds.isArray() && assignmentIds.size() > 0 ? assignmentIds.get(0).asText() : null;
        }

        // (c) Activation invitation — a missing contact is blocked honestly, not failed.
        String email = blankToNull(payload.get("email"));
        String invitationId = null;
        String executionStatus;
        String executionError = null;
        if (email == null) {
            executionStatus = EXEC_BLOCKED_MISSING_CONTACT;
        } else {
            String displayName = (payload.getOrDefault("givenname", "") + " " + payload.getOrDefault("familyname", "")).trim();
            GovernanceInvitationService.InvitationDeliveryResult delivery =
                    governanceInvitationService.deliverImportRowInvitation(
                            organisationId, batchId, email, displayName.isBlank() ? email : displayName, IMPORT_TYPE);
            if (delivery.activated()) {
                executionStatus = EXEC_DONE;
                invitationId = delivery.invitationId();
            } else {
                executionStatus = EXEC_FAILED_INVITATION;
                executionError = delivery.friendlyMessage();
            }
        }

        // (d) Record the outcome on the wgv row (source of record for the tracker).
        recordResult(batchId, rowId, mapOfNullable(
                "executionStatus", executionStatus,
                "executionError", executionError,
                "providerPublicId", providerPublicId,
                "vashandiProfileId", vashandiProfileId,
                "assignmentId", assignmentId,
                "invitationId", invitationId));
        Map<String, Object> result = rowResult(rowId, rowNumber, executionStatus,
                executionError != null ? executionError : "Provider " + providerPublicId + " issued and workforce profile staged.",
                providerPublicId);
        if (vashandiProfileId != null) result.put("vashandiProfileId", vashandiProfileId);
        if (assignmentId != null) result.put("assignmentId", assignmentId);
        if (invitationId != null) result.put("invitationId", invitationId);
        return result;
    }

    private Map<String, Object> providerCreateRequest(String matchedHealthId, Map<String, String> payload) {
        Map<String, Object> request = new LinkedHashMap<>();
        if (matchedHealthId != null && !matchedHealthId.isBlank()) {
            request.put("impiloHealthId", matchedHealthId);
        }
        request.put("givenName", payload.getOrDefault("givenname", ""));
        request.put("familyName", payload.getOrDefault("familyname", ""));
        request.put("nationalId", payload.getOrDefault("nationalid", ""));
        request.put("profession", payload.getOrDefault("profession", ""));
        putIfPresent(request, "cadre", payload.get("cadre"));
        putIfPresent(request, "email", payload.get("email"));
        putIfPresent(request, "phone", payload.get("phone"));
        putIfPresent(request, "practiceNumber", payload.get("councilnumber"));
        return request;
    }

    private Map<String, Object> importBridgeRequest(String batchId, String organisationId,
                                                    String personHealthId, String providerPublicId,
                                                    Map<String, String> payload) {
        Map<String, Object> bridgeRow = new LinkedHashMap<>();
        putIfPresent(bridgeRow, "healthId", personHealthId);
        putIfPresent(bridgeRow, "providerWorkerId", providerPublicId);
        bridgeRow.put("organisationId", organisationId);
        bridgeRow.put("organisationType", "MOHCC");
        bridgeRow.put("membershipType", "EMPLOYEE");
        bridgeRow.put("workerType", "HEALTH_WORKER");
        putIfPresent(bridgeRow, "profession", payload.get("profession"));
        putIfPresent(bridgeRow, "cadre", payload.get("cadre"));
        bridgeRow.put("currentStatus", "active");
        String facilityId = uuidOrNullString(payload.get("facilitycode"));
        if (facilityId != null) {
            bridgeRow.put("assignmentType", "FACILITY_POSTING");
            bridgeRow.put("facilityId", facilityId);
            putIfPresent(bridgeRow, "roleTemplateId", payload.get("cadre"));
        }
        return Map.of("source", "workforce-intake:" + batchId, "rows", List.of(bridgeRow));
    }

    // ── 6. Status tracker ────────────────────────────────────────────────────

    public AdminGovernanceDtos.LookupEnvelope status(String batchId) {
        JsonNode batch = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId);
        if (batch == null || batch.isNull()) {
            return unavailable("Import batch unavailable.");
        }
        JsonNode rows = workforceGovernanceClient.getJson(IMPORTS + "/" + batchId + "/rows");
        Map<String, Integer> executionTally = new LinkedHashMap<>();
        Map<String, Integer> matchTally = new LinkedHashMap<>();
        List<Map<String, Object>> rowViews = new ArrayList<>();
        if (rows != null && rows.isArray()) {
            for (JsonNode row : rows) {
                String exec = row.path("executionStatus").asText("");
                String match = row.path("matchStatus").asText("");
                if (!exec.isBlank()) executionTally.merge(exec, 1, Integer::sum);
                if (!match.isBlank()) matchTally.merge(match, 1, Integer::sum);
                Map<String, Object> view = new LinkedHashMap<>();
                view.put("rowId", row.path("id").asText());
                view.put("rowNumber", row.path("rowNumber").asInt());
                view.put("payload", parsePayload(row));
                view.put("validationStatus", row.path("validationStatus").asText(""));
                view.put("matchStatus", match);
                view.put("matchedHealthId", row.path("matchedHealthId").asText(null));
                view.put("providerPublicId", row.path("providerPublicId").asText(null));
                view.put("vashandiProfileId", row.path("vashandiProfileId").asText(null));
                view.put("assignmentId", row.path("assignmentId").asText(null));
                view.put("executionStatus", exec);
                view.put("executionError", row.path("executionError").asText(null));
                view.put("invitationId", row.path("invitationId").asText(null));
                rowViews.add(view);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("batch", toMap(batch));
        data.put("stage", batch.path("stage").asText(""));
        data.put("executionTally", executionTally);
        data.put("matchTally", matchTally);
        data.put("rows", rowViews);
        return live(data);
    }

    public AdminGovernanceDtos.LookupEnvelope template() {
        JsonNode template = workforceGovernanceClient.getJson(IMPORTS + "/templates/" + IMPORT_TYPE);
        if (template == null) {
            return unavailable("Import template unavailable.");
        }
        return live(toMap(template));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    record MatchResult(String status, String healthId) {}

    private AdminGovernanceDtos.PolicyDecision precheck(String actorId, String providerId, boolean hasFacility,
                                                        String action, String subjectRef) {
        return policyService.evaluate(
                sessionExperienceService.buildExperienceContract(actorId, null, providerId, hasFacility),
                new AdminGovernanceDtos.PrecheckRequest(
                        action, SOURCE_PAGE, subjectRef, null, null, null, null, null, null, null,
                        "HIGH", Map.of("importType", IMPORT_TYPE)));
    }

    private void recordResult(String batchId, String rowId, Map<String, Object> body) {
        JsonNode saved = workforceGovernanceClient.postJson(
                IMPORTS + "/" + batchId + "/rows/" + rowId + "/execution-result", body);
        if (saved == null) {
            log.warn("Execution result for row {} of batch {} could not be recorded", rowId, batchId);
        }
    }

    private static Map<String, Object> rowResult(String rowId, int rowNumber, String executionStatus,
                                                 String message, String providerPublicId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowId", rowId);
        result.put("rowNumber", rowNumber);
        result.put("executionStatus", executionStatus);
        if (message != null) result.put("message", message);
        if (providerPublicId != null) result.put("providerPublicId", providerPublicId);
        return result;
    }

    private Map<String, String> parsePayload(JsonNode row) {
        String json = row.path("normalizedPayloadJson").asText(null);
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> payload = new LinkedHashMap<>();
            objectMapper.readTree(json).fields().forEachRemaining(entry ->
                    payload.put(entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asText()));
            return payload;
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static Map<String, Object> previewRow(JsonNode row, Map<String, String> payload,
                                                  String matchStatus, String matchedHealthId, String duplicateOfRowId) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("rowId", row.path("id").asText());
        view.put("rowNumber", row.path("rowNumber").asInt());
        view.put("payload", payload);
        view.put("matchStatus", matchStatus);
        if (matchedHealthId != null) view.put("matchedHealthId", matchedHealthId);
        if (duplicateOfRowId != null) view.put("duplicateOfRowId", duplicateOfRowId);
        return view;
    }

    private static List<Map<String, String>> normalizeJsonRows(List<Map<String, String>> rows) {
        List<Map<String, String>> normalized = new ArrayList<>();
        for (Map<String, String> row : rows) {
            Map<String, String> lower = new LinkedHashMap<>();
            row.forEach((key, value) -> {
                if (key != null) {
                    lower.put(key.trim().toLowerCase(Locale.ROOT), value != null ? value.trim() : "");
                }
            });
            normalized.add(lower);
        }
        return normalized;
    }

    /**
     * Serialises parsed rows back to a canonical CSV for the wgv upload contract
     * (header union in first-seen order; commas inside values are not expected in
     * the intake headers and are stripped to keep the row aligned).
     */
    private static String toCanonicalCsv(List<Map<String, String>> rows) {
        List<String> headers = new ArrayList<>();
        for (Map<String, String> row : rows) {
            for (String key : row.keySet()) {
                if (!headers.contains(key)) headers.add(key);
            }
        }
        StringBuilder csv = new StringBuilder(String.join(",", headers)).append("\n");
        for (Map<String, String> row : rows) {
            List<String> values = new ArrayList<>();
            for (String header : headers) {
                String value = row.getOrDefault(header, "");
                values.add(value.replace(",", " ").replace("\n", " ").trim());
            }
            csv.append(String.join(",", values)).append("\n");
        }
        return csv.toString();
    }

    private static String normalizeKeyValue(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String uuidOrNullString(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return java.util.UUID.fromString(value.trim()).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static void putIfPresent(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value);
        }
    }

    private static Map<String, Object> mapOfNullable(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            if (pairs[i + 1] != null) {
                map.put(String.valueOf(pairs[i]), pairs[i + 1]);
            }
        }
        return map;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        return message.length() > 400 ? message.substring(0, 400) : message;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(JsonNode node) {
        return objectMapper.convertValue(node, Map.class);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toList(JsonNode node) {
        return objectMapper.convertValue(node, List.class);
    }

    private static String deniedMessage(AdminGovernanceDtos.PolicyDecision decision, String fallback) {
        return decision.warnings() != null && !decision.warnings().isEmpty() ? decision.warnings().get(0) : fallback;
    }

    private static AdminGovernanceDtos.LookupEnvelope unavailable(String message) {
        return new AdminGovernanceDtos.LookupEnvelope("pending_backend", message, Map.of());
    }

    private static AdminGovernanceDtos.LookupEnvelope live(Map<String, Object> data) {
        return new AdminGovernanceDtos.LookupEnvelope("live", null, data);
    }
}
