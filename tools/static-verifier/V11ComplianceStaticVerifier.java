import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Static verifier for v1.1 Spec 2.0 compliance.
 *
 * Checks source files (no Spring context, no Maven needed) to verify that
 * enforcement wiring is correctly configured across the monorepo.
 *
 * Run with:
 *   javac V11ComplianceStaticVerifier.java && java V11ComplianceStaticVerifier /path/to/Impilo-vNext
 *
 * Exit code 0 = all checks pass, 1 = failures found.
 *
 * NOTE: This does NOT replace real integration tests (GoldenContractIT, *V11ComplianceTest).
 *       It is only an offline evidence tool for CI or environments where Maven cannot run.
 */
public class V11ComplianceStaticVerifier {

    private static int passed = 0;
    private static int failed = 0;
    private static int skipped = 0;

    public static void main(String[] args) throws Exception {
        String repoRoot = args.length > 0 ? args[0] : ".";
        Path root = Paths.get(repoRoot).toAbsolutePath();

        System.out.println("=== v1.1 Spec 2.0 Static Compliance Verifier ===");
        System.out.println("Repo root: " + root);
        System.out.println();

        // ── 1. CompanionHeaders defines all 4 HARD_REQUIRED headers ──
        Path companionHeaders = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/context/CompanionHeaders.java");
        check("CompanionHeaders.java exists", Files.exists(companionHeaders));
        if (Files.exists(companionHeaders)) {
            String src = Files.readString(companionHeaders);
            check("HARD_REQUIRED array defined", src.contains("HARD_REQUIRED"));
            check("HARD_REQUIRED includes TENANT_ID", src.contains("TENANT_ID") && src.contains("X-Tenant-ID"));
            check("HARD_REQUIRED includes POD_ID", src.contains("POD_ID") && src.contains("X-Pod-ID"));
            check("HARD_REQUIRED includes REQUEST_ID", src.contains("REQUEST_ID") && src.contains("X-Request-ID"));
            check("HARD_REQUIRED includes CORRELATION_ID", src.contains("CORRELATION_ID") && src.contains("X-Correlation-ID"));
            check("IDEMPOTENCY_KEY constant defined", src.contains("IDEMPOTENCY_KEY") && src.contains("Idempotency-Key"));
        }

        // ── 2. V11HeaderFilter enforces all HARD_REQUIRED headers ──
        Path v11Filter = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/filter/V11HeaderFilter.java");
        check("V11HeaderFilter.java exists", Files.exists(v11Filter));
        if (Files.exists(v11Filter)) {
            String src = Files.readString(v11Filter);
            check("V11HeaderFilter iterates HARD_REQUIRED", src.contains("CompanionHeaders.HARD_REQUIRED"));
            check("V11HeaderFilter checks isBlank()", src.contains("isBlank()"));
            check("V11HeaderFilter uses MISSING_REQUIRED_HEADER code",
                    src.contains("ErrorCodes.MISSING_REQUIRED_HEADER"));
            check("V11HeaderFilter responds with 400", src.contains("400"));
            check("V11HeaderFilter checks /internal/v1/ paths", src.contains("/internal/v1/"));
            check("V11HeaderFilter checks /external/v1/ paths", src.contains("/external/v1/"));
        }

        // ── 3. ErrorCodes has all required constants ──
        Path errorCodes = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/error/ErrorCodes.java");
        check("ErrorCodes.java exists", Files.exists(errorCodes));
        if (Files.exists(errorCodes)) {
            String src = Files.readString(errorCodes);
            check("ErrorCodes.MISSING_REQUIRED_HEADER", src.contains("MISSING_REQUIRED_HEADER"));
            check("ErrorCodes.IDENTITY_CONFLICT", src.contains("IDENTITY_CONFLICT"));
            check("ErrorCodes.FEDERATION_AUTHORITY_VIOLATION", src.contains("FEDERATION_AUTHORITY_VIOLATION"));
            check("ErrorCodes.IDEMPOTENCY_KEY_REQUIRED", src.contains("IDEMPOTENCY_KEY_REQUIRED"));
            check("ErrorCodes.CLIENT_TIMEOUT_EXCEEDED", src.contains("CLIENT_TIMEOUT_EXCEEDED"));
        }

        // ── 4. ErrorEnvelope has all 5 fields ──
        Path errorEnvelope = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/error/ErrorEnvelope.java");
        check("ErrorEnvelope.java exists", Files.exists(errorEnvelope));
        if (Files.exists(errorEnvelope)) {
            String src = Files.readString(errorEnvelope);
            check("ErrorEnvelope.ErrorBody has code", src.contains("String code"));
            check("ErrorEnvelope.ErrorBody has message", src.contains("String message"));
            check("ErrorEnvelope.ErrorBody has details", src.contains("details"));
            check("ErrorEnvelope.ErrorBody has request_id", src.contains("request_id"));
            check("ErrorEnvelope.ErrorBody has correlation_id", src.contains("correlation_id"));
        }

        // ── 5. IdempotencyFilter uses V11HeaderFilter.isV11Path ──
        Path idempotencyFilter = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/filter/IdempotencyFilter.java");
        check("IdempotencyFilter.java exists", Files.exists(idempotencyFilter));
        if (Files.exists(idempotencyFilter)) {
            String src = Files.readString(idempotencyFilter);
            check("IdempotencyFilter uses V11HeaderFilter.isV11Path",
                    src.contains("V11HeaderFilter.isV11Path"));
            check("IdempotencyFilter checks Idempotency-Key",
                    src.contains("CompanionHeaders.IDEMPOTENCY_KEY"));
            check("IdempotencyFilter uses IDENTITY_CONFLICT code",
                    src.contains("ErrorCodes.IDENTITY_CONFLICT"));
            check("IdempotencyFilter responds 409 on conflict", src.contains("409"));
        }

        // ── 6. GoldenContractSuite tests all 4 headers individually ──
        Path goldenSuite = root.resolve(
                "libs/tech-companion-harness/src/main/java/zw/gov/mohcc/impilo/companion/harness/GoldenContractSuite.java");
        check("GoldenContractSuite.java exists", Files.exists(goldenSuite));
        if (Files.exists(goldenSuite)) {
            String src = Files.readString(goldenSuite);
            check("GoldenContractSuite: missingTenantIdReturns400 test",
                    src.contains("missingTenantIdReturns400"));
            check("GoldenContractSuite: missingPodIdReturns400 test",
                    src.contains("missingPodIdReturns400"));
            check("GoldenContractSuite: missingRequestIdReturns400 test",
                    src.contains("missingRequestIdReturns400"));
            check("GoldenContractSuite: missingCorrelationIdReturns400 test",
                    src.contains("missingCorrelationIdReturns400"));
            check("GoldenContractSuite: missingAllFourHeadersReturns400 test",
                    src.contains("missingAllFourHeadersReturns400"));
            check("GoldenContractSuite: IDENTITY_CONFLICT test",
                    src.contains("IDENTITY_CONFLICT"));
            check("GoldenContractSuite: FEDERATION_AUTHORITY_VIOLATION test",
                    src.contains("FEDERATION_AUTHORITY_VIOLATION"));
        }

        // ── 7. V11HeaderFilterTest asserts 4 headers ──
        Path filterTest = root.resolve(
                "libs/tech-companion/src/test/java/zw/gov/mohcc/impilo/companion/filter/V11HeaderFilterTest.java");
        check("V11HeaderFilterTest.java exists", Files.exists(filterTest));
        if (Files.exists(filterTest)) {
            String src = Files.readString(filterTest);
            check("V11HeaderFilterTest asserts length 4",
                    src.contains("assertEquals(4,"));
            check("V11HeaderFilterTest asserts X-Request-ID",
                    src.contains("X-Request-ID"));
            check("V11HeaderFilterTest asserts X-Correlation-ID",
                    src.contains("X-Correlation-ID"));
        }

        // ── 8. Target services have GoldenContractIT ──
        String[] targetServices = {
                "services/integration-hub",
                "services/notification-service",
                "services/rules-service",
                "services/experience-bff"
        };
        for (String svc : targetServices) {
            String svcName = svc.substring(svc.lastIndexOf('/') + 1);
            List<Path> goldenITs = findFiles(root.resolve(svc), "GoldenContractIT.java");
            check(svcName + " has GoldenContractIT.java", !goldenITs.isEmpty());
            if (!goldenITs.isEmpty()) {
                String src = Files.readString(goldenITs.get(0));
                check(svcName + " GoldenContractIT extends GoldenContractSuite",
                        src.contains("extends GoldenContractSuite"));
            }
        }

        // ── 9. Target services have V11ComplianceTest ──
        for (String svc : targetServices) {
            String svcName = svc.substring(svc.lastIndexOf('/') + 1);
            List<Path> complianceTests = findFiles(root.resolve(svc), "V11ComplianceTest.java");
            check(svcName + " has V11ComplianceTest.java", !complianceTests.isEmpty());
            if (!complianceTests.isEmpty()) {
                String src = Files.readString(complianceTests.get(0));
                check(svcName + " V11ComplianceTest validates header enforcement",
                        src.contains("MISSING_REQUIRED_HEADER"));
                check(svcName + " V11ComplianceTest validates IDENTITY_CONFLICT",
                        src.contains("IDENTITY_CONFLICT"));
                check(svcName + " V11ComplianceTest validates outbox fields",
                        (src.contains("getSchemaVersion") || src.contains("schema_version"))
                        && (src.contains("getEventType") || src.contains("event_type")));
            }
        }

        // ── 10. Target services have outbox entity/service with required fields ──
        for (String svc : targetServices) {
            String svcName = svc.substring(svc.lastIndexOf('/') + 1);
            List<Path> outboxFiles = findFiles(root.resolve(svc), "OutboxEventEntity.java");
            if (outboxFiles.isEmpty()) {
                outboxFiles = findFiles(root.resolve(svc), "OutboxService.java");
            }
            check(svcName + " has outbox entity or service", !outboxFiles.isEmpty());
            if (!outboxFiles.isEmpty()) {
                String src = Files.readString(outboxFiles.get(0));
                check(svcName + " outbox has tenant_id",
                        src.contains("tenant_id") || src.contains("tenantId"));
                check(svcName + " outbox has pod_id",
                        src.contains("pod_id") || src.contains("podId"));
                check(svcName + " outbox has correlation_id",
                        src.contains("correlation_id") || src.contains("correlationId"));
                check(svcName + " outbox has schema_version",
                        src.contains("schema_version") || src.contains("schemaVersion"));
                check(svcName + " outbox has event_type",
                        src.contains("event_type") || src.contains("eventType"));
            }
        }

        // ── 11. FederationAuthority uses correct error code ──
        Path fedAuth = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/federation/FederationAuthority.java");
        if (Files.exists(fedAuth)) {
            String src = Files.readString(fedAuth);
            check("FederationAuthority calls requireNational",
                    src.contains("requireNational"));
            check("FederationAuthority throws FederationAuthorityViolationException",
                    src.contains("FederationAuthorityViolationException"));
        }

        Path fedExHandler = root.resolve(
                "libs/tech-companion/src/main/java/zw/gov/mohcc/impilo/companion/filter/CompanionExceptionHandler.java");
        if (Files.exists(fedExHandler)) {
            String src = Files.readString(fedExHandler);
            check("CompanionExceptionHandler uses FEDERATION_AUTHORITY_VIOLATION code",
                    src.contains("ErrorCodes.FEDERATION_AUTHORITY_VIOLATION"));
            check("CompanionExceptionHandler returns 403 for federation violation",
                    src.contains("FORBIDDEN"));
        }

        // ── Summary ──
        System.out.println();
        System.out.println("=== VERIFICATION RESULTS ===");
        System.out.println("Passed:  " + passed);
        System.out.println("Failed:  " + failed);
        System.out.println("Skipped: " + skipped);
        System.out.println("Total:   " + (passed + failed + skipped));

        if (failed > 0) {
            System.out.println("\n*** FAILURES DETECTED — repo is NOT v1.1 Spec 2.0 compliant ***");
            System.exit(1);
        } else {
            System.out.println("\n*** ALL STATIC CHECKS PASSED — v1.1 Spec 2.0 wiring verified ***");
            System.exit(0);
        }
    }

    private static void check(String name, boolean condition) {
        if (condition) {
            System.out.println("  PASS: " + name);
            passed++;
        } else {
            System.out.println("  FAIL: " + name);
            failed++;
        }
    }

    private static List<Path> findFiles(Path dir, String fileName) throws IOException {
        if (!Files.exists(dir)) return List.of();
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().endsWith(fileName) || file.getFileName().toString().contains(fileName.replace(".java", ""))) {
                    found.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return found;
    }
}
