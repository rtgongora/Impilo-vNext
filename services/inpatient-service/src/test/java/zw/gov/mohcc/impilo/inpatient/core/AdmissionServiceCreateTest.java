package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.api.dto.CreateAdmissionRequest;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.TransferRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRepository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies that admission creation and bed occupation are atomic: a chosen bed is occupied
 * (running the safety gate) in the same transaction, and a safety violation aborts the whole
 * admission so no orphan admission is persisted (QA/G1 remediation).
 */
@ExtendWith(MockitoExtension.class)
class AdmissionServiceCreateTest {

    @Mock private AdmissionRepository admissionRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private WardRepository wardRepository;
    @Mock private BedRepository bedRepository;
    @Mock private BedManagementService bedManagementService;

    private AdmissionService service;

    @BeforeEach
    void setUp() {
        service = new AdmissionService(admissionRepository, transferRepository,
                outboxRepository, wardRepository, bedRepository, bedManagementService, new ObjectMapper());
    }

    private CreateAdmissionRequest request(UUID bedId) {
        CreateAdmissionRequest r = new CreateAdmissionRequest();
        r.setTenantId(UUID.randomUUID());
        r.setEncounterId(UUID.randomUUID());
        r.setSubjectCpid("CPID-ADMIT-1");
        r.setFacilityId(UUID.randomUUID());
        r.setWardId(UUID.randomUUID());
        r.setBedId(bedId);
        r.setAdmittingDiagnosis("CCF NYHA III");
        return r;
    }

    @Test
    void createAdmission_occupiesChosenBedInSameTransaction() {
        UUID bedId = UUID.randomUUID();
        when(admissionRepository.save(any(AdmissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        AdmissionEntity created = service.createAdmission(request(bedId));

        assertEquals("ADMITTED", created.getStatus());
        verify(bedManagementService).assignPatient(eq(bedId), any(Map.class));
        verify(admissionRepository).save(any(AdmissionEntity.class));
    }

    @Test
    void createAdmission_abortsWhenBedIsUnsafe_noAdmissionPersisted() {
        UUID bedId = UUID.randomUUID();
        when(bedManagementService.assignPatient(eq(bedId), any(Map.class)))
                .thenThrow(new BedSafetyViolationException(List.of("GENDER_BAY_MISMATCH")));

        assertThrows(BedSafetyViolationException.class, () -> service.createAdmission(request(bedId)));

        // The whole transaction must abort — no admission row and no outbox event.
        verify(admissionRepository, never()).save(any(AdmissionEntity.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void createAdmission_withoutBed_skipsBedAssignment() {
        when(admissionRepository.save(any(AdmissionEntity.class))).thenAnswer(i -> i.getArgument(0));

        service.createAdmission(request(null));

        verify(bedManagementService, never()).assignPatient(any(), any());
        verify(admissionRepository).save(any(AdmissionEntity.class));
    }
}
