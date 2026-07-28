package zw.gov.mohcc.impilo.pct.core.clinical;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationEntity;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationFindingEntity;
import zw.gov.mohcc.impilo.pct.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The examination's boundary into the shared health record (brief.md §19).
 *
 * <p>Companion to butano's {@code ConditionShrBoundaryTest}. The same standing rule applies —
 * identifying data stays in VITO and the SHR holds a CPID — and it is enforced at both ends: this
 * file asserts what the producer puts on the wire, and {@code ClinicalImpressionShrBoundaryTest} in
 * butano-service asserts what the consumer will read off it. Either alone would be a rule that holds
 * until someone changes the other side.</p>
 *
 * <p>Named {@code *Test} and not {@code *IT} deliberately: surefire skips {@code *IT} in this repo,
 * and a boundary test that never runs is worse than no boundary test, because it reads as coverage.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExaminationShrBoundaryTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000032");
    private static final String JOURNEY = "77777777-7777-4777-8777-777777777771";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired
    private ExaminationService service;

    @Autowired
    private EventOutboxRepository outboxRepository;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "clin-shr-1", "PRACTITIONER", "EMERGENCY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clearTrustContext() {
        TrustContextHolder.clear();
    }

    // --- the payload the producer actually puts on the wire ------------------------------------

    private static ExaminationFindingEntity finding(String region, String state, String detail,
                                                    String findingCode) {
        ExaminationFindingEntity f = new ExaminationFindingEntity();
        f.setFindingId(UUID.randomUUID());
        f.setTenantId(TENANT);
        f.setExaminationId(UUID.randomUUID());
        f.setRegion(region);
        f.setState(state);
        f.setDetail(detail);
        f.setFindingCode(findingCode);
        f.setSite("plantar first metatarsal head");
        f.setGraphic("DIABETIC_FOOT");
        f.setLaterality("LEFT");
        return f;
    }

    private static ExaminationEntity examination() {
        ExaminationEntity e = new ExaminationEntity();
        e.setExaminationId(UUID.fromString("11111111-1111-4111-8111-111111111111"));
        e.setTenantId(TENANT);
        e.setSubjectCpid("cpid-exam-shr");
        e.setJourneyId(UUID.fromString(JOURNEY));
        e.setExaminedAt(OffsetDateTime.parse("2026-07-28T09:15:30Z"));
        e.setExaminedBy("clin-shr-1");
        e.setContextNote("Seen with her sister Rudo Moyo, who translated");
        return e;
    }

    @Test
    void freeTextDetailNeverCrossesEvenWhenTheClinicianTypedIdentifyingDataIntoIt() {
        // detail is the field a clinician types prose into, so it is the field that carries a name, a
        // phone number or an address into a record whose entire contract is that identifying data
        // stays in VITO. A patient whose examination note says who brought them and where they live
        // has, in effect, been re-identified inside the shared record. Enforced by construction: the
        // key is never written, so no future producer change can leak it downstream.
        Map<String, Object> payload = ExaminationService.shrPayload(
                examination(),
                List.of(finding("RESPIRATORY", "ABNORMAL",
                        "coarse crackles; husband Tendai Moyo, 0772-123456, 12 Samora Machel Ave",
                        "R09.8")),
                "clin-shr-1");

        String json = json(payload);
        assertThat(json).doesNotContain("Tendai Moyo", "0772-123456", "Samora Machel");
        // context_note and site are prose for the same reason and are absent for the same reason.
        assertThat(json).doesNotContain("Rudo Moyo", "plantar first metatarsal head");
        assertThat(json).doesNotContain("detail", "contextNote", "site");
        // What DOES cross: the closed-vocabulary region and state, and the coded finding.
        assertThat(json).contains("RESPIRATORY", "ABNORMAL", "R09.8", "cpid-exam-shr");
    }

    @Test
    void allSixStatesCrossVerbatimSoNobodyLookedNeverBecomesNothingWrong() {
        // The single property the examination framework exists to hold. If the producer collapsed the
        // three not-examined states into the shape it uses for NORMAL, the clinician at the next
        // facility would read "abdomen normal" for an abdomen nobody has ever palpated — and would not
        // examine it. The states travel verbatim so the consumer can keep them distinct.
        List<ExaminationFindingEntity> findings = new ArrayList<>();
        for (String state : List.of("NORMAL", "ABNORMAL", "UNCERTAIN",
                "NOT_EXAMINED", "UNABLE_TO_EXAMINE", "DEFERRED")) {
            findings.add(finding("ABDOMEN", state, "detail for " + state, null));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>)
                ExaminationService.shrPayload(examination(), findings, "clin-shr-1").get("findings");

        assertThat(rows).extracting(r -> r.get("state"))
                .containsExactly("NORMAL", "ABNORMAL", "UNCERTAIN",
                        "NOT_EXAMINED", "UNABLE_TO_EXAMINE", "DEFERRED");
        // Six inputs, six distinct outputs: nothing has been folded together on the way out.
        assertThat(rows).extracting(r -> r.get("state")).doesNotHaveDuplicates();
    }

    @Test
    void theAnchorAndTheSubjectTravelSoTheImpressionCanBePlacedInTheJourney() {
        Map<String, Object> payload = ExaminationService.shrPayload(
                examination(), List.of(finding("ABDOMEN", "NORMAL", null, null)), "clin-shr-1");

        assertThat(payload).containsEntry("subjectCpid", "cpid-exam-shr")
                .containsEntry("journeyId", JOURNEY)
                .containsEntry("examinationId", "11111111-1111-4111-8111-111111111111");
    }

    // --- the emit actually happens, and is actually routed --------------------------------------

    @Test
    void recordingAnExaminationWritesAnOutboxRowCarryingTheUnexaminedRegion() {
        // A registry entry is not evidence of execution: this asserts the write actually runs. The
        // event type it writes is asserted to be routed away from the catch-all by
        // OutboxPublisherRouteTest, which lives in the package where routeTopic is visible.
        long before = countExaminationEvents();

        service.record(body(List.of(
                findingMap("ABDOMEN", "NORMAL", null),
                findingMap("NEUROLOGY", "NOT_EXAMINED", null))));

        assertThat(countExaminationEvents()).isEqualTo(before + 1);

        EventOutboxEntity row = latestExaminationEvent();
        assertThat(row.getAggregateType()).isEqualTo("EXAMINATION");
        assertThat(row.getEventType()).isEqualTo("EXAMINATION_RECORDED");
        assertThat(row.getTenantId()).isEqualTo(TENANT);
        assertThat(row.getPayload()).contains("NOT_EXAMINED").contains("NEUROLOGY");
    }

    @Test
    void aReplayedOfflineExaminationDoesNotEmitASecondEvent() {
        // Idempotency has to hold at the event boundary too, not only in the table. A second event for
        // the same examination would archive a second ClinicalImpression, and two impressions of one
        // examination is a record that cannot be counted.
        Map<String, Object> b = body(List.of(findingMap("ABDOMEN", "NORMAL", null)));
        b.put("client_offline_id", "offline-exam-shr-1");

        long before = countExaminationEvents();
        service.record(b);
        service.record(b);

        assertThat(countExaminationEvents()).isEqualTo(before + 1);
    }

    // --- helpers ---------------------------------------------------------------------------------

    private long countExaminationEvents() {
        return outboxRepository.findAll().stream()
                .filter(e -> "EXAMINATION_RECORDED".equals(e.getEventType()))
                .filter(e -> TENANT.equals(e.getTenantId()))
                .count();
    }

    private EventOutboxEntity latestExaminationEvent() {
        List<EventOutboxEntity> rows = outboxRepository.findAll().stream()
                .filter(e -> "EXAMINATION_RECORDED".equals(e.getEventType()))
                .filter(e -> TENANT.equals(e.getTenantId()))
                .toList();
        return rows.get(rows.size() - 1);
    }

    private static Map<String, Object> findingMap(String region, String state, String detail) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("region", region);
        f.put("state", state);
        if (detail != null) {
            f.put("detail", detail);
        }
        return f;
    }

    private static Map<String, Object> body(List<Map<String, Object>> findings) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("subject_cpid", "cpid-exam-shr");
        b.put("journey_id", JOURNEY);
        b.put("findings", new ArrayList<>(findings));
        return b;
    }

    private static String json(Object payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
