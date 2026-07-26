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
     *
     * <p>Re-pinned 55 → 54: the D7 fix removed the raw-Health-ID emission in
     * {@code WalletOverviewService.identityAndTrust} (the wallet now surfaces the patient-facing
     * Impilo ID, not {@code healthId}). The ratchet only moves down.</p>
     *
     * <p>Re-pinned 54 → 56 (2026-07-20): the +2 sites are <b>server-side reads, not browser
     * emissions</b> — {@code ActorContextFilter} reads the {@code health_id} JWT claim to force
     * {@code X-Actor-ID} to the token-proven identity (PII Wave 0.3 stage 1a, commit 6f2ed276f:
     * the hardening itself), and {@code AuthSessionController} reads {@code healthId} off the
     * TSHEPO identity-mapping S2S response during flag-gated biometric login (L3, commit
     * 956f4c367); neither literal reaches a browser payload. Consistent with the CJ8/CJ14 re-pin,
     * which already counts internal contract field names.</p>
     *
     * <p>Re-pinned 56 → 58 (2026-07-26, staffing name-resolution slice): the +2 sites are
     * <b>internal S2S response reads, not browser emissions</b> — {@code StaffIdentityResolver}
     * reads {@code "health_id"} off vashandi's profile→anchor mapping and {@code "healthId"} off
     * varapi's display-facts response, using each only to compose the request to the next service
     * and to join in memory. The health id is never written onto the staffing response the browser
     * receives, which carries {@code display_name}/{@code phone}/{@code profession} and the
     * workforce profile id only. Same category as the CJ8/CJ14 and PII-Wave re-pins above.</p>
     */
    private static final int BASELINE = 58;

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
