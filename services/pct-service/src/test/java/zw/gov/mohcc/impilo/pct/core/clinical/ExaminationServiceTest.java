package zw.gov.mohcc.impilo.pct.core.clinical;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.pct.persistence.entity.ExaminationEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The examination framework's read and write behaviour (brief.md §7).
 *
 * <p>The property under test throughout: <strong>a region nobody examined must never be readable as
 * one that was examined and found normal.</strong> The database holds the vocabulary; these cover
 * what a CHECK cannot see — coherence before the write, and coverage on the read.</p>
 *
 * <p>Uses purpose EMERGENCY so {@code ClinicalAccessGuard} waives the care-relationship lookup: the
 * subject of these tests is the examination logic, and a journey-resolution failure would fail them
 * for an unrelated reason. The anchor requirement itself is still asserted below.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExaminationServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000031");
    private static final String JOURNEY = "77777777-7777-4777-8777-777777777777";

    @Autowired
    private ExaminationService service;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "clin-1", "PRACTITIONER", "EMERGENCY",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clearTrustContext() {
        TrustContextHolder.clear();
    }

    private static Map<String, Object> finding(String region, String state, String detail) {
        Map<String, Object> f = new LinkedHashMap<>();
        f.put("region", region);
        f.put("state", state);
        if (detail != null) {
            f.put("detail", detail);
        }
        return f;
    }

    private Map<String, Object> body(List<Map<String, Object>> findings) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("subject_cpid", "cpid-exam-1");
        b.put("journey_id", JOURNEY);
        b.put("findings", new ArrayList<>(findings));
        return b;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> coverageOf(ExaminationEntity e) {
        return (Map<String, Object>) service.get(e.getExaminationId()).get("coverage");
    }

    @Test
    void anExaminationRecordsItsRegionsAndReportsWhichWereActuallyExamined() {
        ExaminationEntity e = service.record(body(List.of(
                finding("ABDOMEN", "NORMAL", null),
                finding("RESPIRATORY", "ABNORMAL", "coarse crackles at the left base"),
                finding("NEUROLOGY", "NOT_EXAMINED", null))));

        Map<String, Object> coverage = coverageOf(e);
        assertThat((List<String>) coverage.get("examined")).containsExactlyInAnyOrder("ABDOMEN", "RESPIRATORY");
        assertThat((List<String>) coverage.get("recorded_not_examined")).containsExactly("NEUROLOGY");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theRegionsNobodyConsideredAreNamed() {
        // The read-side half of the framework's purpose. A reader scanning findings sees what was
        // looked at; absence has no row, so what was NOT looked at is invisible unless it is named.
        ExaminationEntity e = service.record(body(List.of(finding("ABDOMEN", "NORMAL", null))));

        Map<String, Object> coverage = coverageOf(e);
        List<String> never = (List<String>) coverage.get("never_considered");
        assertThat(never).hasSize(ExaminationVocabulary.REGIONS.size() - 1);
        assertThat(never).contains("CARDIOVASCULAR", "NEUROLOGY", "SKIN");
        assertThat((String) coverage.get("note")).contains("never considered")
                .contains("Neither of those two groups is a normal finding");
    }

    @Test
    @SuppressWarnings("unchecked")
    void anUnexaminedRegionIsNeverReportedAsExamined() {
        // The single assertion the framework exists for.
        ExaminationEntity e = service.record(body(List.of(
                finding("ABDOMEN", "NOT_EXAMINED", null),
                finding("NEUROLOGY", "UNABLE_TO_EXAMINE", "patient unconscious"),
                finding("SKIN", "DEFERRED", "no chaperone available; to be done on the ward round"))));

        List<Map<String, Object>> findings =
                (List<Map<String, Object>>) service.get(e.getExaminationId()).get("findings");
        assertThat(findings).allSatisfy(f -> assertThat(f.get("was_examined")).isEqualTo(false));
        assertThat((List<String>) coverageOf(e).get("examined")).isEmpty();
    }

    @Test
    void uncertainCountsAsExaminedBecauseTheExaminerDidLook() {
        ExaminationEntity e = service.record(body(List.of(
                finding("ABDOMEN", "UNCERTAIN", "possible mass, could not distinguish from bowel"))));

        assertThat((List<String>) coverageOf(e).get("examined")).containsExactly("ABDOMEN");
    }

    @Test
    void abnormalWithoutDetailIsRefusedWithAReasonAClinicianCanActestOn() {
        assertThatThrownBy(() -> service.record(body(List.of(finding("ABDOMEN", "ABNORMAL", null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must say what was found");
    }

    @Test
    void unableToExamineWithoutAReasonIsRefused() {
        // The next examiner needs to know what has to change before the examination can happen.
        assertThatThrownBy(() -> service.record(body(List.of(finding("NEUROLOGY", "UNABLE_TO_EXAMINE", null)))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must say why not");
    }

    @Test
    void normalAndNotExaminedStandAloneWithNoDetail() {
        ExaminationEntity e = service.record(body(List.of(
                finding("ABDOMEN", "NORMAL", null),
                finding("SKIN", "NOT_EXAMINED", null))));
        assertThat(e.getExaminationId()).isNotNull();
    }

    @Test
    void anUnknownRegionOrStateIsRefusedNamingTheAllowedSet() {
        assertThatThrownBy(() -> service.record(body(List.of(finding("SPLEEN", "NORMAL", null)))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.record(body(List.of(finding("ABDOMEN", "PROBABLY_FINE", null)))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSameRegionTwiceInOneExaminationIsRefused() {
        // The second row would silently win any read that takes the latest.
        assertThatThrownBy(() -> service.record(body(List.of(
                finding("ABDOMEN", "NORMAL", null),
                finding("ABDOMEN", "ABNORMAL", "tender")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recorded twice");
    }

    @Test
    void anExaminationWithNoAnchorIsRefusedNamingTheDoctrine() {
        Map<String, Object> b = body(List.of(finding("ABDOMEN", "NORMAL", null)));
        b.remove("journey_id");
        assertThatThrownBy(() -> service.record(b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("journey_id or an encounter_id");
    }

    @Test
    void anExaminationWithNoFindingsAtAllIsRefused() {
        // An examination with nothing recorded is a timestamp, and it would appear in the timeline
        // implying a look nobody took.
        assertThatThrownBy(() -> service.record(body(List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one region");
    }

    @Test
    void aSiteWithoutAGraphicIsRefused() {
        Map<String, Object> f = finding("FEET", "ABNORMAL", "ulcer");
        f.put("site", "left heel");
        assertThatThrownBy(() -> service.record(body(List.of(f))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("coordinate with no map");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSitedFindingOnANamedGraphicIsKept() {
        Map<String, Object> f = finding("FEET", "ABNORMAL", "neuropathic ulcer");
        f.put("graphic", "DIABETIC_FOOT");
        f.put("site", "plantar first metatarsal head");
        f.put("laterality", "LEFT");
        ExaminationEntity e = service.record(body(List.of(f)));

        List<Map<String, Object>> findings =
                (List<Map<String, Object>>) service.get(e.getExaminationId()).get("findings");
        assertThat(findings.get(0)).containsEntry("graphic", "DIABETIC_FOOT")
                .containsEntry("laterality", "LEFT");
    }

    @Test
    void aReplayedOfflineExaminationDoesNotCreateASecondRecord() {
        Map<String, Object> b = body(List.of(finding("ABDOMEN", "NORMAL", null)));
        b.put("client_offline_id", "offline-exam-1");

        ExaminationEntity first = service.record(b);
        ExaminationEntity replay = service.record(b);

        assertThat(replay.getExaminationId()).isEqualTo(first.getExaminationId());
        assertThat(service.list("cpid-exam-1")).hasSize(1);
    }
}
