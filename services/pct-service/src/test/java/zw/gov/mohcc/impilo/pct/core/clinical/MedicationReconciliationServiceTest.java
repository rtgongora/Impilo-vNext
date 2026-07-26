package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.pct.integration.OrosMedicationIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.MedicationReconciliationItemEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationItemRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.MedicationReconciliationRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Medication reconciliation, and the one rule everything here serves: <strong>an unfinished
 * reconciliation must never read as a clean one.</strong>
 */
@ExtendWith(MockitoExtension.class)
class MedicationReconciliationServiceTest {

    @Mock private MedicationReconciliationRepository reconciliationRepository;
    @Mock private MedicationReconciliationItemRepository itemRepository;
    @Mock private ClinicalAccessGuard accessGuard;
    @Mock private OrosMedicationIntegration orosMedications;

    private MedicationReconciliationService service;
    private static final UUID TENANT = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new MedicationReconciliationService(
                reconciliationRepository, itemRepository, accessGuard, orosMedications);
        lenient().when(reconciliationRepository.save(any())).thenAnswer(i -> {
            MedicationReconciliationEntity e = i.getArgument(0);
            if (e.getReconciliationId() == null) e.setReconciliationId(UUID.randomUUID());
            return e;
        });
        lenient().when(itemRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }

    private <T> T asClinician(Supplier<T> action) {
        try (MockedStatic<TrustContextHolder> h = mockStatic(TrustContextHolder.class)) {
            h.when(TrustContextHolder::require).thenReturn(new TrustContext(
                    TENANT, "clinician-1", "PROVIDER", "TREATMENT", null,
                    UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
            return action.get();
        }
    }

    private static Map<String, Object> start(String context) {
        Map<String, Object> b = new HashMap<>();
        b.put("subject_cpid", "CPID-9");
        b.put("context", context);
        return b;
    }

    // ── Opening ─────────────────────────────────────────────────────────────────

    /**
     * The distinction the whole S2S design protects. OROS unreachable returns null, which must not
     * become "the patient has no medicines" — a clinician would reconcile against an empty list and
     * conclude nothing was prescribed.
     */
    @Test
    void reportsWhetherTheSystemListCouldBeReadAtAll() {
        when(orosMedications.currentMedicationCodes(any(), any())).thenReturn(null);
        Map<String, Object> unavailable = asClinician(() -> service.start(start("ADMISSION")));
        assertThat(unavailable.get("systemListAvailable")).isEqualTo(false);
        assertThat((List<?>) unavailable.get("systemMedicationCodes")).isEmpty();

        when(orosMedications.currentMedicationCodes(any(), any())).thenReturn(List.of());
        Map<String, Object> genuinelyNone = asClinician(() -> service.start(start("ADMISSION")));
        assertThat(genuinelyNone.get("systemListAvailable")).isEqualTo(true);
    }

    /** A clinician in front of a patient must still be able to record what they are told. */
    @Test
    void opensEvenWhenTheSystemListCannotBeRead() {
        when(orosMedications.currentMedicationCodes(any(), any())).thenReturn(null);
        Map<String, Object> out = asClinician(() -> service.start(start("CLINIC_REVIEW")));
        MedicationReconciliationEntity r = (MedicationReconciliationEntity) out.get("reconciliation");
        assertThat(r.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(r.getStartedBy()).isEqualTo("clinician-1");
    }

    @Test
    void refusesAContextOutsideTheVocabulary() {
        assertThatThrownBy(() -> asClinician(() -> service.start(start("WARD_ROUND"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Completing ──────────────────────────────────────────────────────────────

    private MedicationReconciliationEntity open() {
        MedicationReconciliationEntity r = new MedicationReconciliationEntity();
        r.setReconciliationId(UUID.randomUUID());
        r.setTenantId(TENANT);
        r.setSubjectCpid("CPID-9");
        r.setStatus("IN_PROGRESS");
        r.setContext("ADMISSION");
        r.setStartedBy("clinician-1");
        lenient().when(reconciliationRepository.findById(r.getReconciliationId()))
                .thenReturn(Optional.of(r));
        return r;
    }

    private static MedicationReconciliationItemEntity item(String finding, String action) {
        MedicationReconciliationItemEntity i = new MedicationReconciliationItemEntity();
        i.setDisplay("Amlodipine 5mg");
        i.setFinding(finding);
        i.setAction(action);
        return i;
    }

    /**
     * The claim a completed reconciliation makes is "no discrepancies outstanding". Letting it be
     * made over an undecided discrepancy is how one that was noticed becomes one silently accepted.
     */
    @Test
    void refusesToCompleteWhileADiscrepancyHasNoDecidedAction() {
        MedicationReconciliationEntity r = open();
        when(itemRepository.findByReconciliationId(r.getReconciliationId()))
                .thenReturn(List.of(item("NOT_TAKING", null)));

        assertThatThrownBy(() -> asClinician(() -> service.complete(r.getReconciliationId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Amlodipine 5mg");
    }

    @Test
    void completesOnceEveryDiscrepancyHasAnAction() {
        MedicationReconciliationEntity r = open();
        when(itemRepository.findByReconciliationId(r.getReconciliationId()))
                .thenReturn(List.of(item("NOT_TAKING", "STOP"), item("MATCHED", null)));

        MedicationReconciliationEntity done = asClinician(() -> service.complete(r.getReconciliationId()));
        assertThat(done.getStatus()).isEqualTo("COMPLETED");
        assertThat(done.getCompletedBy()).isEqualTo("clinician-1");
        assertThat(done.getCompletedAt()).isNotNull();
    }

    /**
     * UNABLE_TO_VERIFY needs no action because recording that it could not be established IS the
     * decision — but it is deliberately not MATCHED, so it never reads as agreement.
     */
    @Test
    void anUnverifiableMedicineDoesNotBlockCompletionAndIsNotAMatch() {
        MedicationReconciliationEntity r = open();
        when(itemRepository.findByReconciliationId(r.getReconciliationId()))
                .thenReturn(List.of(item("UNABLE_TO_VERIFY", null)));

        assertThat(asClinician(() -> service.complete(r.getReconciliationId())).getStatus())
                .isEqualTo("COMPLETED");
        assertThat(MedicationReconciliationService.NEEDS_ACTION).doesNotContain("UNABLE_TO_VERIFY");
        assertThat(MedicationReconciliationService.FINDINGS).contains("UNABLE_TO_VERIFY", "MATCHED");
    }

    @Test
    void abandoningRequiresAReason() {
        MedicationReconciliationEntity r = open();
        assertThatThrownBy(() -> asClinician(() -> service.abandon(r.getReconciliationId(), "  ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(asClinician(() -> service.abandon(r.getReconciliationId(), "Patient transferred"))
                .getAbandonedReason()).isEqualTo("Patient transferred");
    }

    // ── Items ───────────────────────────────────────────────────────────────────

    /** Traditional and OTC medicines are items with a source, so they reach interaction checking. */
    @Test
    void recordsTraditionalAndOverTheCounterMedicinesAsFirstClassItems() {
        MedicationReconciliationEntity r = open();
        Map<String, Object> b = new HashMap<>();
        b.put("display", "Herbal remedy for swelling");
        b.put("finding", "TAKING_UNPRESCRIBED");
        b.put("source", "traditional");
        b.put("patient_says", "A cup each morning");

        MedicationReconciliationItemEntity i =
                asClinician(() -> service.addItem(r.getReconciliationId(), b));
        assertThat(i.getSource()).isEqualTo("TRADITIONAL");
        assertThat(i.getPatientSays()).isEqualTo("A cup each morning");
    }

    @Test
    void refusesToAddFindingsToAReconciliationThatIsNoLongerOpen() {
        MedicationReconciliationEntity r = open();
        r.setStatus("COMPLETED");
        Map<String, Object> b = new HashMap<>();
        b.put("display", "X");
        b.put("finding", "MATCHED");
        assertThatThrownBy(() -> asClinician(() -> service.addItem(r.getReconciliationId(), b)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("COMPLETED");
    }
}
