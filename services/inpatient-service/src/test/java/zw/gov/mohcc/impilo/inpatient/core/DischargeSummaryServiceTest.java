package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeClearanceEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.DischargeSummaryEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DischargeClearanceRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.DischargeSummaryRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DischargeSummaryServiceTest {

    @Mock DischargeSummaryRepository summaryRepository;
    @Mock DischargeClearanceRepository clearanceRepository;
    @Mock EventOutboxRepository outboxRepository;

    private DischargeSummaryService service;
    private final UUID tenant = UUID.randomUUID();
    private final UUID encounter = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DischargeSummaryService(summaryRepository, clearanceRepository,
                outboxRepository, new ObjectMapper());
        TrustContextHolder.set(new TrustContext(tenant, "actor-doc", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() { TrustContextHolder.clear(); }

    private DischargeSummaryEntity draft() {
        DischargeSummaryEntity s = new DischargeSummaryEntity();
        s.setSummaryId(UUID.randomUUID());
        s.setTenantId(tenant);
        s.setEncounterId(encounter);
        s.setSubjectCpid("CPID-1");
        s.setStatus("DRAFT");
        s.setHospitalCourse("Admitted with pneumonia, IV antibiotics, improved.");
        return s;
    }

    private DischargeClearanceEntity clearance(String type, String status) {
        DischargeClearanceEntity c = new DischargeClearanceEntity();
        c.setClearanceType(type);
        c.setStatus(status);
        return c;
    }

    @Test
    void finalise_blockedWhenClearancePending() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(List.of(clearance("CLINICAL", "CLEARED"), clearance("PHARMACY", "PENDING")));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.finalise(encounter));
        assertTrue(ex.getMessage().contains("PHARMACY"));
        verify(summaryRepository, never()).save(any());
    }

    @Test
    void finalise_blockedWhenNoClearancesInitialised() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(List.of());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.finalise(encounter));
        assertTrue(ex.getMessage().contains("not initialised"));
    }

    @Test
    void finalise_succeeds_emitsFhirCompositionAndFollowup_whenAllCleared() {
        when(summaryRepository.findByTenantIdAndEncounterId(tenant, encounter))
                .thenReturn(Optional.of(draft()));
        when(clearanceRepository.findByTenantIdAndEncounterIdOrderByClearanceTypeAsc(tenant, encounter))
                .thenReturn(List.of(clearance("CLINICAL", "CLEARED"), clearance("PHARMACY", "WAIVED")));
        when(summaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> result = service.finalise(encounter);
        assertEquals("FINALISED", result.get("status"));
        assertTrue(String.valueOf(result.get("fhir_composition")).contains("\"resourceType\":\"Composition\""));
        assertTrue(String.valueOf(result.get("fhir_composition")).contains("18842-5")); // LOINC discharge summary

        ArgumentCaptor<EventOutboxEntity> evt = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository, atLeastOnce()).save(evt.capture());
        List<String> types = evt.getAllValues().stream().map(EventOutboxEntity::getEventType).toList();
        assertTrue(types.contains("inpatient.discharge.summary_finalised"));
        assertTrue(types.contains("inpatient.discharge.followup_requested"));
    }
}
