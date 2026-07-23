package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.persistence.entity.AllergyEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.AllergyRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Unit tests for the OF-B3 allergy SoR: BFF contract keys, coded fields, deactivation, audit events. */
@ExtendWith(MockitoExtension.class)
class AllergyServiceTest {

    @Mock private AllergyRepository allergyRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private AllergyService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AllergyService(allergyRepository, outboxRepository, new ObjectMapper(), accessGuard);
    }

    private TrustContext ctx() {
        return new TrustContext(TENANT_ID, "nurse-1", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL);
    }

    @Test
    void addAcceptsBffPatientIdKeyAndRecordsCodedAllergy() {
        when(allergyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            AllergyEntity a = service.add(Map.of(
                    "patient_id", "CPID-9",            // BFF strangler key (not subject_cpid)
                    "allergen", "Penicillin",
                    "allergen_code", "J01CE01",
                    "code_system", "http://www.whocc.no/atc",
                    "severity", "severe",
                    "reaction", "Anaphylaxis"));

            assertThat(a.getSubjectCpid()).isEqualTo("CPID-9");
            assertThat(a.getAllergenCode()).isEqualTo("J01CE01");
            assertThat(a.getSeverity()).isEqualTo("SEVERE");
            assertThat(a.getClinicalStatus()).isEqualTo("ACTIVE");
            assertThat(a.getRecordedBy()).isEqualTo("nurse-1");
            verify(accessGuard).requireCareRelationship(any(), eq("CPID-9"), any(), any());
            verify(outboxRepository).save(argThat(e -> "ALLERGY_RECORDED".equals(e.getEventType())));
        }
    }

    @Test
    void addWithoutAllergenRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            assertThatThrownBy(() -> service.add(Map.of("patient_id", "CPID-9")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("allergen");
        }
    }

    @Test
    void deactivateFlipsStatusNeverDeletes() {
        AllergyEntity a = new AllergyEntity();
        UUID id = UUID.randomUUID();
        a.setAllergyId(id);
        a.setTenantId(TENANT_ID);
        a.setSubjectCpid("CPID-9");
        a.setAllergen("Penicillin");
        a.setRecordedBy("nurse-1");
        when(allergyRepository.findById(id)).thenReturn(Optional.of(a));
        when(allergyRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            AllergyEntity out = service.deactivate(id);
            assertThat(out.getClinicalStatus()).isEqualTo("INACTIVE");
            assertThat(out.getDeactivatedAt()).isNotNull();
            verify(allergyRepository, never()).delete(any());
            verify(outboxRepository).save(argThat(e -> "ALLERGY_DEACTIVATED".equals(e.getEventType())));
        }
    }

    @Test
    void crossTenantDeactivateDeniedAsNotFound() {
        AllergyEntity a = new AllergyEntity();
        UUID id = UUID.randomUUID();
        a.setAllergyId(id);
        a.setTenantId(UUID.randomUUID()); // another tenant
        when(allergyRepository.findById(id)).thenReturn(Optional.of(a));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx());
            assertThatThrownBy(() -> service.deactivate(id))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not found");
        }
    }
}
