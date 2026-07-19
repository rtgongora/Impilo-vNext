package zw.gov.mohcc.impilo.experience.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D7 residual-leak ratchet: the BFF still names raw Health-ID fields in
 * browser-bound emission sites (the measured backlog behind
 * {@link IdentifierExposureShapingAdvice}). This test pins the count so the
 * backlog can only shrink — a new {@code "healthId"} literal in main sources
 * fails the build until it is either removed or the baseline is consciously
 * re-pinned with justification.
 *
 * <p>The full inventory is written to {@code target/d7-leak-inventory.txt}
 * per run so the residual sweep works from a current list, not folklore.</p>
 */
class IdentifierLeakInventoryTest {

    /**
     * Pinned 2026-07-19 (Identity Journey Program, D7 staff-surface slice).
     *
     * <p>Re-pinned 53 → 55 (CJ8/CJ14): the +2 sites are <b>internal VITO-contract field names</b>,
     * not browser emissions — {@code VitoServiceClient} builds {@code /v1/cards/request} and
     * {@code /identity/register} bodies whose field VITO <i>requires</i> to be {@code "healthId"},
     * and {@code CitizenDependantController} reads {@code "healthId"} back off VITO's register
     * response (it is used internally as the mvumo delegation subject; the browser response names
     * it {@code dependantSubjectRef}, disclosed only to the just-authorised guardian). The literal
     * is dictated by VITO's API and cannot be renamed; consistent with the baseline, which already
     * counts internal service-to-service field names (e.g. {@code WorkforceIntakeService}).</p>
     */
    private static final int BASELINE = 55;

    private static final Pattern LEAK = Pattern.compile("\"(healthId|impiloHealthId|health_id)\"");

    @Test
    @DisplayName("raw Health-ID emission sites in BFF sources never grow beyond the pinned baseline")
    void leakSitesDoNotGrow() throws IOException {
        Path srcRoot = Path.of("src/main/java");
        List<String> hits = new ArrayList<>();
        try (Stream<Path> files = Files.walk(srcRoot)) {
            files.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
                try {
                    List<String> lines = Files.readAllLines(p);
                    for (int i = 0; i < lines.size(); i++) {
                        if (LEAK.matcher(lines.get(i)).find()) {
                            hits.add(p + ":" + (i + 1) + "  " + lines.get(i).trim());
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        Path report = Path.of("target/d7-leak-inventory.txt");
        Files.createDirectories(report.getParent());
        Files.write(report, hits);

        assertTrue(hits.size() <= BASELINE,
                "D7 ratchet: raw Health-ID emission sites grew from " + BASELINE + " to " + hits.size()
                        + " — do not add new browser-bound healthId fields (see target/d7-leak-inventory.txt)");
    }
}
