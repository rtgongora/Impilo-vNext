package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.governance.persistence.ImportBatchEntity;
import zw.gov.mohcc.impilo.governance.persistence.ImportBatchRepository;
import zw.gov.mohcc.impilo.governance.persistence.ImportExceptionEntity;
import zw.gov.mohcc.impilo.governance.persistence.ImportExceptionRepository;
import zw.gov.mohcc.impilo.governance.persistence.ImportRowEntity;
import zw.gov.mohcc.impilo.governance.persistence.ImportRowRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ImportBatchService {

    static final String HSC_IMPORT_TYPE = "hsc_employment_records";
    static final String EC_NUMBER_COLUMN = "ec_number";
    static final String OUTCOME_INVALID_EC_NUMBER = "invalid_ec_number";

    static final String WORKFORCE_INTAKE_IMPORT_TYPE = "workforce_intake";

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            "organisation_users", List.of("email", "full_name", "role_template"),
            "hsc_employment_records", List.of("provider_worker_id", "employment_status", "post_title"),
            "council_professional_register", List.of("registration_number", "profession", "registration_status"),
            "facility_staff_list", List.of("provider_worker_id", "department", "role_template"),
            WORKFORCE_INTAKE_IMPORT_TYPE, List.of("nationalid", "givenname", "familyname", "profession")
    );

    /**
     * Workforce-intake pipeline stages. Legal transitions only — an out-of-order
     * PATCH is rejected, never silently coerced. PARTIAL → EXECUTING allows an
     * idempotent re-execution of the failed remainder.
     */
    private static final Map<String, Set<String>> STAGE_TRANSITIONS = Map.of(
            "UPLOADED", Set.of("MAPPED"),
            "MAPPED", Set.of("VALIDATED"),
            "VALIDATED", Set.of("MATCHED"),
            "MATCHED", Set.of("APPROVED"),
            "APPROVED", Set.of("EXECUTING"),
            "EXECUTING", Set.of("COMPLETED", "PARTIAL"),
            "PARTIAL", Set.of("EXECUTING"),
            "COMPLETED", Set.of()
    );

    private final ImportBatchRepository batchRepository;
    private final ImportRowRepository rowRepository;
    private final ImportExceptionRepository exceptionRepository;
    private final ObjectMapper objectMapper;

    public ImportBatchService(ImportBatchRepository batchRepository,
                              ImportRowRepository rowRepository,
                              ImportExceptionRepository exceptionRepository,
                              ObjectMapper objectMapper) {
        this.batchRepository = batchRepository;
        this.rowRepository = rowRepository;
        this.exceptionRepository = exceptionRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportBatchEntity upload(UUID tenantId, UUID organisationId, String uploadedBy, String importType,
                                    String fileName, String csvContent) {
        List<Map<String, String>> rows = parseCsv(csvContent);
        ImportBatchEntity batch = new ImportBatchEntity();
        batch.init(tenantId, organisationId, importType, uploadedBy, fileName, sha256(csvContent), rows.size());
        batch = batchRepository.save(batch);

        int valid = 0;
        int exceptions = 0;
        int duplicates = 0;
        List<String> required = REQUIRED_COLUMNS.getOrDefault(importType, List.of("email"));
        Set<String> seen = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            Map<String, String> row = rows.get(i);
            ImportRowEntity rowEntity = new ImportRowEntity();
            rowEntity.init(batch.getId(), i + 1, writeJson(row));
            String ecIssue = mapEcNumber(importType, row, rowEntity);
            String outcome = ecIssue != null ? ecIssue : determineOutcome(row, required, seen, importType);
            rowEntity.setOutcome(outcome);
            rowEntity.setValidationStatus(outcome.startsWith("invalid") ? "invalid" : "validated");
            rowEntity.setPrecheckStatus("precheck_pending");
            if (outcome.equals("duplicate_possible")) duplicates++;
            if (outcome.startsWith("invalid")) {
                exceptions++;
                ImportExceptionEntity ex = new ImportExceptionEntity();
                ex.init(batch.getId(), rowEntity.getId(), "validation", "error", outcome);
                exceptionRepository.save(ex);
            } else {
                valid++;
            }
            rowRepository.save(rowEntity);
        }

        batch.setCounts(valid, exceptions, duplicates, 0, 0);
        batch.setStatus("validated");
        return batchRepository.save(batch);
    }

    public List<ImportBatchEntity> list(UUID tenantId, UUID organisationId) {
        return batchRepository.findByTenantIdAndOrganisationIdOrderByCreatedAtDesc(tenantId, organisationId);
    }

    public ImportBatchEntity get(UUID id) {
        return batchRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("Import batch not found"));
    }

    public List<ImportRowEntity> rows(UUID batchId) {
        return rowRepository.findByImportBatchIdOrderByRowNumberAsc(batchId);
    }

    public List<ImportExceptionEntity> exceptions(UUID batchId) {
        return exceptionRepository.findByImportBatchIdOrderByCreatedAtAsc(batchId);
    }

    @Transactional
    public ImportBatchEntity approve(UUID batchId, String actorId) {
        ImportBatchEntity batch = get(batchId);
        batch.setStatus("approved");
        batch.setAuditStatus("approved_by_" + actorId);
        if (batch.getStage() != null && canTransition(batch.getStage(), "APPROVED")) {
            batch.setStage("APPROVED");
        }
        return batchRepository.save(batch);
    }

    /**
     * Explicit stage transition for the workforce-intake pipeline.
     * Rejects transitions that are not in {@link #STAGE_TRANSITIONS}.
     */
    @Transactional
    public ImportBatchEntity transitionStage(UUID batchId, String requestedStage) {
        if (requestedStage == null || requestedStage.isBlank()) {
            throw new IllegalArgumentException("stage is required");
        }
        String target = requestedStage.trim().toUpperCase(Locale.ROOT);
        if (!STAGE_TRANSITIONS.containsKey(target)) {
            throw new IllegalArgumentException("Unknown stage: " + target);
        }
        ImportBatchEntity batch = get(batchId);
        String current = batch.getStage() != null ? batch.getStage() : "UPLOADED";
        if (!canTransition(current, target)) {
            throw new IllegalStateException("Illegal stage transition: " + current + " -> " + target);
        }
        batch.setStage(target);
        return batchRepository.save(batch);
    }

    private static boolean canTransition(String current, String target) {
        return STAGE_TRANSITIONS.getOrDefault(current, Set.of()).contains(target);
    }

    /**
     * Applies a column mapping (source header, lower-cased → canonical column key)
     * to every row's raw payload, re-validates against the import type's required
     * columns, and records fresh validation exceptions. The batch moves through
     * MAPPED to VALIDATED in one transaction — both stages are real, the endpoint
     * simply performs them atomically so a half-mapped batch is never observable.
     */
    @Transactional
    public ImportBatchEntity applyColumnMapping(UUID batchId, Map<String, String> columnMapping) {
        ImportBatchEntity batch = get(batchId);
        String current = batch.getStage() != null ? batch.getStage() : "UPLOADED";
        if (!"UPLOADED".equals(current) && !"MAPPED".equals(current) && !"VALIDATED".equals(current)) {
            throw new IllegalStateException("Column mapping can only be changed before matching (stage=" + current + ")");
        }
        Map<String, String> mapping = new LinkedHashMap<>();
        if (columnMapping != null) {
            columnMapping.forEach((source, target) -> {
                if (source != null && target != null && !source.isBlank() && !target.isBlank()) {
                    mapping.put(source.trim().toLowerCase(Locale.ROOT), target.trim().toLowerCase(Locale.ROOT));
                }
            });
        }
        batch.setColumnMapping(mapping);

        List<String> required = REQUIRED_COLUMNS.getOrDefault(batch.getImportType(), List.of("email"));
        List<ImportRowEntity> rows = rowRepository.findByImportBatchIdOrderByRowNumberAsc(batchId);
        int valid = 0;
        int exceptions = 0;
        for (ImportRowEntity row : rows) {
            Map<String, String> raw = readRow(row.getRawPayloadJson());
            Map<String, String> normalized = new LinkedHashMap<>();
            raw.forEach((key, value) -> normalized.put(mapping.getOrDefault(key, key), value));
            row.setNormalizedPayloadJson(writeJson(normalized));

            List<String> missing = required.stream()
                    .filter(col -> normalized.getOrDefault(col, "").isBlank())
                    .toList();
            if (missing.isEmpty()) {
                row.setValidationStatus("validated");
                row.setOutcome("ready_to_invite");
                valid++;
            } else {
                row.setValidationStatus("invalid");
                row.setOutcome("invalid_required_fields");
                exceptions++;
                ImportExceptionEntity ex = new ImportExceptionEntity();
                ex.init(batchId, row.getId(), "mapping_validation", "error",
                        "Missing required column(s) after mapping: " + String.join(", ", missing));
                exceptionRepository.save(ex);
            }
            rowRepository.save(row);
        }
        batch.setCounts(valid, exceptions, batch.getDuplicateCount(), 0, 0);
        batch.setStage("VALIDATED");
        batch.setStatus("validated");
        return batchRepository.save(batch);
    }

    /**
     * Records the per-row execution outcome (Provider ID issuance, Vashandi
     * profile/assignment bridge, invitation) written back by the experience BFF
     * after each idempotent execution step.
     */
    @Transactional
    public ImportRowEntity recordExecutionResult(UUID batchId, UUID rowId, Map<String, Object> request) {
        ImportRowEntity row = rowRepository.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Import row not found"));
        if (!batchId.equals(row.getImportBatchId())) {
            throw new IllegalArgumentException("Import row does not belong to batch");
        }
        if (request.containsKey("executionStatus")) {
            row.setExecutionStatus(stringOrNull(request.get("executionStatus")));
        }
        if (request.containsKey("executionError")) {
            String error = stringOrNull(request.get("executionError"));
            row.setExecutionError(error != null && error.length() > 500 ? error.substring(0, 500) : error);
        }
        if (request.containsKey("providerPublicId")) {
            row.setProviderPublicId(stringOrNull(request.get("providerPublicId")));
        }
        if (request.containsKey("vashandiProfileId")) {
            row.setVashandiProfileId(uuidOrNull(request.get("vashandiProfileId")));
        }
        if (request.containsKey("assignmentId")) {
            row.setAssignmentId(uuidOrNull(request.get("assignmentId")));
        }
        if (request.containsKey("invitationId")) {
            row.setInvitationId(stringOrNull(request.get("invitationId")));
        }
        return rowRepository.save(row);
    }

    private static String stringOrNull(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value);
        return s.isBlank() || "null".equals(s) ? null : s;
    }

    private static UUID uuidOrNull(Object value) {
        String s = stringOrNull(value);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid UUID: " + s);
        }
    }

    private Map<String, String> readRow(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            Map<String, String> row = new LinkedHashMap<>();
            objectMapper.readTree(json).fields().forEachRemaining(entry ->
                    row.put(entry.getKey(), entry.getValue().isNull() ? "" : entry.getValue().asText()));
            return row;
        } catch (Exception e) {
            return Map.of();
        }
    }

    /**
     * Legacy endpoint — does not fabricate invitation ids. Use {@link #recordInvitations} after
     * Keycloak delivery succeeds in experience-bff.
     */
    @Transactional
    public ImportBatchEntity sendInvitations(UUID batchId) {
        ImportBatchEntity batch = get(batchId);
        batch.setStatus("invitations_pending");
        return batchRepository.save(batch);
    }

    @Transactional
    public ImportBatchEntity recordInvitations(UUID batchId, List<Map<String, Object>> deliveries) {
        ImportBatchEntity batch = get(batchId);
        int sent = 0;
        for (Map<String, Object> delivery : deliveries) {
            UUID rowId = UUID.fromString(String.valueOf(delivery.get("rowId")));
            ImportRowEntity row = rowRepository.findById(rowId)
                    .orElseThrow(() -> new IllegalArgumentException("Import row not found: " + rowId));
            if (!batchId.equals(row.getImportBatchId())) {
                throw new IllegalArgumentException("Import row does not belong to batch");
            }
            if (!"ready_to_invite".equals(row.getOutcome()) && !"requires_approval".equals(row.getOutcome())) {
                continue;
            }
            row.setOutcome("invitation_sent");
            row.setInvitationId(String.valueOf(delivery.get("invitationId")));
            mergeInvitationMetadata(row, delivery);
            rowRepository.save(row);
            sent++;
        }
        if (sent > 0) {
            batch.setStatus("invitations_sent");
            batch.setReadyToInviteCount(sent);
        } else {
            batch.setStatus("invitations_pending");
        }
        return batchRepository.save(batch);
    }

    @Transactional
    public ImportRowEntity updateRowInvitation(UUID batchId, UUID rowId, Map<String, Object> request) {
        ImportRowEntity row = rowRepository.findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("Import row not found"));
        if (!batchId.equals(row.getImportBatchId())) {
            throw new IllegalArgumentException("Import row does not belong to batch");
        }
        if (request.containsKey("outcome")) {
            row.setOutcome(String.valueOf(request.get("outcome")));
        }
        if (request.containsKey("invitationId")) {
            row.setInvitationId(String.valueOf(request.get("invitationId")));
        }
        if (request.containsKey("matchStatus")) {
            row.setMatchStatus(stringOrNull(request.get("matchStatus")));
        }
        if (request.containsKey("matchedHealthId")) {
            row.setMatchedHealthId(stringOrNull(request.get("matchedHealthId")));
        }
        if (request.containsKey("duplicateOfRowId")) {
            row.setDuplicateOfRowId(uuidOrNull(request.get("duplicateOfRowId")));
        }
        mergeInvitationMetadata(row, request);
        return rowRepository.save(row);
    }

    private void mergeInvitationMetadata(ImportRowEntity row, Map<String, Object> delivery) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = row.getNormalizedPayloadJson() == null || row.getNormalizedPayloadJson().isBlank()
                    ? new LinkedHashMap<>()
                    : objectMapper.readValue(row.getNormalizedPayloadJson(), Map.class);
            if (delivery.get("expiresAt") != null) payload.put("invitation_expires_at", delivery.get("expiresAt"));
            if (delivery.get("keycloakUserId") != null) payload.put("keycloak_user_id", delivery.get("keycloakUserId"));
            if (delivery.get("invitationStatus") != null) payload.put("invitation_status", delivery.get("invitationStatus"));
            if (delivery.get("invitationAuditStatus") != null) payload.put("invitation_audit_status", delivery.get("invitationAuditStatus"));
            row.setNormalizedPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (Exception ignored) {
            // keep row outcome authoritative even if metadata merge fails
        }
    }

    public Map<String, Object> template(String importType) {
        return Map.of(
                "importType", importType,
                "requiredColumns", REQUIRED_COLUMNS.getOrDefault(importType, List.of("email", "full_name")),
                "supportedFormats", List.of("csv", "json"),
                "xlsxContract", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
    }

    /**
     * Maps the optional {@code ec_number} column on HSC employment imports.
     *
     * <p>A present-but-malformed EC number is an explicit {@code invalid_ec_number}
     * outcome (surfaced as an {@link ImportExceptionEntity} by the caller), never a
     * silent drop. A valid EC number is canonicalised into the row's normalized
     * payload so downstream reconciliation writes the canonical key to
     * {@code wgv_hsc_employment.ec_number}. Absent/blank EC is allowed — the column
     * is optional for legacy HSC extracts.</p>
     *
     * @return the invalid outcome code, or {@code null} when the row is acceptable
     */
    private String mapEcNumber(String importType, Map<String, String> row, ImportRowEntity rowEntity) {
        if (!HSC_IMPORT_TYPE.equals(importType) || !row.containsKey(EC_NUMBER_COLUMN)) {
            return null;
        }
        String raw = row.get(EC_NUMBER_COLUMN);
        String canonical = EcNumbers.canonicalise(raw);
        if (canonical == null) {
            return null; // column present but blank — EC stays optional
        }
        if (!EcNumbers.isValid(canonical)) {
            return OUTCOME_INVALID_EC_NUMBER;
        }
        if (!canonical.equals(raw)) {
            row.put(EC_NUMBER_COLUMN, canonical);
            rowEntity.setNormalizedPayloadJson(writeJson(row));
        }
        return null;
    }

    private String determineOutcome(Map<String, String> row, List<String> required, Set<String> seen, String importType) {
        for (String col : required) {
            if (!row.containsKey(col) || row.get(col).isBlank()) {
                return "invalid_required_fields";
            }
        }
        String key = row.getOrDefault("email", row.getOrDefault("provider_worker_id", row.getOrDefault("registration_number", "")));
        if (!key.isBlank() && !seen.add(key.toLowerCase(Locale.ROOT))) {
            return "duplicate_possible";
        }
        if ("external_partner_users".equals(importType)) {
            if (row.getOrDefault("purpose", "").isBlank() || row.getOrDefault("data_scope", "").isBlank()) {
                return "invalid_required_fields";
            }
        }
        return "ready_to_invite";
    }

    static List<Map<String, String>> parseCsv(String content) {
        if (content == null || content.isBlank()) return List.of();
        String[] lines = content.replace("\r", "").split("\n");
        if (lines.length < 2) return List.of();
        String[] headers = Arrays.stream(lines[0].split(",")).map(h -> h.trim().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] values = lines[i].split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < values.length; c++) {
                row.put(headers[c], values[c].trim());
            }
            rows.add(row);
        }
        return rows;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}
