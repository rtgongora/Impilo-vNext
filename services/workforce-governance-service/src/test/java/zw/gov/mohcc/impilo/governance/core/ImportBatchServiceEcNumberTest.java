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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * EC-number column mapping on HSC employment imports (IATG WS-D):
 * valid EC is canonicalised into the normalized payload, malformed EC becomes an
 * ImportException row (never a silent drop), and absent EC stays optional.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImportBatchServiceEcNumberTest {

    @Mock private ImportBatchRepository batchRepository;
    @Mock private ImportRowRepository rowRepository;
    @Mock private ImportExceptionRepository exceptionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ImportBatchService service;

    @BeforeEach
    void setUp() {
        service = new ImportBatchService(batchRepository, rowRepository, exceptionRepository, objectMapper);
        when(batchRepository.save(any(ImportBatchEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(rowRepository.save(any(ImportRowEntity.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ImportBatchEntity upload(String csv) {
        return service.upload(UUID.randomUUID(), UUID.randomUUID(), "importer@mohcc.gov.zw",
                "hsc_employment_records", "hsc.csv", csv);
    }

    @Test
    void validEcNumberIsCanonicalisedIntoNormalizedPayload() {
        String csv = """
                provider_worker_id,employment_status,post_title,ec_number
                PW-1,ACTIVE,Nurse,ec 123456
                """;
        upload(csv);

        ArgumentCaptor<ImportRowEntity> rowCaptor = ArgumentCaptor.forClass(ImportRowEntity.class);
        verify(rowRepository).save(rowCaptor.capture());
        ImportRowEntity row = rowCaptor.getValue();
        assertThat(row.getOutcome()).isEqualTo("ready_to_invite");
        assertThat(row.getNormalizedPayloadJson()).contains("\"ec_number\":\"EC123456\"");
        verify(exceptionRepository, never()).save(any(ImportExceptionEntity.class));
    }

    @Test
    void malformedEcNumberBecomesImportExceptionRowNotSilentDrop() {
        String csv = """
                provider_worker_id,employment_status,post_title,ec_number
                PW-2,ACTIVE,Nurse,NODIGITSHERE
                """;
        ImportBatchEntity batch = upload(csv);

        ArgumentCaptor<ImportRowEntity> rowCaptor = ArgumentCaptor.forClass(ImportRowEntity.class);
        verify(rowRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getOutcome()).isEqualTo("invalid_ec_number");

        ArgumentCaptor<ImportExceptionEntity> exCaptor = ArgumentCaptor.forClass(ImportExceptionEntity.class);
        verify(exceptionRepository).save(exCaptor.capture());
        assertThat(exCaptor.getValue()).isNotNull();
        assertThat(batch.getStatus()).isEqualTo("validated");
    }

    @Test
    void blankEcNumberStaysOptional() {
        String csv = """
                provider_worker_id,employment_status,post_title,ec_number
                PW-3,ACTIVE,Nurse,
                """;
        upload(csv);

        ArgumentCaptor<ImportRowEntity> rowCaptor = ArgumentCaptor.forClass(ImportRowEntity.class);
        verify(rowRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getOutcome()).isEqualTo("ready_to_invite");
        verify(exceptionRepository, never()).save(any(ImportExceptionEntity.class));
    }

    @Test
    void otherImportTypesIgnoreEcColumn() {
        String csv = """
                email,full_name,role_template,ec_number
                a@b.zw,Alice,admin,NODIGITSHERE
                """;
        service.upload(UUID.randomUUID(), UUID.randomUUID(), "importer@mohcc.gov.zw",
                "organisation_users", "users.csv", csv);

        ArgumentCaptor<ImportRowEntity> rowCaptor = ArgumentCaptor.forClass(ImportRowEntity.class);
        verify(rowRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getOutcome()).isEqualTo("ready_to_invite");
        verify(exceptionRepository, never()).save(any(ImportExceptionEntity.class));
    }

    @Test
    void ecNumberCanonicalisationAndMasking() {
        assertThat(EcNumbers.canonicalise("  ec 123456 ")).isEqualTo("EC123456");
        assertThat(EcNumbers.isValid("EC123456")).isTrue();
        assertThat(EcNumbers.isValid("NODIGITSHERE")).isFalse();
        assertThat(EcNumbers.isValid("E")).isFalse();
        assertThat(EcNumbers.isValid("EC 12")).isFalse(); // whitespace only removed by canonicalise
        assertThat(EcNumbers.mask("EC123456")).isEqualTo("EC***");
        assertThat(EcNumbers.mask("E1")).isEqualTo("E1***");
        assertThat(EcNumbers.mask(null)).isNull();
    }

    @Test
    void ecNumberWithSlashAndDashIsAccepted() {
        String csv = """
                provider_worker_id,employment_status,post_title,ec_number
                PW-9,ACTIVE,Nurse,EC-777/2024
                """;
        ImportBatchEntity batch = upload(csv);
        assertThat(List.of(batch.getStatus())).containsExactly("validated");
        ArgumentCaptor<ImportRowEntity> rowCaptor = ArgumentCaptor.forClass(ImportRowEntity.class);
        verify(rowRepository).save(rowCaptor.capture());
        assertThat(rowCaptor.getValue().getOutcome()).isEqualTo("ready_to_invite");
    }
}
