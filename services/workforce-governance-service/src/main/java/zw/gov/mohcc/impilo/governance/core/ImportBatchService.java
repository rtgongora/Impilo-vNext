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

    private static final Map<String, List<String>> REQUIRED_COLUMNS = Map.of(
            "organisation_users", List.of("email", "full_name", "role_template"),
            "hsc_employment_records", List.of("provider_worker_id", "employment_status", "post_title"),
            "council_professional_register", List.of("registration_number", "profession", "registration_status"),
            "facility_staff_list", List.of("provider_worker_id", "department", "role_template")
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
            String outcome = determineOutcome(row, required, seen, importType);
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
        return batchRepository.save(batch);
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

    public Map<String, Object> template(String importType) {
        return Map.of(
                "importType", importType,
                "requiredColumns", REQUIRED_COLUMNS.getOrDefault(importType, List.of("email", "full_name")),
                "supportedFormats", List.of("csv", "json"),
                "xlsxContract", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        );
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
