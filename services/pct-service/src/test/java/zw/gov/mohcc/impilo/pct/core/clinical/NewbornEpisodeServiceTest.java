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
import zw.gov.mohcc.impilo.pct.persistence.entity.NewbornBirthRecordEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.NewbornBirthRecordRepository;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NewbornEpisodeServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String BABY = "CPID-BABY-1";
    private static final String MOTHER = "CPID-MOTHER-1";

    @Mock private NewbornBirthRecordRepository birthRecordRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private NewbornEpisodeOpener episodeOpener;

    private NewbornEpisodeService newbornEpisodeService;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        newbornEpisodeService = new NewbornEpisodeService(
                birthRecordRepository, outboxRepository, new ObjectMapper(), episodeOpener);
        context = new TrustContext(
                TENANT, "midwife-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        when(birthRecordRepository.save(any(NewbornBirthRecordEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(birthRecordRepository.findByTenantIdAndSubjectCpid(any(), anyString()))
                .thenReturn(Optional.empty());

        when(episodeOpener.open(any(), anyString(), any()))
                .thenReturn(new NewbornEpisodeOpener.OpenedEpisode("J-NEWBORN-1", UUID.randomUUID().toString()));
    }

    @Test
    void opensTheBabysOwnJourneyAndEncounterSoLaterWritesResolveToTheChild() {
        NewbornBirthRecordEntity record = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("mother_cpid", MOTHER);
            body.put("outcome", "LIVE_BIRTH");
            body.put("apgar_1", 8);
            body.put("apgar_5", 9);
        });

        assertThat(record.getSubjectCpid()).isEqualTo(BABY);
        assertThat(record.getMotherCpid()).isEqualTo(MOTHER);
        assertThat(record.getJourneyId()).isEqualTo("J-NEWBORN-1");
        assertThat(record.getEncounterId()).isNotBlank();
        assertThat(record.getApgarFiveMinute()).isEqualTo(9);
    }

    @Test
    void aReplayedDeliveryEventDoesNotCreateASecondRecordForTheSameBaby() {
        NewbornBirthRecordEntity first = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("mother_cpid", MOTHER);
            body.put("outcome", "LIVE_BIRTH");
        });

        // The second delivery of the same event finds the existing record.
        when(birthRecordRepository.findByTenantIdAndSubjectCpid(TENANT, BABY)).thenReturn(Optional.of(first));

        NewbornBirthRecordEntity second = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("mother_cpid", MOTHER);
            body.put("outcome", "LIVE_BIRTH");
        });

        assertThat(second.getBirthRecordId()).isEqualTo(first.getBirthRecordId());
        // The journey is opened once, not on every replay.
        verify(episodeOpener, times(1)).open(any(), anyString(), any());
    }

    @Test
    void twinsGetSeparateRecordsBecauseTheKeyIsTheBabyNotTheDelivery() {
        NewbornBirthRecordEntity first = ensure(body -> {
            body.put("baby_cpid", "CPID-TWIN-A");
            body.put("mother_cpid", MOTHER);
            body.put("birth_order", 1);
            body.put("multiple_birth", true);
        });
        NewbornBirthRecordEntity second = ensure(body -> {
            body.put("baby_cpid", "CPID-TWIN-B");
            body.put("mother_cpid", MOTHER);
            body.put("birth_order", 2);
            body.put("multiple_birth", true);
        });

        assertThat(first.getBirthRecordId()).isNotEqualTo(second.getBirthRecordId());
        assertThat(first.getBirthOrder()).isEqualTo(1);
        assertThat(second.getBirthOrder()).isEqualTo(2);
        assertThat(second.isMultipleBirth()).isTrue();
        verify(episodeOpener, times(2)).open(any(), anyString(), any());
    }

    @Test
    void aLaterSourceEnrichesTheRecordWithoutErasingWhatWasAlreadyKnown() {
        NewbornBirthRecordEntity fromTheatre = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("mother_cpid", MOTHER);
            body.put("apgar_5", 9);
            body.put("delivery_source", "THEATRE");
        });
        when(birthRecordRepository.findByTenantIdAndSubjectCpid(TENANT, BABY)).thenReturn(Optional.of(fromTheatre));

        // The maternity form arrives later with the measurements theatre did not carry.
        NewbornBirthRecordEntity enriched = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("birth_weight_grams", 2350);
            body.put("gestational_age_weeks", 34);
            body.put("delivery_source", "MATERNITY_FORM");
        });

        assertThat(enriched.getApgarFiveMinute()).isEqualTo(9);        // preserved
        assertThat(enriched.getBirthWeightGrams()).isEqualTo(2350);    // added
        assertThat(enriched.getGestationalAgeWeeks()).isEqualTo(34);
        assertThat(enriched.isPreterm()).isTrue();
        assertThat(enriched.isLowBirthWeight()).isTrue();
    }

    @Test
    void gestationalAgeIsAvailableToLaterGrowthScoring() {
        NewbornBirthRecordEntity record = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("gestational_age_weeks", 32);
        });
        when(birthRecordRepository.findByTenantIdAndSubjectCpid(TENANT, BABY)).thenReturn(Optional.of(record));

        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            assertThat(newbornEpisodeService.gestationalAgeWeeks(BABY)).isEqualTo(32);
        }
    }

    @Test
    void essentialNewbornCareIsRecordedAsGivenOrNotAssessedButNeverAssumed() {
        NewbornBirthRecordEntity record = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("skin_to_skin", "DONE");
            body.put("vitamin_k_given", "yes");
            // eye prophylaxis deliberately absent
        });

        assertThat(record.getSkinToSkin()).isEqualTo("DONE");
        assertThat(record.getVitaminKGiven()).isEqualTo("DONE");
        assertThat(record.getEyeProphylaxisGiven()).isEqualTo("NOT_ASSESSED");
    }

    @Test
    void aDeliveryWithoutABabyIdentityIsRejectedRatherThanMintingOne() {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("mother_cpid", MOTHER);

            assertThatThrownBy(() -> newbornEpisodeService.ensureNewbornEpisode(body))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("provisional VITO identity");
        }
        verify(birthRecordRepository, never()).save(any());
    }

    @Test
    void theBirthRecordSurvivesWhenTheEpisodeCannotBeOpened() {
        // Losing the delivery facts would be worse than losing the workflow scaffolding.
        //
        // The opener returns null rather than throwing, and that distinction is the fix for a
        // real production failure: when this service caught the exception itself, the failed
        // insert had already marked the transaction rollback-only, so the birth record was
        // discarded at commit even though the code looked like it recovered. The opener now
        // runs in its own transaction (REQUIRES_NEW) and reports failure by returning null.
        when(episodeOpener.open(any(), anyString(), any())).thenReturn(null);

        NewbornBirthRecordEntity record = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("birth_weight_grams", 3200);
        });

        assertThat(record.getBirthWeightGrams()).isEqualTo(3200);
        assertThat(record.getJourneyId()).isNull();
        assertThat(record.getEncounterId()).isNull();
    }

    @Test
    void openingAnEpisodeEmitsAnEventAndEnrichingItEmitsADifferentOne() {
        NewbornBirthRecordEntity created = ensure(body -> body.put("baby_cpid", BABY));

        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("pct.newborn.episode.opened");
        assertThat(captor.getValue().getAggregateType()).isEqualTo("NEWBORN_BIRTH_RECORD");

        when(birthRecordRepository.findByTenantIdAndSubjectCpid(TENANT, BABY)).thenReturn(Optional.of(created));
        ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("birth_weight_grams", 3100);
        });

        verify(outboxRepository, times(2)).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo("pct.newborn.record.updated");
    }

    @Test
    void aStillbirthIsRecordedAsSuchRatherThanDefaultingToALiveBirth() {
        NewbornBirthRecordEntity record = ensure(body -> {
            body.put("baby_cpid", BABY);
            body.put("outcome", "STILLBIRTH");
        });

        assertThat(record.getBirthOutcome()).isEqualTo("STILLBIRTH");
    }

    // --- helpers ---------------------------------------------------------------------

    private NewbornBirthRecordEntity ensure(Consumer<Map<String, Object>> customiser) {
        Map<String, Object> body = new LinkedHashMap<>();
        customiser.accept(body);
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(context);
            return newbornEpisodeService.ensureNewbornEpisode(body);
        }
    }
}
