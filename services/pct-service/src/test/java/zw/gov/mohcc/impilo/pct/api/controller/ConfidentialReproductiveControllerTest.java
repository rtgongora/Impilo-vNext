package zw.gov.mohcc.impilo.pct.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The routing and disclosure properties of the confidential reproductive lane.
 *
 * <p>These are structural assertions rather than behavioural ones on purpose. The behaviour is
 * already proven in {@code ConfidentialRecordGuardTest}; what can still silently break here is the
 * PATH — and a wrong path produces no error, no failing test and no log line. It produces a route
 * that, after the governance flip, withholds every stamped record from every requester including its
 * author, because the PDP never classifies it into the confidential lane at all.
 */
class ConfidentialReproductiveControllerTest {

    /** Must match ResourceSensitivityClassifier.PROTECTED_LANE_MARKERS. */
    private static final String[] LANE_MARKERS = {"confidential", "safeguarding", "protected-disclosure"};

    @Test
    @DisplayName("the controller is mounted on a confidential lane the PDP will actually classify")
    void mountedOnAConfidentialLane() {
        RequestMapping mapping = ConfidentialReproductiveController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).as("the controller must declare a class-level path").isNotNull();
        assertThat(mapping.value()).isNotEmpty();

        String path = mapping.value()[0].toLowerCase();
        assertThat(Arrays.stream(LANE_MARKERS).anyMatch(path::contains))
                .as("path '%s' carries no lane marker %s — tshepo-authz classifies confidentiality "
                        + "from the path, so this route would receive no confidentialCategories and the "
                        + "fail-closed guard would withhold everything after the flip",
                        path, Arrays.toString(LANE_MARKERS))
                .isTrue();
    }

    @Test
    @DisplayName("no endpoint on this lane can answer 403 — a withheld record must read as absent")
    void noEndpointDeclaresAForbiddenPath() {
        for (Method m : ConfidentialReproductiveController.class.getDeclaredMethods()) {
            assertThat(m.getExceptionTypes())
                    .as("%s must not declare a thrown exception: a 403 distinguishable from a 404 tells "
                            + "the guardian the confidential record IS there", m.getName())
                    .isEmpty();
        }
        // And nothing in the source reaches for a status that would leak existence.
        String src = read("src/main/java/zw/gov/mohcc/impilo/pct/api/controller/"
                + "ConfidentialReproductiveController.java");
        String code = src.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
        assertThat(code)
                .as("this lane must not construct a FORBIDDEN response anywhere")
                .doesNotContain("FORBIDDEN", "SC_FORBIDDEN", "status(403)");
    }

    @Test
    @DisplayName("the postnatal view passes danger_signs_present through, including null")
    void dangerSignsNullSurvivesTheReadPath() {
        String code = read("src/main/java/zw/gov/mohcc/impilo/pct/api/controller/"
                + "ConfidentialReproductiveController.java");
        // V436's chk_pnc_screening_gate keeps this null unless a screen was completed. Coercing it in
        // the read path would undo the schema guarantee and manufacture a false all-clear.
        assertThat(code)
                .as("danger_signs_present must be passed through, never defaulted in the view")
                .contains("m.put(\"danger_signs_present\", e.getDangerSignsPresent());");
        assertThat(code).doesNotContain("getDangerSignsPresent() == null ?");
        assertThat(code).doesNotContain("Boolean.TRUE.equals(e.getDangerSignsPresent())");
    }

    @Test
    @DisplayName("every one of the six stamped record types has a route on this lane")
    void allSixStampedRecordTypesAreReachable() {
        // pct V437 stamped six tables. A stamped type with no guarded route is a route the first
        // reader who needs it writes unguarded, on an ordinary path — which is the failure
        // check-confidential-lane-routing.sh exists to catch, arriving through the one door the guard
        // cannot watch: code that does not exist yet.
        String src = source();
        for (String service : new String[]{
                "losses.forMother",
                "postnatalContacts.forMother",
                "terminations.proceduresForSubject",
                "terminations.authorisationsForSubject",
                "contraception.coverage",
                "contraception.history",
                "pregnancies.history",
                "pregnancies.currentPregnancy"}) {
            assertThat(src)
                    .as("%s must be served from this lane", service)
                    .contains(service);
        }
    }

    @Test
    @DisplayName("the pregnancy routes never reach the unguarded pregnant() or gestationalAgeDays()")
    void theUnguardedAccessorsBackNoEndpoint() {
        // PregnancyEpisodeService.pregnant() and gestationalAgeDays() are unguarded by a recorded
        // decision (stamping doc §8) because clinical decision support calls them with no request
        // context. They answer about a record; they must never return one. An endpoint backed by
        // either would turn a bounded, owned leak into a disclosure API.
        String code = codeWithoutComments();
        assertThat(code).doesNotContain("pregnancies.pregnant(");
        assertThat(code).doesNotContain("pregnancies.gestationalAgeDays(");
    }

    @Test
    @DisplayName("no view computes a gestational age — dating is stamped once and read everywhere")
    void noViewRederivesDating() {
        // Two components that can each compute a gestational age will eventually disagree about how
        // pregnant a woman is, and the disagreement surfaces as a chart contradicting an alert.
        assertThat(codeWithoutComments()).doesNotContain("EddCalculator", "gestationalAgeDays");
    }

    @Test
    @DisplayName("coverage_unknown_why is carried, so UNKNOWN never renders as a shrug")
    void coverageUnknownReasonSurvivesTheReadPath() {
        // UNKNOWN with no reason gets ignored, and the one case that matters — a woman months past
        // her injection — then looks exactly like a data-entry gap.
        assertThat(source()).contains("m.put(\"coverage_unknown_why\", c.coverageUnknownWhy());");
    }

    @Test
    @DisplayName("the current-pregnancy route answers 200 with an empty body, never 404")
    void absentCurrentPregnancyIsNotA404() {
        // The two cases this lane must not distinguish are "she is not pregnant" and "she is pregnant
        // and you may not know". A 404 on one of them names the other.
        String code = codeWithoutComments();
        assertThat(code).doesNotContain("notFound()", "NOT_FOUND", "status(404)");
        assertThat(code)
                .as("an absent episode must map to a null body inside a 200")
                .contains(".orElse(null)");
    }

    private static String source() {
        return read("src/main/java/zw/gov/mohcc/impilo/pct/api/controller/"
                + "ConfidentialReproductiveController.java");
    }

    private static String codeWithoutComments() {
        return source().replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static String read(String relativePath) {
        try {
            return Files.readString(Path.of(relativePath));
        } catch (Exception e) {
            throw new AssertionError("could not read " + relativePath, e);
        }
    }
}
