package zw.gov.mohcc.impilo.governance.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.governance.persistence.ImportBatchEntity;
import zw.gov.mohcc.impilo.governance.persistence.ImportBatchRepository;
import zw.gov.mohcc.impilo.governance.persistence.ImportExceptionEntity;
import zw.gov.mohcc.impilo.governance.persistence.ImportExceptionRepository;
import zw.gov.mohcc.impilo.governance.persistence.ImportRowEntity;
import zw.gov.mohcc.impilo.governance.persistence.ImportRowRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Workforce-intake pipeline on the wgv import batch model (V011):
 * explicit stage lifecycle with rejected out-of-order transitions, column
 * mapping + re-validation that records exceptions instead of dropping rows,
 * match write-back on the row PATCH path, and per-row execution results.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WorkforceIntakePipelineTest {

    @Mock private ImportBatchRepository batchRepository;
    @Mock private ImportRowRepository rowRepository;
    @Mock private ImportExceptionRepository exceptionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ImportBatchService service;
    private ImportBatchEntity batch;
    private UUID batchId;

    @BeforeEach
    void setUp() {
        service = new ImportBatchService(batchRepository, rowRepository, exceptionRepository, objectMapper);
        when(batchRepository.save(any(ImportBatchEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rowRepository.save(any(ImportRowEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        batch = new ImportBatchEntity();
        batch.init(UUID.randomUUID(), UUID.randomUUID(), "workforce_intake",
                "intake-operator@mohcc.gov.zw", "staff.csv", "hash", 2);
        batchId = batch.getId();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));
    }

    // ── stage lifecycle ────────────────────────────────────────────────────

    @Test
    void newBatchStartsAtUploadedStage() {
        assertThat(batch.getStage()).isEqualTo("UPLOADED");
    }

    @Test
    void fullLegalStageChainIsAccepted() {
        for (String next : List.of("MAPPED", "VALIDATED", "MATCHED", "APPROVED", "EXECUTING", "COMPLETED")) {
            ImportBatchEntity result = service.transitionStage(batchId, next);
            assertThat(result.getStage()).isEqualTo(next);
        }
    }

    @Test
    void partialExecutionCanResumeToExecuting() {
        batch.setStage("EXECUTING");
        assertThat(service.transitionStage(batchId, "PARTIAL").getStage()).isEqualTo("PARTIAL");
        assertThat(service.transitionStage(batchId, "EXECUTING").getStage()).isEqualTo("EXECUTING");
    }

    @Test
    void outOfOrderStageTransitionIsRejected() {
        assertThatThrownBy(() -> service.transitionStage(batchId, "APPROVED"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UPLOADED -> APPROVED");
    }

    @Test
    void completedIsTerminal() {
        batch.setStage("COMPLETED");
        assertThatThrownBy(() -> service.transitionStage(batchId, "EXECUTING"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownStageIsRejected() {
        assertThatThrownBy(() -> service.transitionStage(batchId, "SHIPPED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown stage");
    }

    @Test
    void approveAlsoAdvancesStageWhenMatched() {
        batch.setStage("MATCHED");
        ImportBatchEntity approved = service.approve(batchId, "admin-1");
        assertThat(approved.getStage()).isEqualTo("APPROVED");
        assertThat(approved.getStatus()).isEqualTo("approved");
    }

    // ── column mapping + validation ────────────────────────────────────────

    private ImportRowEntity row(int number, String rawJson) {
        ImportRowEntity row = new ImportRowEntity();
        row.init(batchId, number, rawJson);
        return row;
    }

    @Test
    void columnMappingRemapsHeadersAndValidatesRows() throws Exception {
        ImportRowEntity valid = row(1,
                "{\"id_number\":\"63-123456A63\",\"first_name\":\"Rudo\",\"surname\":\"Moyo\",\"profession\":\"NURSE\",\"email\":\"rudo.moyo@mohcc.gov.zw\"}");
        ImportRowEntity missingProfession = row(2,
                "{\"id_number\":\"63-654321B63\",\"first_name\":\"Tawanda\",\"surname\":\"Ncube\",\"profession\":\"\"}");
        when(rowRepository.findByImportBatchIdOrderByRowNumberAsc(batchId))
                .thenReturn(List.of(valid, missingProfession));

        ImportBatchEntity result = service.applyColumnMapping(batchId, Map.of(
                "id_number", "nationalid",
                "first_name", "givenname",
                "surname", "familyname"));

        assertThat(result.getStage()).isEqualTo("VALIDATED");
        assertThat(result.getColumnMapping()).containsEntry("id_number", "nationalid");
        assertThat(result.getValidRowCount()).isEqualTo(1);
        assertThat(result.getExceptionCount()).isEqualTo(1);

        Map<String, Object> normalized = objectMapper.readValue(valid.getNormalizedPayloadJson(), Map.class);
        assertThat(normalized)
                .containsEntry("nationalid", "63-123456A63")
                .containsEntry("givenname", "Rudo")
                .containsEntry("familyname", "Moyo")
                .containsEntry("email", "rudo.moyo@mohcc.gov.zw");
        assertThat(valid.getOutcome()).isEqualTo("ready_to_invite");
        assertThat(missingProfession.getOutcome()).isEqualTo("invalid_required_fields");

        ArgumentCaptor<ImportExceptionEntity> captor = ArgumentCaptor.forClass(ImportExceptionEntity.class);
        verify(exceptionRepository).save(captor.capture());
        assertThat(captor.getValue().getMessage()).contains("profession");
    }

    @Test
    void columnMappingIsRejectedAfterMatching() {
        batch.setStage("MATCHED");
        assertThatThrownBy(() -> service.applyColumnMapping(batchId, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before matching");
    }

    // ── match write-back on row PATCH ──────────────────────────────────────

    @Test
    void rowPatchAcceptsMatchFields() {
        ImportRowEntity row = row(1, "{}");
        UUID duplicateOf = UUID.randomUUID();
        when(rowRepository.findById(row.getId())).thenReturn(Optional.of(row));

        ImportRowEntity updated = service.updateRowInvitation(batchId, row.getId(), Map.of(
                "matchStatus", "DUPLICATE_IN_BATCH",
                "matchedHealthId", "0f9f87a2-4f7f-4a24-9e5f-30b1c9dfc001",
                "duplicateOfRowId", duplicateOf.toString()));

        assertThat(updated.getMatchStatus()).isEqualTo("DUPLICATE_IN_BATCH");
        assertThat(updated.getMatchedHealthId()).isEqualTo("0f9f87a2-4f7f-4a24-9e5f-30b1c9dfc001");
        assertThat(updated.getDuplicateOfRowId()).isEqualTo(duplicateOf);
    }

    // ── execution results ──────────────────────────────────────────────────

    @Test
    void executionResultPersistsProviderAndVashandiRefs() {
        ImportRowEntity row = row(1, "{}");
        UUID profileId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        when(rowRepository.findById(row.getId())).thenReturn(Optional.of(row));

        ImportRowEntity updated = service.recordExecutionResult(batchId, row.getId(), Map.of(
                "executionStatus", "DONE",
                "providerPublicId", "PRV-2026-000123",
                "vashandiProfileId", profileId.toString(),
                "assignmentId", assignmentId.toString()));

        assertThat(updated.getExecutionStatus()).isEqualTo("DONE");
        assertThat(updated.getProviderPublicId()).isEqualTo("PRV-2026-000123");
        assertThat(updated.getVashandiProfileId()).isEqualTo(profileId);
        assertThat(updated.getAssignmentId()).isEqualTo(assignmentId);
        assertThat(updated.getExecutionError()).isNull();
    }

    @Test
    void executionResultTruncatesOverlongErrorTo500Chars() {
        ImportRowEntity row = row(1, "{}");
        when(rowRepository.findById(row.getId())).thenReturn(Optional.of(row));

        ImportRowEntity updated = service.recordExecutionResult(batchId, row.getId(), Map.of(
                "executionStatus", "FAILED_VASHANDI",
                "executionError", "x".repeat(700)));

        assertThat(updated.getExecutionStatus()).isEqualTo("FAILED_VASHANDI");
        assertThat(updated.getExecutionError()).hasSize(500);
    }

    @Test
    void executionResultRejectsRowFromAnotherBatch() {
        ImportRowEntity foreign = new ImportRowEntity();
        foreign.init(UUID.randomUUID(), 1, "{}");
        when(rowRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.recordExecutionResult(batchId, foreign.getId(), Map.of("executionStatus", "DONE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
    }

    // ── template ───────────────────────────────────────────────────────────

    @Test
    void workforceIntakeTemplateExposesCanonicalRequiredColumns() {
        Map<String, Object> template = service.template("workforce_intake");
        assertThat((List<String>) template.get("requiredColumns"))
                .containsExactly("nationalid", "givenname", "familyname", "profession");
    }
}
