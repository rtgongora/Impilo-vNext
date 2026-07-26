package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.pct.integration.ClinicalKnowledgeIntegration;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamEpisodeEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ImamVisitEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImamEpisodeRepository;
import zw.gov.mohcc.impilo.pct.persistence.repository.ImamVisitRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImamServiceTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String SUBJECT = "CPID-CHILD-IMAM";

    @Mock private ImamEpisodeRepository episodeRepository;
    @Mock private ImamVisitRepository visitRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private ClinicalAccessGuard accessGuard;
    @Mock private ClinicalKnowledgeIntegration knowledge;

    private ImamService imamService;
    private TrustContext context;

    @BeforeEach
    void setUp() {
        imamService = new ImamService(episodeRepository, visitRepository, outboxRepository,
                new ObjectMapper(), accessGuard, knowledge);
        context = new TrustContext(
                TENANT, "nurse-1", "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);
        when(episodeRepository.save(any(ImamEpisodeEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(visitRepository.save(any(ImamVisitEntity.class)))
                .thenAnswer(i -> i.getArgument(0));
        when(episodeRepository.findByTenantIdAndSubjectCpidAndStatus(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(visitRepository.findByTenantIdAndImamEpisodeIdOrderByVisitNumberAsc(any(), any()))
                .thenReturn(List.of());
    }

    // ── enrolment routed by the classification ───────────────────────────────────────────────────

    @Test
    void enrolsStraightFromAnImnciClassificationAndKeepsTheProvenance() {
        // The wire this whole wave exists to build: the classification engine says "enrol in
        // outpatient therapeutic care" and there is now somewhere for that to land.
        eligibility(mapOf(
                "assessable", true, "eligible", true, "programme", "OTP",
                "admission_criterion", "IMNCI_CLASSIFICATION",
                "review_interval_days", 7, "attendance_grace_days", 2,
                "trace_after_missed_visits", 1, "defaulter_missed_visits", 2,
                "content_pack_id", "impilo.paediatric.imam-programme",
                "content_version", "engineering-seed-1.0.0",
                "approval_status", "ENGINEERING_SEED"));

        ImamEpisodeEntity episode = run(() -> imamService.enrol(body(b -> {
            b.put("source_classification", "UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION");
            b.put("muac_cm", "10.8");
            b.put("oedema", "ABSENT");
            b.put("enrolled_at", "2026-06-01T09:00:00Z");
        })));

        assertThat(episode.getProgramme()).isEqualTo("OTP");
        assertThat(episode.getEnrolmentSource()).isEqualTo("IMNCI_CLASSIFICATION");
        assertThat(episode.getSourceClassification()).isEqualTo("UNCOMPLICATED_SEVERE_ACUTE_MALNUTRITION");
        assertThat(episode.getAdmissionMuacCm()).isEqualByComparingTo("10.8");
        assertThat(episode.getReviewIntervalDays()).isEqualTo(7);
        assertThat(episode.getNextReviewDue()).isEqualTo(LocalDate.parse("2026-06-08"));
        // The rules the child was admitted under travel with the episode.
        assertThat(episode.getContentVersion()).isEqualTo("engineering-seed-1.0.0");
        assertThat(episode.getApprovalStatus()).isEqualTo("ENGINEERING_SEED");
        assertThat(episode.getDefaulterMissedVisits()).isEqualTo(2);
    }

    @Test
    void theCareRelationshipGuardRunsBeforeAnyWrite() {
        eligibility(Map.of("assessable", true, "eligible", true, "programme", "OTP"));
        run(() -> imamService.enrol(body(b -> b.put("muac_cm", "10.8"))));

        verify(accessGuard).requireCareRelationship(any(), any(), any(), any());
    }

    @Test
    void asecondOpenEpisodeIsRefusedBecauseItWouldSplitTheReviewHistory() {
        ImamEpisodeEntity open = episode("OTP");
        when(episodeRepository.findByTenantIdAndSubjectCpidAndStatus(TENANT, SUBJECT, "ACTIVE"))
                .thenReturn(Optional.of(open));

        assertThatThrownBy(() -> run(() -> imamService.enrol(body(b -> b.put("muac_cm", "10.8")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already enrolled")
                .hasMessageContaining("split the review history");

        verify(episodeRepository, never()).save(any());
    }

    @Test
    void anUnreachableKnowledgePlatformDoesNotGuessAProgramme() {
        // Guessing would guess a ration, a review interval and a discharge criterion with it.
        when(knowledge.imamEligibility(any(), anyMap())).thenReturn(null);

        assertThatThrownBy(() -> run(() -> imamService.enrol(body(b -> b.put("muac_cm", "10.8")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be resolved");
    }

    @Test
    void aClinicianMayStillEnrolByNamingTheProgrammeWhenTheEngineIsDown() {
        when(knowledge.imamEligibility(any(), anyMap())).thenReturn(null);

        ImamEpisodeEntity episode = run(() -> imamService.enrol(body(b -> {
            b.put("programme", "otp");
            b.put("muac_cm", "10.8");
        })));

        assertThat(episode.getProgramme()).isEqualTo("OTP");
        assertThat(episode.getAdmissionCriterion()).isEqualTo("CLINICIAN_DECISION");
        // And the episode says the pack was never consulted rather than implying it was.
        assertThat(episode.getApprovalStatus()).isEqualTo("UNVERIFIED_CONTENT_UNAVAILABLE");
    }

    @Test
    void aChildWhoseNutritionCouldNotBeAssessedIsNotEnrolledOnAGuess() {
        eligibility(Map.of("assessable", false, "eligible", false,
                "not_assessable_reason", "No nutritional measurement was supplied"));

        assertThatThrownBy(() -> run(() -> imamService.enrol(body(b -> { }))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not be assessed");
    }

    // ── reviews ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void aReviewStampsTheAssessmentAndAdvancesTheSchedule() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(mapOf(
                "discharge_eligible", false, "attendance_status", "ATTENDING",
                "response_status", "RESPONDING", "content_version", "engineering-seed-1.0.0",
                "recommended_sachets_per_week", 21, "therapeutic_food", "RUTF",
                "next_review_due", "2026-06-15",
                "actions", List.of("Continue treatment. Not yet dischargeable: MUAC_SUSTAINED.")));

        ImamVisitEntity visit = run(() -> imamService.recordVisit(episode.getImamEpisodeId(), body(b -> {
            b.put("visit_date", "2026-06-08T09:00:00Z");
            b.put("muac_cm", "11.8");
            b.put("weight_kg", "7.2");
            b.put("oedema", "ABSENT");
            b.put("danger_signs_present", false);
            b.put("rutf_sachets_issued", 21);
        })));

        assertThat(visit.getVisitNumber()).isEqualTo(1);
        assertThat(visit.getDischargeEligible()).isFalse();
        assertThat(visit.getAttendanceStatus()).isEqualTo("ATTENDING");
        assertThat(visit.getRutfSachetsRecommended()).isEqualByComparingTo("21");
        assertThat(visit.getRutfProductCode()).isEqualTo("RUTF");
        assertThat(visit.getNextReviewDue()).isEqualTo(LocalDate.parse("2026-06-15"));
        assertThat(episode.getLastContactOn()).isEqualTo(LocalDate.parse("2026-06-08"));
        assertThat(episode.getNextReviewDue()).isEqualTo(LocalDate.parse("2026-06-15"));
    }

    @Test
    void anUnreachableEngineLeavesDischargeReadinessUnknownRatherThanFalse() {
        // The distinction the whole pack turns on. Storing false would put a clinical finding about
        // the child on the record when the only fact was that a network call failed.
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        when(knowledge.imamProgress(any(), anyMap())).thenReturn(null);

        ImamVisitEntity visit = run(() -> imamService.recordVisit(episode.getImamEpisodeId(), body(b -> {
            b.put("visit_date", "2026-06-08T09:00:00Z");
            b.put("muac_cm", "12.9");
            b.put("oedema", "ABSENT");
        })));

        assertThat(visit.getDischargeEligible()).isNull();
        assertThat(visit.getAssessmentNote()).contains("not a finding about the child");
        // The review itself is still recorded: losing the measurement is worse than losing its
        // interpretation.
        verify(visitRepository).save(any(ImamVisitEntity.class));
    }

    @Test
    void aMissedReviewIsARowAndCarriesNoVisitDate() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("attendance_status", "TRACE_REQUIRED", "tracing_required", true,
                "consecutive_missed_visits", 1, "days_overdue", 4, "discharge_eligible", false));

        ImamVisitEntity visit = run(() -> imamService.recordVisit(episode.getImamEpisodeId(), body(b -> {
            b.put("attended", false);
            b.put("scheduled_for", "2026-06-08");
        })));

        assertThat(visit.isAttended()).isFalse();
        assertThat(visit.getVisitDate()).isNull();
        assertThat(visit.getScheduledFor()).isEqualTo(LocalDate.parse("2026-06-08"));
        // Last contact must not move: the child was not seen.
        assertThat(episode.getLastContactOn()).isNull();
    }

    @Test
    void aMissedReviewMayNotCarryAVisitDate() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);

        assertThatThrownBy(() -> run(() -> imamService.recordVisit(episode.getImamEpisodeId(), body(b -> {
            b.put("attended", false);
            b.put("visit_date", "2026-06-08T09:00:00Z");
        })))).isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot carry a visit date");
    }

    @Test
    void tracingRaisesItsOwnEventBecauseADifferentTeamActsOnIt() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("attendance_status", "DEFAULTER", "tracing_required", true,
                "consecutive_missed_visits", 2, "days_overdue", 11, "discharge_eligible", false));

        run(() -> imamService.recordVisit(episode.getImamEpisodeId(), body(b -> b.put("attended", false))));

        verify(outboxRepository, times(2)).save(any(EventOutboxEntity.class));
    }

    @Test
    void aReviewCannotBeRecordedAgainstAClosedEpisode() {
        ImamEpisodeEntity episode = episode("OTP");
        episode.setStatus("CLOSED");
        episode.setOutcome("CURED");
        stored(episode);

        assertThatThrownBy(() -> run(() -> imamService.recordVisit(episode.getImamEpisodeId(), body(b -> { }))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("closed");
    }

    // ── outcome ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void aCureIsNotCertifiedOnCriteriaThatWereNotMet() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("discharge_eligible", false, "unmet_criteria", List.of("MUAC_SUSTAINED"),
                "discharge_criteria", List.of(Map.of("code", "MUAC_SUSTAINED", "met", false,
                        "detail", "1 review(s) recorded; 2 consecutive are required."))));

        assertThatThrownBy(() -> run(() ->
                imamService.closeOutcome(episode.getImamEpisodeId(), body(b -> b.put("outcome", "CURED")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("cannot be recorded as CURED")
                .hasMessageContaining("false reassurance");
    }

    @Test
    void aClinicianMayOverrideButTheReasonIsStoredOnTheEpisode() {
        // Blocking the discharge outright would be blocking care. Requiring the reason is what keeps
        // the programme's cure rate honest.
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("discharge_eligible", false, "unmet_criteria", List.of("MINIMUM_STAY"),
                "discharge_criteria", List.of(Map.of("code", "MINIMUM_STAY", "met", false, "detail", "9 days"))));

        ImamEpisodeEntity closed = run(() -> imamService.closeOutcome(episode.getImamEpisodeId(), body(b -> {
            b.put("outcome", "CURED");
            b.put("discharge_override_reason", "Family relocating; recovered on all indicators bar length of stay.");
        })));

        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getOutcome()).isEqualTo("CURED");
        assertThat(closed.getDischargeCriteriaMet()).isFalse();
        assertThat(closed.getDischargeOverrideReason()).contains("Family relocating");
        assertThat(closed.getDischargeCriteriaDetail()).contains("NOT MET MINIMUM_STAY");
    }

    @Test
    void anUnevaluableAssessmentAlsoRequiresAReasonBeforeCertifyingACure() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        when(knowledge.imamProgress(any(), anyMap())).thenReturn(null);

        assertThatThrownBy(() -> run(() ->
                imamService.closeOutcome(episode.getImamEpisodeId(), body(b -> b.put("outcome", "CURED")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("could not be evaluated");
    }

    @Test
    void anOutcomeThatIsNotACureNeedsNoDischargeEvidence() {
        // A child who died or defaulted did not meet the discharge criteria, and demanding evidence
        // that they did would make the honest outcome the hardest one to record.
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("discharge_eligible", false, "unmet_criteria", List.of("MUAC_SUSTAINED"),
                "discharge_criteria", List.of()));

        ImamEpisodeEntity closed = run(() -> imamService.closeOutcome(episode.getImamEpisodeId(),
                body(b -> b.put("outcome", "DEFAULTED"))));

        assertThat(closed.getOutcome()).isEqualTo("DEFAULTED");
        assertThat(closed.getDischargeCriteriaMet()).isFalse();
    }

    @Test
    void theDischargeIsJudgedAsOfTheStatedDischargeDateNotTheDayItIsTypedIn() {
        // Caught by deploying. A discharge backfilled on Monday from Friday's register was being
        // evaluated against today, so the weekend counted as a gap in observation and a properly
        // earned cure was refused. The clinical date is the one the clinician states.
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("discharge_eligible", true, "unmet_criteria", List.of(),
                "discharge_criteria", List.of()));

        run(() -> imamService.closeOutcome(episode.getImamEpisodeId(), body(b -> {
            b.put("outcome", "CURED");
            b.put("outcome_at", "2026-06-15T09:00:00Z");
        })));

        org.mockito.ArgumentCaptor<Map<String, Object>> sent = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(knowledge).imamProgress(any(), sent.capture());
        assertThat(sent.getValue().get("as_of")).isEqualTo("2026-06-15");
    }

    @Test
    void aWriteWithNoIdentifiableAuthorIsRejectedBeforeItReachesTheDatabase() {
        // Caught by deploying: with no actor on the trust context the insert died on the NOT NULL
        // constraint and the clinician got a 500 that told them nothing. A clinical record with no
        // author is not a record — you cannot ask whoever wrote it what they saw.
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        TrustContext anonymous = new TrustContext(
                TENANT, null, "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        assertThatThrownBy(() -> runAs(anonymous, () ->
                imamService.recordVisit(episode.getImamEpisodeId(), body(b -> b.put("muac_cm", "11.9")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("no identifiable author");

        verify(visitRepository, never()).save(any());
    }

    @Test
    void anExplicitRecordedByStandsInForAMissingActorContext() {
        // An offline pack or a register-entry job legitimately names its own author.
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("discharge_eligible", false, "attendance_status", "ATTENDING"));
        TrustContext anonymous = new TrustContext(
                TENANT, null, "PROVIDER", "TREATMENT", null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, AccessMode.INTERNAL);

        ImamVisitEntity visit = runAs(anonymous, () ->
                imamService.recordVisit(episode.getImamEpisodeId(), body(b -> {
                    b.put("visit_date", "2026-06-08T09:00:00Z");
                    b.put("muac_cm", "11.9");
                    b.put("recorded_by", "nurse.chienda");
                })));

        assertThat(visit.getRecordedBy()).isEqualTo("nurse.chienda");
    }

    @Test
    void anUnknownOutcomeIsRejectedRatherThanStored() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);

        assertThatThrownBy(() -> run(() -> imamService.closeOutcome(episode.getImamEpisodeId(),
                body(b -> b.put("outcome", "IMPROVED")))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("outcome must be one of");
    }

    @Test
    void aCureWithTheCriteriaMetNeedsNoOverride() {
        ImamEpisodeEntity episode = episode("OTP");
        stored(episode);
        progress(Map.of("discharge_eligible", true, "unmet_criteria", List.of(),
                "discharge_criteria", List.of(Map.of("code", "MUAC_SUSTAINED", "met", true,
                        "detail", "The last 2 reviews are all at or above 12.5 cm."))));

        ImamEpisodeEntity closed = run(() -> imamService.closeOutcome(episode.getImamEpisodeId(),
                body(b -> b.put("outcome", "CURED"))));

        assertThat(closed.getDischargeCriteriaMet()).isTrue();
        assertThat(closed.getDischargeOverrideReason()).isNull();
        assertThat(closed.getDischargeCriteriaDetail()).contains("MET MUAC_SUSTAINED");
    }

    // ── the tracing worklist ─────────────────────────────────────────────────────────────────────

    @Test
    void theTracingQueueBandsChildrenByTheRulesTheyWereAdmittedUnder() {
        ImamEpisodeEntity traceMe = episode("OTP");
        traceMe.setLastContactOn(LocalDate.parse("2026-06-08"));
        traceMe.setNextReviewDue(LocalDate.parse("2026-06-15"));

        ImamEpisodeEntity defaulted = episode("OTP");
        defaulted.setSubjectCpid("CPID-CHILD-2");
        defaulted.setLastContactOn(LocalDate.parse("2026-06-01"));
        defaulted.setNextReviewDue(LocalDate.parse("2026-06-08"));

        when(episodeRepository.findOverdue(any(), any(), any())).thenReturn(List.of(traceMe, defaulted));

        List<Map<String, Object>> queue = run(() -> imamService.tracingQueue(null, LocalDate.parse("2026-06-19")));

        assertThat(queue).hasSize(2);
        // Most overdue first: a worklist that buries the child missing longest is not a worklist.
        assertThat(queue.get(0).get("patient_id")).isEqualTo("CPID-CHILD-2");
        assertThat(queue.get(0).get("attendance_status")).isEqualTo("DEFAULTER");
        assertThat(queue.get(1).get("attendance_status")).isEqualTo("TRACE_REQUIRED");
    }

    @Test
    void aChildStillWithinTheGraceWindowIsNotOnTheTracingQueue() {
        ImamEpisodeEntity recent = episode("OTP");
        recent.setLastContactOn(LocalDate.parse("2026-06-08"));
        recent.setNextReviewDue(LocalDate.parse("2026-06-15"));
        when(episodeRepository.findOverdue(any(), any(), any())).thenReturn(List.of(recent));

        List<Map<String, Object>> queue = run(() -> imamService.tracingQueue(null, LocalDate.parse("2026-06-16")));

        assertThat(queue).isEmpty();
    }

    @Test
    void aChildEnrolledAndNeverReviewedIsFlaggedRatherThanLookingLikeAnyOtherOverdueRow() {
        // Silence is never reassurance: this is the most urgent row on the list, not the quietest.
        ImamEpisodeEntity neverSeen = episode("OTP");
        neverSeen.setLastContactOn(null);
        neverSeen.setNextReviewDue(LocalDate.parse("2026-06-08"));
        when(episodeRepository.findOverdue(any(), any(), any())).thenReturn(List.of(neverSeen));

        List<Map<String, Object>> queue = run(() -> imamService.tracingQueue(null, LocalDate.parse("2026-06-26")));

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).get("never_reviewed")).isEqualTo(true);
        assertThat(queue.get(0).get("attendance_status")).isEqualTo("DEFAULTER");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    /** {@code Map.of} caps at ten pairs and the engine responses are wider than that. */
    private static Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }

    private void eligibility(Map<String, Object> response) {
        when(knowledge.imamEligibility(any(), anyMap())).thenReturn(new LinkedHashMap<>(response));
    }

    private void progress(Map<String, Object> response) {
        when(knowledge.imamProgress(any(), anyMap())).thenReturn(new LinkedHashMap<>(response));
    }

    private void stored(ImamEpisodeEntity episode) {
        when(episodeRepository.findByTenantIdAndImamEpisodeId(TENANT, episode.getImamEpisodeId()))
                .thenReturn(Optional.of(episode));
    }

    private static ImamEpisodeEntity episode(String programme) {
        ImamEpisodeEntity e = new ImamEpisodeEntity();
        e.setImamEpisodeId(UUID.randomUUID());
        e.setTenantId(TENANT);
        e.setSubjectCpid(SUBJECT);
        e.setProgramme(programme);
        e.setStatus("ACTIVE");
        e.setEnrolledAt(OffsetDateTime.parse("2026-06-01T09:00:00Z"));
        e.setEnrolledBy("nurse-1");
        e.setAdmissionOedema("ABSENT");
        e.setAdmissionMuacCm(new BigDecimal("10.8"));
        e.setReviewIntervalDays(7);
        e.setAttendanceGraceDays(2);
        e.setTraceAfterMissedVisits(1);
        e.setDefaulterMissedVisits(2);
        e.setNextReviewDue(LocalDate.parse("2026-06-08"));
        return e;
    }

    private static Map<String, Object> body(java.util.function.Consumer<Map<String, Object>> customiser) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("patient_id", SUBJECT);
        body.put("encounter_id", "ENC-1");
        customiser.accept(body);
        return body;
    }

    private <T> T run(Supplier<T> action) {
        return runAs(context, action);
    }

    private <T> T runAs(TrustContext ctx, Supplier<T> action) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            holder.when(TrustContextHolder::require).thenReturn(ctx);
            return action.get();
        }
    }
}
