package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImmunizationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImmunizationRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImmunizationServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUBJECT = "CPID-CHILD-1";

    @Mock private ImmunizationRepository immunizationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private ImmunizationService immunizationService;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        immunizationService = new ImmunizationService(
                immunizationRepository, outboxRepository, new ObjectMapper(), accessGuard);
        context = new TrustContext(
                TENANT, "nurse-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
        when(immunizationRepository.save(any(ImmunizationEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void recordsABirthDoseWithVialTraceability() {
        ImmunizationEntity row = record(body -> {
            body.put("vaccine_name", "BCG");
            body.put("vaccine_code", "BCG");
            body.put("dose_number", 1);
            body.put("lot_number", "LOT-2026-03");
            body.put("expiry_date", "2027-01-31");
            body.put("route", "id");
            body.put("site", "left upper arm");
            body.put("occurrence_at", "2026-07-26T08:00:00Z");
        });

        assertThat(row.getStatus()).isEqualTo(ImmunizationEntity.STATUS_COMPLETED);
        assertThat(row.countsTowardsSeries()).isTrue();
        assertThat(row.getLotNumber()).isEqualTo("LOT-2026-03");
        assertThat(row.getExpiryDate()).isEqualTo("2027-01-31");
        assertThat(row.getRoute()).isEqualTo("ID");
        assertThat(row.getBodySite()).isEqualTo("left upper arm");
        assertThat(row.getDeliveryContext()).isEqualTo("ROUTINE");
    }

    @Test
    void acceptsTheLegacyBffFieldNamesUnchanged() {
        // The BFF has been sending dose_sequence and expiration_date since before this
        // endpoint existed; accepting them keeps the existing surfaces working as-is.
        ImmunizationEntity row = record(body -> {
            body.put("vaccine_name", "Measles-Rubella");
            body.put("dose_sequence", "MR");
            body.put("expiration_date", "2027-06-30");
        });

        assertThat(row.getSeries()).isEqualTo("MR");
        assertThat(row.getExpiryDate()).isEqualTo("2027-06-30");
    }

    @Test
    void aRefusalIsRecordedAsADistinctOutcomeWithItsReason() {
        ImmunizationEntity row = record(body -> {
            body.put("vaccine_name", "Measles-Rubella");
            body.put("status", "NOT_DONE_REFUSED");
            body.put("status_reason", "Caregiver declined, will discuss at next visit");
        });

        assertThat(row.getStatus()).isEqualTo(ImmunizationEntity.STATUS_REFUSED);
        assertThat(row.countsTowardsSeries()).isFalse();
        assertThat(row.getStatusReason()).contains("declined");
    }

    @Test
    void aDoseNotGivenWithoutAReasonIsRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            Map<String, Object> body = baseBody();
            body.put("vaccine_name", "OPV");
            body.put("status", "NOT_DONE_DEFERRED");

            assertThatThrownBy(() -> immunizationService.record(body))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("status_reason is required");
        }
    }

    @Test
    void aDoseReportedFromElsewhereDoesNotCountUntilVerified() {
        ImmunizationEntity row = record(body -> {
            body.put("vaccine_name", "Penta");
            body.put("dose_number", 2);
            body.put("status", "RECORDED_ELSEWHERE_PENDING_VERIFICATION");
            body.put("source_reference", "Caregiver reports dose at another clinic");
        });

        assertThat(row.countsTowardsSeries()).isFalse();
        assertThat(row.getStatusReason()).isNull();
    }

    @Test
    void verifyingAReportedDoseMakesItCountAndRecordsWhoConfirmedIt() {
        ImmunizationEntity pending = new ImmunizationEntity();
        pending.setImmunizationId(UUID.randomUUID());
        pending.setTenantId(TENANT);
        pending.setSubjectCpid(SUBJECT);
        pending.setVaccineName("Penta");
        pending.setOccurrenceAt(java.time.OffsetDateTime.parse("2026-05-01T09:00:00Z"));
        pending.setStatus(ImmunizationEntity.STATUS_PENDING_VERIFICATION);
        pending.setRecordedBy("nurse-1");
        when(immunizationRepository.findById(pending.getImmunizationId())).thenReturn(Optional.of(pending));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            ImmunizationEntity verified = immunizationService.verify(
                    pending.getImmunizationId(), Map.of("source_reference", "Child health card sighted"));

            assertThat(verified.countsTowardsSeries()).isTrue();
            assertThat(verified.getVerifiedBy()).isEqualTo("nurse-1");
            assertThat(verified.getVerifiedAt()).isNotNull();
            assertThat(verified.getSourceReference()).isEqualTo("Child health card sighted");
        }
    }

    @Test
    void anAlreadyCompletedDoseCannotBeVerifiedAgain() {
        ImmunizationEntity completed = new ImmunizationEntity();
        completed.setImmunizationId(UUID.randomUUID());
        completed.setTenantId(TENANT);
        completed.setStatus(ImmunizationEntity.STATUS_COMPLETED);
        when(immunizationRepository.findById(completed.getImmunizationId())).thenReturn(Optional.of(completed));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            assertThatThrownBy(() -> immunizationService.verify(
                    completed.getImmunizationId(), Map.of("source_reference", "card")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("pending verification");
        }
    }

    @Test
    void verifyingWithoutASourceIsRejected() {
        ImmunizationEntity pending = new ImmunizationEntity();
        pending.setImmunizationId(UUID.randomUUID());
        pending.setTenantId(TENANT);
        pending.setStatus(ImmunizationEntity.STATUS_PENDING_VERIFICATION);
        when(immunizationRepository.findById(pending.getImmunizationId())).thenReturn(Optional.of(pending));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            assertThatThrownBy(() -> immunizationService.verify(pending.getImmunizationId(), Map.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("source_reference");
        }
    }

    @Test
    void campaignDosesAreDistinguishedFromRoutineOnes() {
        ImmunizationEntity row = record(body -> {
            body.put("vaccine_name", "Measles-Rubella");
            body.put("delivery_context", "campaign");
            body.put("campaign_ref", "MR-CAMPAIGN-2026");
            body.put("outreach_location", "Chitungwiza primary school");
        });

        assertThat(row.getDeliveryContext()).isEqualTo("CAMPAIGN");
        assertThat(row.getCampaignRef()).isEqualTo("MR-CAMPAIGN-2026");
    }

    @Test
    void anUnknownStatusOrContextIsRejectedRatherThanStoredAsFreeText() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            Map<String, Object> badStatus = baseBody();
            badStatus.put("vaccine_name", "OPV");
            badStatus.put("status", "MAYBE");
            assertThatThrownBy(() -> immunizationService.record(badStatus))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown immunisation status");

            Map<String, Object> badContext = baseBody();
            badContext.put("vaccine_name", "OPV");
            badContext.put("delivery_context", "DOOR_TO_DOOR");
            assertThatThrownBy(() -> immunizationService.record(badContext))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown delivery_context");
        }
    }

    @Test
    void doseHistoryExcludesAnythingThatWasNotActuallyGiven() {
        when(immunizationRepository.findByTenantIdAndSubjectCpidOrderByOccurrenceAtAsc(TENANT, SUBJECT))
                .thenReturn(List.of(
                        stored(ImmunizationEntity.STATUS_COMPLETED, "BCG"),
                        stored(ImmunizationEntity.STATUS_REFUSED, "Measles-Rubella"),
                        stored(ImmunizationEntity.STATUS_PENDING_VERIFICATION, "Penta")));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            List<ImmunizationEntity> history = immunizationService.doseHistory(SUBJECT);

            assertThat(history).hasSize(1);
            assertThat(history.get(0).getVaccineName()).isEqualTo("BCG");
        }
    }

    @Test
    void missingVaccineNameIsRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);

            assertThatThrownBy(() -> immunizationService.record(baseBody()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("vaccine_name");
        }
    }

    @Test
    void everyRecordedDosePublishesAnOutboxEvent() {
        record(body -> body.put("vaccine_name", "BCG"));

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("pct.immunization.recorded");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("IMMUNIZATION");
        assertThat(captor.getValue().getPayload()).contains("BCG");
    }

    // --- helpers ---------------------------------------------------------------------

    private ImmunizationEntity record(Consumer<Map<String, Object>> customiser) {
        Map<String, Object> body = baseBody();
        customiser.accept(body);
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            return immunizationService.record(body);
        }
    }

    private static Map<String, Object> baseBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", SUBJECT);
        body.put("encounter_id", "ENC-1");
        return body;
    }

    private static ImmunizationEntity stored(String status, String vaccineName) {
        ImmunizationEntity row = new ImmunizationEntity();
        row.setStatus(status);
        row.setVaccineName(vaccineName);
        return row;
    }
}
