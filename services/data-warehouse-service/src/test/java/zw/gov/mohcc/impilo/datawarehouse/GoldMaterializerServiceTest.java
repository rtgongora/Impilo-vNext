package zw.gov.mohcc.impilo.datawarehouse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.datawarehouse.core.GoldMaterializerService;
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldEncounterEntity;
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldLabEntity;
import zw.gov.mohcc.impilo.datawarehouse.domain.GoldMedicationEntity;
import zw.gov.mohcc.impilo.datawarehouse.repository.*;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoldMaterializerServiceTest {

    @Mock private GoldEncounterRepository encounterRepository;
    @Mock private GoldMedicationRepository medicationRepository;
    @Mock private GoldLabRepository labRepository;
    @Mock private MaterializerWatermarkRepository watermarkRepository;
    @Mock private OutboxEventRepository outboxRepository;

    private GoldMaterializerService service;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GoldMaterializerService(encounterRepository, medicationRepository,
                labRepository, watermarkRepository, outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("Encounter event materializes to gold_encounters table")
    void encounterEventMaterializes() {
        String json = """
                {"data": {"encounter_id": "enc-1", "patient_id": "pat-1", "facility_id": "fac-1",
                 "encounter_type": "OPD", "status": "COMPLETED"}}
                """;

        when(encounterRepository.findByTenantIdAndEncounterId(any(), eq("enc-1")))
                .thenReturn(Optional.empty());
        when(encounterRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = service.materializeEvent(json, tenantId, "evt-1", "impilo.clinical.encounter.created.v1");

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<GoldEncounterEntity> captor = ArgumentCaptor.forClass(GoldEncounterEntity.class);
        verify(encounterRepository).save(captor.capture());
        GoldEncounterEntity saved = captor.getValue();
        assertThat(saved.getEncounterId()).isEqualTo("enc-1");
        assertThat(saved.getPatientId()).isEqualTo("pat-1");
        assertThat(saved.getEncounterType()).isEqualTo("OPD");
    }

    @Test
    @DisplayName("Medication event materializes to gold_medications table")
    void medicationEventMaterializes() {
        String json = """
                {"data": {"medication_id": "med-1", "patient_id": "pat-1", "drug_code": "ART-001",
                 "drug_name": "Tenofovir"}}
                """;

        when(medicationRepository.findByTenantIdAndMedicationId(any(), eq("med-1")))
                .thenReturn(Optional.empty());
        when(medicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = service.materializeEvent(json, tenantId, "evt-2", "impilo.pharmacy.medication.dispensed.v1");

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<GoldMedicationEntity> captor = ArgumentCaptor.forClass(GoldMedicationEntity.class);
        verify(medicationRepository).save(captor.capture());
        assertThat(captor.getValue().getDrugCode()).isEqualTo("ART-001");
    }

    @Test
    @DisplayName("Lab event materializes to gold_labs table")
    void labEventMaterializes() {
        String json = """
                {"data": {"lab_result_id": "lab-1", "patient_id": "pat-1", "test_code": "HIV-VL",
                 "result_value": "50", "result_unit": "copies/mL"}}
                """;

        when(labRepository.findByTenantIdAndLabResultId(any(), eq("lab-1")))
                .thenReturn(Optional.empty());
        when(labRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = service.materializeEvent(json, tenantId, "evt-3", "impilo.lab.observation.reported.v1");

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<GoldLabEntity> captor = ArgumentCaptor.forClass(GoldLabEntity.class);
        verify(labRepository).save(captor.capture());
        assertThat(captor.getValue().getTestCode()).isEqualTo("HIV-VL");
        assertThat(captor.getValue().getResultValue()).isEqualTo("50");
    }

    @Test
    @DisplayName("Unknown event type returns 0 upserts")
    void unknownEventTypeReturnsZero() {
        String json = """
                {"data": {"some_id": "x"}}
                """;

        int count = service.materializeEvent(json, tenantId, "evt-4", "impilo.unknown.event.v1");
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Duplicate encounter is idempotent (upserts)")
    void duplicateEncounterIsIdempotent() {
        String json = """
                {"data": {"encounter_id": "enc-1", "patient_id": "pat-1", "status": "UPDATED"}}
                """;

        GoldEncounterEntity existing = new GoldEncounterEntity();
        existing.setEncounterId("enc-1");
        existing.setPatientId("pat-1");
        existing.setTenantId(tenantId);
        when(encounterRepository.findByTenantIdAndEncounterId(any(), eq("enc-1")))
                .thenReturn(Optional.of(existing));
        when(encounterRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        int count = service.materializeEvent(json, tenantId, "evt-5", "impilo.clinical.encounter.updated.v1");

        assertThat(count).isEqualTo(1);
        verify(encounterRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("Malformed JSON returns 0 upserts")
    void malformedJsonReturnsZero() {
        int count = service.materializeEvent("not-json{{", tenantId, "evt-6", "impilo.lab.observation.v1");
        assertThat(count).isEqualTo(0);
    }
}
