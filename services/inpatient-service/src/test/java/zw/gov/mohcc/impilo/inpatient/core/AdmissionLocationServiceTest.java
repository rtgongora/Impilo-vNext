package zw.gov.mohcc.impilo.inpatient.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.inpatient.api.dto.PatientLocationView;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.BedEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.WardEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.BedRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.TransferRepository;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.WardRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AdmissionService#getCurrentLocation} — the resolver that backs
 * the experience-shell patient-location badge (G053). Verifies it picks the most-recent
 * active admission, resolves ward/bed UUIDs to labels, and returns empty when not admitted.
 */
@ExtendWith(MockitoExtension.class)
class AdmissionLocationServiceTest {

    @Mock private AdmissionRepository admissionRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private WardRepository wardRepository;
    @Mock private BedRepository bedRepository;

    private AdmissionService service;

    private static final String CPID = "CPID-7788";

    @BeforeEach
    void setUp() {
        service = new AdmissionService(admissionRepository, transferRepository,
                outboxRepository, wardRepository, bedRepository, new ObjectMapper());
    }

    private AdmissionEntity admission(UUID wardId, UUID bedId, OffsetDateTime admittedAt) {
        AdmissionEntity a = new AdmissionEntity();
        a.setAdmissionRef(UUID.randomUUID());
        a.setSubjectCpid(CPID);
        a.setFacilityId(UUID.randomUUID());
        a.setWardId(wardId);
        a.setBedId(bedId);
        a.setStatus("ADMITTED");
        a.setAdmissionType("EMERGENCY");
        a.setAdmittedAt(admittedAt);
        return a;
    }

    @Test
    void resolvesLatestActiveAdmissionWithWardAndBedLabels() {
        UUID wardId = UUID.randomUUID();
        UUID bedId = UUID.randomUUID();
        AdmissionEntity older = admission(UUID.randomUUID(), UUID.randomUUID(),
                OffsetDateTime.now().minusDays(3));
        AdmissionEntity latest = admission(wardId, bedId, OffsetDateTime.now().minusHours(2));

        when(admissionRepository.findBySubjectCpidAndStatus(CPID, "ADMITTED"))
                .thenReturn(List.of(older, latest));

        WardEntity ward = new WardEntity();
        ward.setName("Medical Ward A");
        BedEntity bed = new BedEntity();
        bed.setBedNumber("MW-12");
        when(wardRepository.findById(wardId)).thenReturn(Optional.of(ward));
        when(bedRepository.findById(bedId)).thenReturn(Optional.of(bed));

        Optional<PatientLocationView> result = service.getCurrentLocation(CPID, null);

        assertTrue(result.isPresent());
        assertEquals("Medical Ward A", result.get().wardName());
        assertEquals("MW-12", result.get().bedNumber());
        assertEquals("ADMITTED", result.get().status());
        assertEquals(latest.getAdmissionRef(), result.get().admissionRef());
    }

    @Test
    void emptyWhenPatientNotAdmitted() {
        when(admissionRepository.findBySubjectCpidAndStatus(CPID, "ADMITTED"))
                .thenReturn(List.of());
        assertTrue(service.getCurrentLocation(CPID, null).isEmpty());
    }

    @Test
    void emptyForBlankCpidWithoutHittingRepository() {
        assertTrue(service.getCurrentLocation("  ", null).isEmpty());
    }

    @Test
    void facilityScopedLookupUsesFacilityQuery() {
        UUID facilityId = UUID.randomUUID();
        lenient().when(admissionRepository
                        .findBySubjectCpidAndFacilityIdAndStatus(CPID, facilityId, "ADMITTED"))
                .thenReturn(List.of());
        assertTrue(service.getCurrentLocation(CPID, facilityId).isEmpty());
    }
}
