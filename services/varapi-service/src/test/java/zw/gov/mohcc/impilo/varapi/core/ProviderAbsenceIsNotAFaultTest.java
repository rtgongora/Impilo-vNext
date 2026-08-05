package zw.gov.mohcc.impilo.varapi.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "This Health ID carries no Provider ID" is one of VARAPI's correct answers, not a failure.
 *
 * <p>It used to be thrown as {@code IllegalArgumentException} and surfaced as HTTP 500. That made
 * an absence indistinguishable from an outage, and the consequence only appears once something
 * enforces on the answer: measured across the 20 accounts in the preview realm carrying a
 * clinician, nurse, facility-admin or regulator role label, <strong>every single one returned
 * 500</strong>. A consumer failing closed on "unavailable" would tell twenty people to try again
 * later, forever, for a condition that never resolves by itself.</p>
 *
 * <p>These are source-level assertions because the behaviour lives in a Spring MVC path this module
 * has no slice test for. They are deliberately written against the seam rather than the wording, so
 * they fail on a regression rather than on a rename.</p>
 */
class ProviderAbsenceIsNotAFaultTest {

    private static final Path SERVICE = Path.of(
            "src/main/java/zw/gov/mohcc/impilo/varapi/core/ProviderService.java");
    private static final Path CONTROLLER = Path.of(
            "src/main/java/zw/gov/mohcc/impilo/varapi/api/controller/ProviderByHealthIdController.java");

    @Test
    @DisplayName("an Optional-returning lookup exists, so absence can be expressed without throwing")
    void absenceCanBeExpressed() throws Exception {
        assertThat(Files.isRegularFile(SERVICE))
                .as("ProviderService not found at %s — this assertion would pass vacuously",
                        SERVICE.toAbsolutePath())
                .isTrue();
        String src = Files.readString(SERVICE);

        assertThat(src)
                .as("a lookup that can only throw cannot report an absence")
                .contains("Optional<ProviderDetail> findProviderByImpiloHealthId");
    }

    @Test
    @DisplayName("the by-health-id endpoint answers 404 for an absent provider, not 500")
    void endpointReturns404ForAbsence() throws Exception {
        assertThat(Files.isRegularFile(CONTROLLER))
                .as("controller not found at %s", CONTROLLER.toAbsolutePath()).isTrue();
        String src = Files.readString(CONTROLLER);

        int method = src.indexOf("getByHealthId(");
        assertThat(method).as("getByHealthId is gone or renamed").isGreaterThan(-1);
        String body = src.substring(method, src.indexOf("@GetMapping", method + 1));

        assertThat(body)
                .as("the endpoint must resolve through the Optional form, or absence throws again")
                .contains("findProviderByImpiloHealthId");
        assertThat(body)
                .as("absence must be answered as NOT_FOUND — a 500 makes it indistinguishable "
                        + "from the registry being down")
                .contains("HttpStatus.NOT_FOUND");
    }

    @Test
    @DisplayName("a MALFORMED id is still a fault — it is not silently reported as 'no provider'")
    void malformedIdIsNotAnAbsence() throws Exception {
        // The tempting over-correction: catch everything and return empty. That would tell a caller
        // the person holds no Provider ID when in truth the identifier could not be parsed — a
        // claim about a human derived from a formatting error.
        String src = Files.readString(SERVICE);
        // Anchor on the DEFINITION, not the name: the identifier also appears in the throwing
        // form's delegation above it, and slicing from the first hit measured the wrong method.
        int m = src.indexOf("Optional<ProviderDetail> findProviderByImpiloHealthId");
        assertThat(m).as("the Optional lookup is gone or renamed").isGreaterThan(-1);
        String body = src.substring(m, src.indexOf("\n    }", m));

        assertThat(body)
                .as("a malformed Health ID must not be flattened into an absence")
                .contains("Malformed Impilo Health ID");
    }

    @Test
    @DisplayName("the throwing form is retained and delegates — one lookup, one definition of absence")
    void throwingFormDelegates() throws Exception {
        String src = Files.readString(SERVICE);
        int m = src.indexOf("public ProviderDetail getProviderByImpiloHealthId");
        assertThat(m).as("the throwing form was removed — callers that require a provider need it")
                .isGreaterThan(-1);
        String body = src.substring(m, src.indexOf("\n    }", m));

        assertThat(body)
                .as("two independent lookups would drift on what 'absent' means")
                .contains("findProviderByImpiloHealthId");
    }
}
