package zw.gov.mohcc.impilo.tshepo.authz.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.fail;

/**
 * Reads the TypeScript union types that clients use to build trust headers.
 *
 * <p>These files are the checked-in statement of what an actor can put on the wire. Anchoring the
 * seed guards to them — rather than to a grep over the estate — is deliberate: a heuristic scan of
 * every string literal that might be an actor type produces noise
 * ({@code FACILITY_FINANCE}, {@code USER}, {@code STAFF}, {@code PATIENT} all turn up), and a guard
 * built on noise either cries wolf or gets muted. A declared vocabulary that is itself guarded
 * cannot drift silently.
 */
final class ContractMirrors {

    private ContractMirrors() {}

    /** Every TypeScript file that mirrors a trust-header vocabulary, repo-relative. */
    static final List<String> MIRROR_PATHS = List.of(
            "contracts/health-os-identifiers.ts",
            "ui/shared-ui/lib/contracts.ts",
            "apps/mobile/packages/mobile-trust/src/types.ts");

    /**
     * Actor types that only a service ever emits, so no browser/mobile mirror declares them.
     * Service-originated calls set these directly in Java (see the write-back gateways), which is
     * why they are legitimate in a seed despite being absent from every client union.
     */
    static final Set<String> SERVICE_SIDE_ACTOR_TYPES = Set.of("SYSTEM", "SERVICE");

    private static final Pattern MEMBER = Pattern.compile("\"([A-Z_]+)\"");

    /** Members of {@code export type <typeName> = ...;} per mirror, in declaration order. */
    static Map<String, Set<String>> membersByMirror(String typeName) {
        Pattern union = Pattern.compile(
                "export\\s+type\\s+" + Pattern.quote(typeName) + "\\s*=\\s*([^;]+);");

        Path root = repoRoot();
        Map<String, Set<String>> byMirror = new LinkedHashMap<>();

        for (String mirror : MIRROR_PATHS) {
            Path file = root.resolve(mirror);
            if (!Files.isRegularFile(file)) {
                fail("Mirror %s not found at %s. If it moved, update ContractMirrors.MIRROR_PATHS;"
                        + " if it was deleted, remove it. Do not let the guard go blind.",
                        mirror, file);
            }
            Matcher m = union.matcher(read(file));
            if (!m.find()) {
                // Not every mirror has to declare every type; absence is reported by the caller.
                continue;
            }
            Set<String> members = new LinkedHashSet<>();
            Matcher member = MEMBER.matcher(m.group(1));
            while (member.find()) {
                members.add(member.group(1));
            }
            byMirror.put(mirror, members);
        }

        if (byMirror.isEmpty()) {
            fail("No mirror declares `export type %s`; the guard cannot verify it against anything.",
                    typeName);
        }
        return byMirror;
    }

    /** Every value any client mirror can put on the wire for {@code typeName}. */
    static Set<String> declaredByAnyClient(String typeName) {
        Set<String> union = new LinkedHashSet<>();
        membersByMirror(typeName).values().forEach(union::addAll);
        return union;
    }

    /** Walks up from the module directory to the repo root (the one holding contracts/). */
    static Path repoRoot() {
        Path dir = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6 && dir != null; i++) {
            if (Files.isDirectory(dir.resolve("contracts")) && Files.isDirectory(dir.resolve("services"))) {
                return dir;
            }
            dir = dir.getParent();
        }
        return fail("Cannot locate the repo root from " + Paths.get("").toAbsolutePath()
                + " — the contract guards would silently pass without checking anything.");
    }

    static String read(Path p) {
        try {
            return Files.readString(p, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
