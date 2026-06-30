package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.integration.InventoryConsumptionClient;
import zw.gov.mohcc.impilo.inpatient.integration.OrosOrderClient;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.MarEntryEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Exercises the eMAR 5-rights gate, the administration event, and the inventory stock-consumption hook. */
@ExtendWith(MockitoExtension.class)
class InpatientMedicationAdministrationTest {

    @Mock CarePlanRepository carePlanRepository;
    @Mock CarePlanGoalRepository goalRepository;
    @Mock CarePlanInterventionRepository interventionRepository;
    @Mock FluidBalanceRepository fluidBalanceRepository;
    @Mock ClinicalChartEntryRepository chartEntryRepository;
    @Mock MarEntryRepository marEntryRepository;
    @Mock EarlyWarningScoreRepository ewsRepository;
    @Mock EmergencyActivationRepository emergencyRepository;
    @Mock EmergencyActionRepository emergencyActionRepository;
    @Mock ResuscitationRecordRepository resuscitationRecordRepository;
    @Mock ApgarScoreRepository apgarScoreRepository;
    @Mock ShiftHandoverRepository handoverRepository;
    @Mock ShiftHandoverItemRepository handoverItemRepository;
    @Mock WardPatientAlertRepository wardAlertRepository;
    @Mock DischargeClearanceRepository dischargeClearanceRepository;
    @Mock ResuscitationPhaseRepository resuscitationPhaseRepository;
    @Mock AdmissionRepository admissionRepository;
    @Mock TransferRepository transferRepository;
    @Mock InpatientSafetyService safetyService;
    @Mock InventoryConsumptionClient inventoryConsumptionClient;
    @Mock OrosOrderClient orosOrderClient;
    @Mock EventOutboxRepository outboxRepository;

    private InpatientClinicalService service;
    private final UUID tenant = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new InpatientClinicalService(carePlanRepository, goalRepository, interventionRepository,
                fluidBalanceRepository, chartEntryRepository, marEntryRepository, ewsRepository,
                emergencyRepository, emergencyActionRepository, resuscitationRecordRepository,
                apgarScoreRepository, handoverRepository, handoverItemRepository, wardAlertRepository,
                dischargeClearanceRepository, resuscitationPhaseRepository, admissionRepository,
                transferRepository, new ObjectMapper(), safetyService, inventoryConsumptionClient,
                orosOrderClient, outboxRepository);
        TrustContextHolder.set(new TrustContext(tenant, "actor-nurse", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void activeAdmission() {
        AdmissionEntity adm = new AdmissionEntity();
        adm.setTenantId(tenant);
        adm.setSubjectCpid("CPID-1");
        when(admissionRepository.findBySubjectCpidAndStatus("CPID-1", "ADMITTED"))
                .thenReturn(List.of(adm));
    }

    @Test
    void administer_withoutFiveRightsConfirmation_isRejected() {
        activeAdmission();
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.administerMedication(Map.of(
                        "patientId", "CPID-1", "drugName", "Amoxicillin")));
        assertTrue(ex.getMessage().contains("fiveRightsConfirmed"));
        verify(marEntryRepository, never()).save(any());
    }

    @Test
    void administer_confirmed_persistsEmitsEventAndCallsInventory() {
        activeAdmission();
        when(marEntryRepository.save(any())).thenAnswer(inv -> {
            MarEntryEntity m = inv.getArgument(0);
            if (m.getMarId() == null) m.setMarId(UUID.randomUUID());
            return m;
        });
        when(inventoryConsumptionClient.recordEmarConsumption(anyString(), anyString(), anyInt(), any()))
                .thenReturn(true);

        Map<String, Object> result = service.administerMedication(Map.of(
                "patientId", "CPID-1",
                "drugName", "Amoxicillin",
                "dose", "500mg",
                "route", "PO",
                "fiveRightsConfirmed", true,
                "storeId", UUID.randomUUID().toString(),
                "itemCode", "AMOX-500",
                "quantity", 1,
                "administeredBy", "actor-nurse"));

        assertEquals("ADMINISTERED", result.get("status"));
        assertEquals(true, result.get("stockConsumptionRecorded"));

        // administration event emitted on the outbox
        org.mockito.ArgumentCaptor<EventOutboxEntity> evt =
                org.mockito.ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(evt.capture());
        assertEquals("inpatient.medication.administered", evt.getValue().getEventType());

        // inventory consumption hook called with the eMAR refType qty
        verify(inventoryConsumptionClient).recordEmarConsumption(anyString(), eq("AMOX-500"), eq(1), any());
    }

    @Test
    void administer_drugMismatch_isRejected() {
        activeAdmission();
        MarEntryEntity scheduled = new MarEntryEntity();
        scheduled.setMarId(UUID.randomUUID());
        scheduled.setTenantId(tenant);
        scheduled.setSubjectCpid("CPID-1");
        scheduled.setPrescriptionId("RX-1");
        scheduled.setDrugName("Amoxicillin");
        when(marEntryRepository.findByTenantIdAndSubjectCpidOrderByRecordedAtDesc(tenant, "CPID-1"))
                .thenReturn(List.of(scheduled));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                service.administerMedication(Map.of(
                        "patientId", "CPID-1",
                        "prescriptionId", "RX-1",
                        "drugName", "Paracetamol",
                        "fiveRightsConfirmed", true)));
        assertTrue(ex.getMessage().contains("drug mismatch"));
    }
}
