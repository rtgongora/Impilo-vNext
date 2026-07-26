package zw.gov.mohcc.impilo.tshepo.authz.migration;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The set of URL path segments the estate can actually produce.
 *
 * <p>Exists to make {@code policy_rule.resource_type} checkable. The PDP does not receive a
 * resource type — it derives one, in {@code AuthzInternalRequest.deriveResourceType}, as the last
 * path segment of the request that is not a UUID and not {@code v1}/{@code api}. So a resource type
 * is reachable if and only if it is a path segment somewhere. A seeded value that appears in no
 * path in the entire codebase can never be produced, and the rule is dead on arrival — which is
 * exactly how V017 seeded {@code 'khuluma-conversation'} for routes that yield
 * {@code 'conversations'}.
 *
 * <p><b>Why string literals rather than route annotations.</b> Scanning only
 * {@code @RequestMapping}/{@code @GetMapping} misses paths the estate builds by hand: experience-bff
 * synthesises {@code /internal/v1/public-health-governed-read} and friends purely to obtain an
 * ext_authz verdict, and those are legitimate resource types with no Spring route behind them.
 * Taking every string literal that starts with {@code /} covers both, and it errs toward
 * permissiveness — the failure mode is a missed defect, never a false accusation.
 *
 * <p>The leading slash matters. It is what separates a path from an identifier that merely looks
 * like one: khuluma-service builds consent references as {@code "khuluma-call:" + callId}, and
 * without the slash rule that literal would have made {@code khuluma-call} look reachable and hidden
 * a real dead rule.
 */
final class ReachablePathSegments {

    private ReachablePathSegments() {}

    /** Roots scanned for path literals, relative to the repo root. */
    private static final List<String> ROOTS = List.of("services", "ui", "apps", "libs", "contracts");

    private static final Set<String> SKIP_DIRECTORIES =
            Set.of("node_modules", "target", "build", ".next", "dist", ".git", "coverage");

    private static final Set<String> SOURCE_SUFFIXES = Set.of(".java", ".ts", ".tsx");

    /**
     * Segments that {@code deriveResourceType} skips outright, so they can never be a resource
     * type no matter how many paths contain them.
     */
    private static final Set<String> NEVER_A_RESOURCE_TYPE = Set.of("v1", "v2", "api");

    /** A double-quoted literal beginning with {@code /} — i.e. a URL path, not an identifier. */
    private static final Pattern PATH_LITERAL = Pattern.compile("\"(/[A-Za-z0-9_\\-./{}]*)\"");

    private static Set<String> cached;

    /** Every path segment declared anywhere in the source tree. Computed once per JVM. */
    static synchronized Set<String> all() {
        if (cached == null) {
            cached = scan();
        }
        return cached;
    }

    private static Set<String> scan() {
        Path repoRoot = ContractMirrors.repoRoot();
        Set<String> segments = new LinkedHashSet<>();

        for (String root : ROOTS) {
            Path dir = repoRoot.resolve(root);
            if (!Files.isDirectory(dir)) {
                continue;
            }
            try {
                Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path d, BasicFileAttributes attrs) {
                        return SKIP_DIRECTORIES.contains(d.getFileName().toString())
                                ? FileVisitResult.SKIP_SUBTREE
                                : FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        if (SOURCE_SUFFIXES.stream().anyMatch(name::endsWith)) {
                            collectInto(segments, file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return segments;
    }

    private static void collectInto(Set<String> segments, Path file) {
        String source;
        try {
            source = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return; // unreadable or non-UTF8: contributes nothing, never a false accusation
        }
        Matcher m = PATH_LITERAL.matcher(source);
        while (m.find()) {
            for (String segment : m.group(1).split("/")) {
                String s = segment.trim();
                if (s.isEmpty() || s.startsWith("{") || NEVER_A_RESOURCE_TYPE.contains(s)) {
                    continue;
                }
                segments.add(s);
            }
        }
    }
}
