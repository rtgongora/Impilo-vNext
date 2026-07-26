package zw.gov.mohcc.impilo.tshepo.authz.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tshepo.contracts.enums.PurposeOfUse;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Guard: every TypeScript mirror of {@code PurposeOfUse} must match the Java enum exactly.
 *
 * <p>The Java enum is the authority — {@code PolicyEngine.parsePurpose} rejects anything outside
 * it with {@code INVALID_PURPOSE} at Step 2, before any rule is consulted. So drift in either
 * direction is a silent defect:
 *
 * <ul>
 *   <li>A code in TypeScript but not in Java is a request a client can construct and the PDP
 *       will always deny. That is how the nhume write-back gateway came to send
 *       {@code X-Purpose-Of-Use: LOGISTICS} and be denied on every call.</li>
 *   <li>A code in Java but not in TypeScript is surface area no client can reach.</li>
 * </ul>
 *
 * <p>Before this guard the three mirrors had drifted apart in both directions at once, which is
 * what made the dead {@code CARE_COORDINATION} policy seeds survive review: the type that should
 * have contradicted them had been widened with an inline
 * {@code PurposeOfUse | "CARE_COORDINATION"} escape hatch instead.
 *
 * <p>This lives in the Java build on purpose. The mirrors sit in three different npm packages
 * with their own test runners; a check in any one of them would not see the others, and a check
 * in none of them is what got us here.
 */
@DisplayName("TypeScript PurposeOfUse mirrors match the Java enum")
class PurposeOfUseContractMirrorTest {

    /** Repo-relative paths of every TypeScript declaration of the PurposeOfUse union. */
    private static final List<String> MIRRORS = List.of(
            "contracts/health-os-identifiers.ts",
            "ui/shared-ui/lib/contracts.ts",
            "apps/mobile/packages/mobile-trust/src/types.ts");

    private static final Pattern UNION = Pattern.compile(
            "export\\s+type\\s+PurposeOfUse\\s*=\\s*([^;]+);");

    private static final Pattern MEMBER = Pattern.compile("\"([A-Z_]+)\"");

    @Test
    void everyTypeScriptMirrorMatchesTheJavaEnum() {
        Set<String> javaCodes = Arrays.stream(PurposeOfUse.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Path repoRoot = locateRepoRoot();
        List<String> problems = new ArrayList<>();

        for (String mirror : MIRRORS) {
            Path file = repoRoot.resolve(mirror);
            if (!Files.isRegularFile(file)) {
                problems.add(mirror + " — not found at " + file
                        + ". If the mirror moved, update MIRRORS in this test; if it was deleted,"
                        + " remove it from the list. Do not let the guard go blind.");
                continue;
            }

            String source = read(file);
            Matcher union = UNION.matcher(source);
            if (!union.find()) {
                problems.add(mirror + " — no `export type PurposeOfUse = ...;` declaration found;"
                        + " the guard cannot verify this mirror");
                continue;
            }

            Set<String> tsCodes = new LinkedHashSet<>();
            Matcher member = MEMBER.matcher(union.group(1));
            while (member.find()) {
                tsCodes.add(member.group(1));
            }

            Set<String> missingInTs = new LinkedHashSet<>(javaCodes);
            missingInTs.removeAll(tsCodes);
            Set<String> extraInTs = new LinkedHashSet<>(tsCodes);
            extraInTs.removeAll(javaCodes);

            if (!missingInTs.isEmpty()) {
                problems.add(mirror + " — missing " + missingInTs
                        + "; these Java codes are unreachable from this client");
            }
            if (!extraInTs.isEmpty()) {
                problems.add(mirror + " — declares " + extraInTs
                        + " which the Java enum does not have; requests carrying these are denied"
                        + " INVALID_PURPOSE before any rule is evaluated");
            }
        }

        if (!problems.isEmpty()) {
            fail("TypeScript PurposeOfUse mirrors have drifted from %s:%n%n%s%n%n"
                    + "The Java enum is the authority. Either add the code there (and give it a"
                    + " visibility envelope in VisibilityObligationComposer.purposeDefaults), or"
                    + " drop it from the TypeScript union.",
                    PurposeOfUse.class.getName(), String.join("\n", problems));
        }
    }

    /** Walks up from the module directory to the repo root (the one holding contracts/). */
    private static Path locateRepoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("contracts")) && Files.isDirectory(dir.resolve("services"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return fail("Cannot locate the repo root from " + Paths.get("").toAbsolutePath()
                + " — the mirror guard would silently pass without checking anything.");
    }

    private static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theMirrorListItselfIsNotEmpty() {
        assertThat(MIRRORS).as("TypeScript mirrors under guard").isNotEmpty();
    }
}
