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
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ObservationEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ObservationRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ObservationServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUBJECT = "CPID-CHILD-1";

    @Mock private ObservationRepository observationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;

    private ObservationService service;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        service = new ObservationService(observationRepository, outboxRepository,
                new ObjectMapper(), accessGuard);
        context = new TrustContext(TENANT, "nurse-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        when(observationRepository.save(any(ObservationEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(observationRepository.findByTenantIdAndDerivedFromTypeAndDerivedFromIdAndCode(
                any(), anyString(), anyString(), anyString())).thenReturn(Optional.empty());
    }

    private ObservationEntity record(Consumer<Map<String, Object>> customiser) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", SUBJECT);
        customiser.accept(body);
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            return service.record(body);
        }
    }

    // --- the rule this table exists for ------------------------------------------------------

    @Test
    void anObservationMustEitherCarryAValueOrSayWhyItHasNone() {
        // A row with neither claims to be an observation and observes nothing. Rejecting it here
        // rather than at the database gives the caller a message they can act on.
        assertThatThrownBy(() -> record(body -> body.put("code", "BODY_TEMPERATURE")))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("data_absent_reason");

        verify(observationRepository, never()).save(any());
    }

    @Test
    void notAssessedIsRecordedAsAnAbsenceRatherThanAsAValue() {
        // The distinction the whole pack turns on: "nobody took the temperature" is a fact worth
        // storing, and it is not the same fact as a normal temperature.
        ObservationEntity row = record(body -> {
            body.put("code", "BODY_TEMPERATURE");
            body.put("data_absent_reason", "NOT_ASSESSED");
        });

        assertThat(row.getDataAbsentReason()).isEqualTo("NOT_ASSESSED");
        assertThat(row.getValueQuantity()).isNull();
        assertThat(row.getValueText()).isNull();
    }

    @Test
    void aQuantityWithoutAUnitIsRejectedBecauseItIsNotAMeasurement() {
        assertThatThrownBy(() -> record(body -> {
            body.put("code", "BODY_TEMPERATURE");
            body.put("value_quantity", 38.4);
        })).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("unit");
    }

    // --- ordinary writes ------------------------------------------------------------------------

    @Test
    void aQuantitativeObservationKeepsItsValueUnitAndInterpretation() {
        ObservationEntity row = record(body -> {
            body.put("code", "BODY_TEMPERATURE");
            body.put("value_quantity", 38.4);
            body.put("value_unit", "Cel");
            body.put("interpretation", "HIGH");
        });

        assertThat(row.getValueQuantity()).isEqualByComparingTo("38.4");
        assertThat(row.getValueUnit()).isEqualTo("Cel");
        assertThat(row.getInterpretation()).isEqualTo("HIGH");
        assertThat(row.getSubjectCpid()).isEqualTo(SUBJECT);
    }

    @Test
    void theCareRelationshipIsCheckedBeforeAnythingIsWritten() {
        record(body -> {
            body.put("code", "BODY_TEMPERATURE");
            body.put("value_boolean", true);
        });

        verify(accessGuard).requireCareRelationship(any(), anyString(), any(), any());
    }

    // --- provenance and correction ----------------------------------------------------------------

    @Test
    void resubmittingAFormAmendsTheObservationItPreviouslyAsserted() {
        // Otherwise a corrected form leaves two contradictory observations on the record and no
        // way to tell which one the clinician meant.
        ObservationEntity existing = new ObservationEntity();
        existing.setObservationId(UUID.randomUUID());
        existing.setTenantId(TENANT);
        existing.setSubjectCpid(SUBJECT);
        existing.setCode("RESPIRATORY_RATE");
        existing.setValueQuantity(new java.math.BigDecimal("52"));
        when(observationRepository.findByTenantIdAndDerivedFromTypeAndDerivedFromIdAndCode(
                TENANT, "FORM_RESPONSE", "resp-1", "RESPIRATORY_RATE"))
                .thenReturn(Optional.of(existing));

        ObservationEntity amended = record(body -> {
            body.put("code", "RESPIRATORY_RATE");
            body.put("value_quantity", 44);
            body.put("value_unit", "/min");
            body.put("derived_from_type", "FORM_RESPONSE");
            body.put("derived_from_id", "resp-1");
        });

        assertThat(amended.getObservationId()).isEqualTo(existing.getObservationId());
        assertThat(amended.getValueQuantity()).isEqualByComparingTo("44");
        assertThat(amended.getStatus()).isEqualTo("AMENDED");
    }

    // --- the shared-record boundary ------------------------------------------------------------------

    @Test
    void theOutboxPayloadCarriesNoIdentifyingDataBecauseTheShrIsCpidOnly() {
        record(body -> {
            body.put("code", "BODY_TEMPERATURE");
            body.put("value_quantity", 38.4);
            body.put("value_unit", "Cel");
            // A caller that mistakenly includes identity must not get it published.
            body.put("patient_name", "Tendai Moyo");
            body.put("date_of_birth", "2025-03-14");
            body.put("national_id", "63-1234567-A-42");
        });

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        EventOutboxEntity event = captor.getValue();

        assertThat(event.getEventType()).isEqualTo("pct.observation.recorded");
        assertThat(event.getAggregateType()).isEqualTo("OBSERVATION");
        assertThat(event.getPayload())
                .contains(SUBJECT)
                .doesNotContain("Tendai", "Moyo", "2025-03-14", "63-1234567");
    }

    @Test
    void anAbsentObservationIsStillPublishedSoTheSharedRecordAgreesWithTheSource() {
        // Dropping these would make the shared record quietly more reassuring than the clinical
        // record it mirrors.
        record(body -> {
            body.put("code", "BODY_TEMPERATURE");
            body.put("data_absent_reason", "NOT_ASSESSED");
        });

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getPayload()).contains("NOT_ASSESSED");
    }

    @Test
    void aMissingPatientIsRejected() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("code", "BODY_TEMPERATURE");
            body.put("value_boolean", true);

            assertThatThrownBy(() -> service.record(body))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("patient_id");
        }
    }
}
