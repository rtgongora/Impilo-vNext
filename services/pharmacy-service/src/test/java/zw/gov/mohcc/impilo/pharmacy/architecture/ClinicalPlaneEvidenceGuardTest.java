package zw.gov.mohcc.impilo.pharmacy.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cross-service guardrails for Clinical plane hardening evidence.
 *
 * <p>These tests intentionally validate source-level safety constraints that are
 * required for clinical production readiness: authenticated business APIs,
 * mutation audit/outbox evidence, and explicit SHR/FHIR ownership boundaries.</p>
 */
class ClinicalPlaneEvidenceGuardTest {

    private static final List<String> CLINICAL_SERVICES = List.of(
            "pharmacy-service",
            "pacs-adapter-service",
            "oros-service",
            "pct-service",
            "butano-service",
            "butano-fhir",
            "fhir-gateway-service",
            "inpatient-service",
            "document-service",
            "forms-service",
            "guidance-service",
            "rules-service",
            "clinical-knowledge-platform-service"
    );

    private static final List<String> SECURITY_FILE_NAMES = List.of("SecurityConfig.java", "WellnessSecurityConfig.java");

    private static final String[] MUTATION_ANNOTATIONS = new String[]{
            "@PostMapping", "@PutMapping", "@PatchMapping", "@DeleteMapping"
    };

    @Test
    void clinicalServicesRequireAuthenticationOnBusinessApis() throws IOException {
        for (String service : CLINICAL_SERVICES) {
            Path srcRoot = serviceMainJavaRoot(service);
            List<Path> securityFiles = SECURITY_FILE_NAMES.stream()
                    .flatMap(name -> findFilesByName(srcRoot, name).stream())
                    .toList();

            assertThat(securityFiles)
                    .withFailMessage("No security config file found for %s under %s", service, srcRoot)
                    .isNotEmpty();

            String merged = securityFiles.stream()
                    .map(this::readFile)
                    .map(s -> s.toLowerCase(Locale.ROOT))
                    .reduce("", (a, b) -> a + "\n" + b);

            assertThat(merged)
                    .withFailMessage("%s security config must enforce authenticated business endpoints", service)
                    .contains("authenticated()");
        }
    }

    @Test
    void mutationCapableClinicalServicesHaveAuditOrOutboxEvidence() throws IOException {
        for (String service : CLINICAL_SERVICES) {
            Path srcRoot = serviceMainJavaRoot(service);
            List<Path> allJava = findJavaFiles(srcRoot);
            long mutationEndpoints = allJava.stream()
                    .filter(path -> path.toString().contains("/api/") || path.toString().contains("\\api\\"))
                    .filter(path -> path.toString().contains("Controller"))
                    .map(this::readFile)
                    .filter(content -> containsAny(content, MUTATION_ANNOTATIONS))
                    .count();

            if (mutationEndpoints == 0) {
                continue;
            }

            boolean hasAuditOrOutbox = allJava.stream()
                    .map(this::readFile)
                    .anyMatch(content -> containsAny(content,
                            "EventOutbox",
                            "OutboxEvent",
                            "OutboxEventRepository",
                            "publishEvent(",
                            "OutboxPublisher",
                            "Audit",
                            "AdminAuditEmitter",
                            "auditLog",
                            "auditRepository"));

            assertThat(hasAuditOrOutbox)
                    .withFailMessage("%s exposes mutation endpoints but no audit/outbox evidence was detected", service)
                    .isTrue();
        }
    }

    @Test
    void shrAndFhirBoundariesRemainExplicit() throws IOException {
        // BUTANO is SHR owner and must not host standalone FHIR controller classes.
        List<Path> butanoControllers = findJavaFiles(serviceMainJavaRoot("butano-service")).stream()
                .filter(path -> path.toString().contains("Controller"))
                .toList();

        assertThat(butanoControllers.stream().map(Path::toString).toList())
                .noneMatch(path -> path.toLowerCase(Locale.ROOT).contains("fhir"));

        // BUTANO-FHIR must provide dedicated FHIR controller surface.
        List<Path> butanoFhirFiles = findJavaFiles(serviceMainJavaRoot("butano-fhir"));
        assertThat(butanoFhirFiles.stream().map(Path::toString).toList())
                .anyMatch(path -> path.contains("FhirResourceController"));

        // FHIR gateway must expose forwarding gateway controller and service.
        List<Path> fhirGatewayFiles = findJavaFiles(serviceMainJavaRoot("fhir-gateway-service"));
        assertThat(fhirGatewayFiles.stream().map(Path::toString).toList())
                .anyMatch(path -> path.contains("GatewayRouteController"));
        assertThat(fhirGatewayFiles.stream().map(Path::toString).toList())
                .anyMatch(path -> path.contains("GatewayForwardService"));
    }

    private Path repoRoot() {
        return Path.of("..", "..").toAbsolutePath().normalize();
    }

    private Path serviceMainJavaRoot(String service) {
        return repoRoot().resolve("services").resolve(service).resolve("src").resolve("main").resolve("java");
    }

    private List<Path> findJavaFiles(Path root) throws IOException {
        if (!Files.exists(root)) {
            return List.of();
        }
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList();
        }
    }

    private List<Path> findFilesByName(Path root, String fileName) {
        try {
            try (Stream<Path> stream = Files.walk(root)) {
                return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(fileName))
                    .toList();
            }
        } catch (IOException e) {
            return List.of();
        }
    }

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) {
            if (value.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
