package zw.gov.mohcc.impilo.inpatient.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.AdmissionEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.AdmissionRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NeonatalAdmissionHandlerTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final UUID FACILITY = UUID.randomUUID();
    private static final UUID MOTHER_EPISODE = UUID.randomUUID();
    private static final String BABY = "CPID-BABY-1";

    @Mock private AdmissionRepository admissionRepository;

    private NeonatalAdmissionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new NeonatalAdmissionHandler(admissionRepository);
        when(admissionRepository.save(any(AdmissionEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(admissionRepository.findBySubjectCpidAndStatus(anyString(), anyString())).thenReturn(List.of());
    }

    @Test
    void aBabyGoingToTheNeonatalUnitIsAdmittedUnderTheirOwnIdentity() {
        // Without this the baby's first APGAR or observation is refused, because the only
        // care context in the building belongs to the mother.
        Optional<AdmissionEntity> admission =
                handler.admitOnHandover(TENANT, BABY, FACILITY, "NICU", MOTHER_EPISODE);

        assertThat(admission).isPresent();
        assertThat(admission.get().getSubjectCpid()).isEqualTo(BABY);
        assertThat(admission.get().getStatus()).isEqualTo("ADMITTED");
        assertThat(admission.get().getTenantId()).isEqualTo(TENANT);
        assertThat(admission.get().getFacilityId()).isEqualTo(FACILITY);
        assertThat(admission.get().getEncounterId()).isNotNull();
    }

    @Test
    void everyInpatientNeonatalDestinationAdmits() {
        assertThat(handler.admitOnHandover(TENANT, BABY, FACILITY, "NEONATAL", MOTHER_EPISODE)).isPresent();
        assertThat(handler.admitOnHandover(TENANT, BABY, FACILITY, "SCBU", MOTHER_EPISODE)).isPresent();
        assertThat(handler.admitOnHandover(TENANT, BABY, FACILITY, "nicu", MOTHER_EPISODE)).isPresent();
    }

    @Test
    void aBabyRoomingInWithTheirMotherIsNotSeparatelyAdmitted() {
        // Admitting a well newborn would create a bed occupancy and a bed-day that does not
        // exist; whether rooming-in should ever be its own admission is a service decision.
        assertThat(handler.admitOnHandover(TENANT, BABY, FACILITY, "POSTNATAL", MOTHER_EPISODE)).isEmpty();
        verify(admissionRepository, never()).save(any());
    }

    @Test
    void aBabyGoingToTheMortuaryIsNotAdmitted() {
        assertThat(handler.admitOnHandover(TENANT, BABY, FACILITY, "MORTUARY", MOTHER_EPISODE)).isEmpty();
        verify(admissionRepository, never()).save(any());
    }

    @Test
    void aRepeatedHandoverReturnsTheExistingAdmissionRatherThanCreatingASecond() {
        AdmissionEntity existing = new AdmissionEntity();
        existing.setAdmissionRef(UUID.randomUUID());
        existing.setTenantId(TENANT);
        existing.setSubjectCpid(BABY);
        existing.setStatus("ADMITTED");
        when(admissionRepository.findBySubjectCpidAndStatus(BABY, "ADMITTED")).thenReturn(List.of(existing));

        Optional<AdmissionEntity> admission =
                handler.admitOnHandover(TENANT, BABY, FACILITY, "NICU", MOTHER_EPISODE);

        assertThat(admission).contains(existing);
        verify(admissionRepository, never()).save(any());
    }

    @Test
    void anAdmissionInAnotherTenantIsNotMistakenForThisOne() {
        AdmissionEntity otherTenant = new AdmissionEntity();
        otherTenant.setAdmissionRef(UUID.randomUUID());
        otherTenant.setTenantId(UUID.randomUUID());
        otherTenant.setSubjectCpid(BABY);
        otherTenant.setStatus("ADMITTED");
        when(admissionRepository.findBySubjectCpidAndStatus(BABY, "ADMITTED")).thenReturn(List.of(otherTenant));

        Optional<AdmissionEntity> admission =
                handler.admitOnHandover(TENANT, BABY, FACILITY, "NICU", MOTHER_EPISODE);

        assertThat(admission).isPresent();
        assertThat(admission.get().getAdmissionRef()).isNotEqualTo(otherTenant.getAdmissionRef());
    }

    @Test
    void aHandoverWithoutABabyIdentityDoesNotAdmitAnAnonymousPatient() {
        assertThat(handler.admitOnHandover(TENANT, null, FACILITY, "NICU", MOTHER_EPISODE)).isEmpty();
        assertThat(handler.admitOnHandover(TENANT, "  ", FACILITY, "NICU", MOTHER_EPISODE)).isEmpty();
        assertThat(handler.admitOnHandover(null, BABY, FACILITY, "NICU", MOTHER_EPISODE)).isEmpty();
        verify(admissionRepository, never()).save(any());
    }

    @Test
    void theDerivedEncounterReferenceIsStableForTheSameBaby() {
        AdmissionEntity first = handler.admitOnHandover(TENANT, BABY, FACILITY, "NICU", MOTHER_EPISODE).orElseThrow();
        AdmissionEntity second = handler.admitOnHandover(TENANT, BABY, FACILITY, "SCBU", MOTHER_EPISODE).orElseThrow();

        assertThat(first.getEncounterId()).isEqualTo(second.getEncounterId());
    }

    @Test
    void destinationClassificationIsCaseAndWhitespaceTolerant() {
        assertThat(NeonatalAdmissionHandler.requiresAdmission(" nicu ")).isTrue();
        assertThat(NeonatalAdmissionHandler.requiresAdmission("Postnatal")).isFalse();
        assertThat(NeonatalAdmissionHandler.requiresAdmission(null)).isFalse();
    }
}
