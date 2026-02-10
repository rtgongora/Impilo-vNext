package zw.gov.mohcc.impilo.vito.migration.v11.offline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JDK-only verification runner for v1.1 request-path enforcement.
 *
 * Zero external dependencies: uses only java.* packages.
 * Reads source files as text and runs strict string/regex assertions.
 *
 * Usage:
 *   javac OfflineVerificationRunner.java V11RequestPathOfflineVerificationTest.java
 *   java -cp . zw.gov.mohcc.impilo.vito.migration.v11.offline.OfflineVerificationRunner /path/to/services/vito-service
 *
 * Exit codes:
 *   0 = all checks passed
 *   1 = one or more checks failed
 */
public final class OfflineVerificationRunner {

    private final Path serviceRoot;
    private final List<Result> results = new ArrayList<>();
    private int passCount;
    private int failCount;

    public OfflineVerificationRunner(Path serviceRoot) {
        this.serviceRoot = serviceRoot;
    }

    // ---- Assertion helpers (JDK-only, no JUnit) ----

    /**
     * Read a source file relative to serviceRoot. Returns file contents.
     * Fails immediately if file does not exist.
     */
    public String readSource(String relativePath, String checkLabel) {
        Path file = serviceRoot.resolve(relativePath);
        if (!Files.isRegularFile(file)) {
            fail(checkLabel, "File not found: " + file);
            return null;
        }
        try {
            return Files.readString(file);
        } catch (IOException e) {
            fail(checkLabel, "Could not read file: " + file + " (" + e.getMessage() + ")");
            return null;
        }
    }

    /** Assert that the source contains the exact literal string. */
    public void assertContains(String source, String literal, String checkLabel) {
        if (source == null) return; // already failed at read time
        if (source.contains(literal)) {
            pass(checkLabel);
        } else {
            fail(checkLabel, "Literal not found: «" + literal + "»");
        }
    }

    /** Assert that the source matches the given regex. */
    public void assertMatches(String source, String regex, String checkLabel) {
        if (source == null) return;
        Pattern p = Pattern.compile(regex, Pattern.MULTILINE);
        Matcher m = p.matcher(source);
        if (m.find()) {
            pass(checkLabel);
        } else {
            fail(checkLabel, "Regex not matched: " + regex);
        }
    }

    /** Assert that multiple literals are ALL present. */
    public void assertContainsAll(String source, List<String> literals, String checkLabel) {
        if (source == null) return;
        List<String> missing = new ArrayList<>();
        for (String lit : literals) {
            if (!source.contains(lit)) {
                missing.add(lit);
            }
        }
        if (missing.isEmpty()) {
            pass(checkLabel);
        } else {
            fail(checkLabel, "Missing literals: " + missing);
        }
    }

    public void pass(String label) {
        passCount++;
        results.add(new Result(true, label, null));
        System.out.println("  PASS  " + label);
    }

    public void fail(String label, String reason) {
        failCount++;
        results.add(new Result(false, label, reason));
        System.out.println("  FAIL  " + label + " — " + reason);
    }

    public int getPassCount() { return passCount; }
    public int getFailCount() { return failCount; }
    public List<Result> getResults() { return List.copyOf(results); }

    /** Print summary and return exit code. */
    public int summarize() {
        System.out.println();
        System.out.println("=".repeat(72));
        System.out.println("  V1.1 Offline Verification Summary");
        System.out.println("  PASSED: " + passCount + "   FAILED: " + failCount);
        System.out.println("=".repeat(72));
        if (failCount > 0) {
            System.out.println();
            System.out.println("Failed checks:");
            for (Result r : results) {
                if (!r.passed) {
                    System.out.println("  [FAIL] " + r.label + " — " + r.reason);
                }
            }
        }
        System.out.println();
        if (failCount == 0) {
            System.out.println("RESULT: ALL CHECKS PASSED");
        } else {
            System.out.println("RESULT: " + failCount + " CHECK(S) FAILED");
        }
        return failCount == 0 ? 0 : 1;
    }

    public record Result(boolean passed, String label, String reason) {}

    // ---- Entry point ----

    public static void main(String[] args) {
        Path serviceRoot;
        if (args.length > 0) {
            serviceRoot = Path.of(args[0]);
        } else {
            // Default: assume CWD is the vito-service root
            serviceRoot = Path.of(".");
        }

        System.out.println("=".repeat(72));
        System.out.println("  V1.1 Request-Path Offline Verification");
        System.out.println("  Service root: " + serviceRoot.toAbsolutePath());
        System.out.println("=".repeat(72));
        System.out.println();

        OfflineVerificationRunner runner = new OfflineVerificationRunner(serviceRoot);
        V11RequestPathOfflineVerificationTest test = new V11RequestPathOfflineVerificationTest(runner);
        test.runAll();

        int exitCode = runner.summarize();
        System.exit(exitCode);
    }
}
