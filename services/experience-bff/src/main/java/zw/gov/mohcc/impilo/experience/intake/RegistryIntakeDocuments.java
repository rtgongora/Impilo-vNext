package zw.gov.mohcc.impilo.experience.intake;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JSON-friendly DTOs for Redis-backed registry intake orchestration on the BFF.
 * Sovereign registries (Vito, Varapi, Tuso, Indawo) remain canonical; this layer tracks
 * progressive intake, import jobs, and self-help tickets between UI and downstream APIs.
 */
public final class RegistryIntakeDocuments {

    private RegistryIntakeDocuments() {
    }

    public record IntakeSessionDocument(
            String id,
            String intakeType,
            String targetRegistry,
            String initiatedChannel,
            String workflowState,
            String completionState,
            String linkedHealthId,
            String linkedEntityId,
            String initiatedBy,
            String provenance,
            String notes,
            Instant createdAt,
            Instant updatedAt,
            List<Map<String, Object>> eventLog
    ) {
        public static IntakeSessionDocument create(
                String id,
                String intakeType,
                String targetRegistry,
                String initiatedChannel,
                String initiatedBy,
                String provenance,
                String notes) {
            Instant now = Instant.now();
            return new IntakeSessionDocument(
                    id,
                    intakeType,
                    targetRegistry,
                    initiatedChannel,
                    "DRAFT",
                    "MINIMUM_VIABLE",
                    null,
                    null,
                    initiatedBy,
                    provenance != null ? provenance : "SELF_HELP",
                    notes,
                    now,
                    now,
                    new ArrayList<>());
        }

        public IntakeSessionDocument withPatch(
                String workflowState,
                String completionState,
                String linkedHealthId,
                String linkedEntityId,
                String notes) {
            return new IntakeSessionDocument(
                    id,
                    intakeType,
                    targetRegistry,
                    initiatedChannel,
                    workflowState != null ? workflowState : this.workflowState,
                    completionState != null ? completionState : this.completionState,
                    linkedHealthId != null ? linkedHealthId : this.linkedHealthId,
                    linkedEntityId != null ? linkedEntityId : this.linkedEntityId,
                    initiatedBy,
                    provenance,
                    notes != null ? notes : this.notes,
                    createdAt,
                    Instant.now(),
                    eventLog);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ImportJobDocument(
            String id,
            String importType,
            String targetRegistry,
            String status,
            String createdBy,
            Instant createdAt,
            Instant updatedAt,
            int rowCount,
            int previewLimit,
            Map<String, Object> previewSummary,
            List<Map<String, Object>> previewRows,
            List<Map<String, Object>> rowResults,
            String notes,
            /** Landela document UUID when CSV is stored out-of-band. */
            String payloadRef,
            /** INLINE, LANDELA, or NONE. */
            String payloadSource
    ) {
        public static ImportJobDocument newJob(
                String id,
                String importType,
                String targetRegistry,
                String createdBy,
                int rowCount,
                Map<String, Object> previewSummary,
                List<Map<String, Object>> previewRows,
                String notes,
                String payloadRef,
                String payloadSource) {
            Instant now = Instant.now();
            return new ImportJobDocument(
                    id,
                    importType,
                    targetRegistry,
                    "PREVIEW_READY",
                    createdBy,
                    now,
                    now,
                    rowCount,
                    previewRows.size(),
                    previewSummary,
                    previewRows,
                    new ArrayList<>(),
                    notes,
                    payloadRef,
                    payloadSource != null ? payloadSource : "NONE");
        }

        public ImportJobDocument withStatus(String status) {
            return new ImportJobDocument(
                    id, importType, targetRegistry, status, createdBy,
                    createdAt, Instant.now(), rowCount, previewLimit,
                    previewSummary, previewRows, rowResults, notes, payloadRef, payloadSource);
        }

        public ImportJobDocument withRowResults(List<Map<String, Object>> rowResults, String status) {
            return new ImportJobDocument(
                    id, importType, targetRegistry, status, createdBy,
                    createdAt, Instant.now(), rowCount, previewLimit,
                    previewSummary, previewRows, rowResults, notes, payloadRef, payloadSource);
        }
    }

    public record RecoveryRequestDocument(
            String id,
            String requestKind,
            String targetRegistry,
            String status,
            String requestedBy,
            String contactHint,
            String narrative,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static RecoveryRequestDocument create(
                String id,
                String requestKind,
                String targetRegistry,
                String requestedBy,
                String contactHint,
                String narrative) {
            Instant now = Instant.now();
            return new RecoveryRequestDocument(
                    id,
                    requestKind,
                    targetRegistry,
                    "REQUESTED",
                    requestedBy,
                    contactHint,
                    narrative,
                    now,
                    now);
        }
    }

    public static Map<String, Object> rowResult(
            int rowNumber,
            String status,
            String validationOutcome,
            String errorSummary,
            String createdEntityRef,
            String duplicateCandidateRef) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("rowNumber", rowNumber);
        m.put("status", status);
        m.put("validationOutcome", validationOutcome);
        m.put("errorSummary", errorSummary);
        m.put("createdEntityRef", createdEntityRef);
        m.put("duplicateCandidateRef", duplicateCandidateRef);
        return m;
    }
}
