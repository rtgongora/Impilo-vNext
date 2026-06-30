package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BedManagementServiceTest {

    @Mock
    private WardRepository wardRepository;
    @Mock
    private BedRepository bedRepository;
    @Mock
    private EventOutboxRepository outboxRepository;

    private BedManagementService service;

    @BeforeEach
    void setUp() {
        // Real evaluator (pure logic) + mocked outbox so safety + event-emit are exercised end to end.
        service = new BedManagementService(wardRepository, bedRepository,
                new BedSafetyEvaluator(), outboxRepository, new ObjectMapper());
    }

    private BedEntity bed(UUID bedId, UUID wardId, UUID facilityId) {
        BedEntity bed = new BedEntity();
        bed.setId(bedId);
        bed.setTenantId(UUID.randomUUID());
        bed.setWardId(wardId);
        bed.setFacilityId(facilityId);
        bed.setBedNumber("MWA-01");
        bed.setStatus("AVAILABLE");
        return bed;
    }

    @Test
    void assignPatient_marksBedOccupiedWithPatientFields_andEmitsEvent() {
        UUID bedId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();

        BedEntity bed = bed(bedId, wardId, facilityId);
        WardEntity ward = new WardEntity();
        ward.setId(wardId);
        ward.setName("Medical Ward A");

        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));
        when(bedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));

        Map<String, Object> result = service.assignPatient(bedId, Map.of(
                "patientId", "CPID-99",
                "patientName", "Jane Doe",
                "acuity", "high"));

        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) result.get("attributes");
        assertEquals("occupied", attrs.get("status"));
        assertEquals("CPID-99", attrs.get("patientMrn"));
        assertEquals("Jane Doe", attrs.get("patientName"));
        verify(bedRepository).save(any());
        verify(outboxRepository).save(any(EventOutboxEntity.class));
    }

    @Test
    void assignPatient_rejectsOccupiedBed() {
        UUID bedId = UUID.randomUUID();
        BedEntity bed = new BedEntity();
        bed.setId(bedId);
        bed.setStatus("OCCUPIED");
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));

        assertThrows(IllegalStateException.class, () ->
                service.assignPatient(bedId, Map.of("patientId", "CPID-1")));
    }

    @Test
    void assignPatient_rejectsGenderMismatch() {
        UUID bedId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();

        BedEntity bed = bed(bedId, wardId, facilityId);
        WardEntity ward = new WardEntity();
        ward.setId(wardId);
        ward.setGenderDesignation("FEMALE");

        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));

        BedSafetyViolationException ex = assertThrows(BedSafetyViolationException.class, () ->
                service.assignPatient(bedId, Map.of("patientId", "CPID-1", "gender", "MALE")));
        assertTrue(ex.getViolations().stream().anyMatch(v -> v.startsWith("GENDER_MISMATCH")));
        verify(bedRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void assignPatient_rejectsIsolationRequiredOnIncapableBed() {
        UUID bedId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();

        BedEntity bed = bed(bedId, wardId, facilityId); // not isolation-capable
        WardEntity ward = new WardEntity();
        ward.setId(wardId);
        ward.setIsolationCapable(false);

        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));

        BedSafetyViolationException ex = assertThrows(BedSafetyViolationException.class, () ->
                service.assignPatient(bedId, Map.of(
                        "patientId", "CPID-1", "requiresIsolation", true)));
        assertTrue(ex.getViolations().stream().anyMatch(v -> v.startsWith("ISOLATION_REQUIRED")));
    }

    @Test
    void assignPatient_allowsWhenWardSatisfiesCapability() {
        UUID bedId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();
        UUID facilityId = UUID.randomUUID();

        BedEntity bed = bed(bedId, wardId, facilityId);
        WardEntity ward = new WardEntity();
        ward.setId(wardId);
        ward.setIcuCapable(true);
        ward.setMonitoringCapable(true);

        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));
        when(bedRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));

        Map<String, Object> result = service.assignPatient(bedId, Map.of(
                "patientId", "CPID-7", "requiresIcu", true, "requiresMonitoring", true));
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) result.get("attributes");
        assertEquals("occupied", attrs.get("status"));
    }

    @Test
    void listWardResources_returnsCensusCounts() {
        UUID facilityId = UUID.randomUUID();
        UUID wardId = UUID.randomUUID();

        WardEntity ward = new WardEntity();
        ward.setId(wardId);
        ward.setFacilityId(facilityId);
        ward.setName("ICU");
        ward.setTotalBeds(2);

        BedEntity available = new BedEntity();
        available.setWardId(wardId);
        available.setFacilityId(facilityId);
        available.setStatus("AVAILABLE");
        available.setBedNumber("ICU-01");

        BedEntity occupied = new BedEntity();
        occupied.setWardId(wardId);
        occupied.setFacilityId(facilityId);
        occupied.setStatus("OCCUPIED");
        occupied.setBedNumber("ICU-02");

        when(wardRepository.findByFacilityIdOrderByNameAsc(facilityId)).thenReturn(List.of(ward));
        when(bedRepository.findByFacilityId(facilityId)).thenReturn(List.of(available, occupied));

        List<Map<String, Object>> wards = service.listWardResources(facilityId);
        assertEquals(1, wards.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> attrs = (Map<String, Object>) wards.get(0).get("attributes");
        assertEquals(1L, attrs.get("occupiedBeds"));
        assertEquals(1L, attrs.get("availableBeds"));
    }
}
