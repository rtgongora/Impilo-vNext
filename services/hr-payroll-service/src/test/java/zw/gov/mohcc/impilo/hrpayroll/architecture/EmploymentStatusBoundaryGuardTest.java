package zw.gov.mohcc.impilo.hrpayroll.architecture;

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
 * Guards the EMPLOYMENT-STATUS ownership boundary between hr-payroll and
 * workforce-governance.
 *
 * <p>Two services hold an employment status for the same worker:
 * hr-payroll {@code EmployeeEntity.employmentStatus} (table {@code hr.employees}) and
 * workforce-governance {@code HscEmploymentEntity.employmentStatus}
 * (table {@code wgv_hsc_employment}). Only workforce-governance is the authoritative
 * <b>employment-trust</b> source of record (consumed by vashandi eligibility, the BFF
 * TrustProfileComposer and Work gating). hr-payroll's copy is <b>payroll-financial only</b>,
 * unsynced, and must never be treated as a trust/eligibility signal.</p>
 *
 * <p>These source-level assertions make that boundary non-regressible: the boundary
 * documentation cannot be silently deleted, hr-payroll must not emit an employment-trust
 * event, and known trust consumers must not wire to the hr-payroll employee record.</p>
 */
class EmploymentStatusBoundaryGuardTest {

    /** Services that compose/consume employment trust and must not read hr-payroll's copy. */
    private static final List<String> TRUST_CONSUMER_SERVICES = List.of(
            "workforce-governance-service",
            "vashandi-workforce-service",
            "experience-bff");

    private static final String HR_PAYROLL_JAVA_PACKAGE = "zw.gov.mohcc.impilo.hrpayroll";

    @Test
    void employmentStatusFieldCarriesPayrollOnlyBoundaryMarker() {
        String entity = readFile(hrPayrollMainJavaRoot()
                .resolve("zw/gov/mohcc/impilo/hrpayroll/persistence/entity/EmployeeEntity.java"));

        assertThat(entity)
                .withFailMessage("EmployeeEntity.employmentStatus must document the payroll-only "
                        + "boundary (workforce-governance is the employment-trust SoR)")
                .contains("Payroll-financial employment status ONLY")
                .contains("NOT the employment-trust source of record")
                .contains("Do not consume this field for access decisions");
    }

    @Test
    void internalHrApiDocumentsPayrollOnlyContractOnCreateEmployee() {
        String api = readFile(hrPayrollMainJavaRoot()
                .resolve("zw/gov/mohcc/impilo/hrpayroll/api/InternalHrApi.java"));

        String lower = api.toLowerCase(Locale.ROOT);
        assertThat(lower)
                .withFailMessage("InternalHrApi.createEmployee must note that employmentStatus is "
                        + "payroll-financial only and not an employment-trust source")
                .contains("payroll-financial only")
                .contains("employment-trust");
    }

    @Test
    void hrPayrollEmitsNoEmploymentTrustEvent() throws IOException {
        List<String> offendingPublishCalls = findJavaFiles(hrPayrollMainJavaRoot()).stream()
                .map(this::readFile)
                .flatMap(content -> content.lines())
                .map(line -> line.toLowerCase(Locale.ROOT))
                .filter(line -> line.contains(".publish("))
                .filter(line -> line.contains("employment") || line.contains("employee")
                        || line.contains("trust") || line.contains("eligibility"))
                .toList();

        assertThat(offendingPublishCalls)
                .withFailMessage("hr-payroll must not emit an employment/trust outbox event for the "
                        + "employee record; payroll only emits payroll-financial events. Offending "
                        + "publish call(s): %s", offendingPublishCalls)
                .isEmpty();
    }

    @Test
    void trustConsumersDoNotWireToHrPayrollEmployeeRecord() throws IOException {
        for (String service : TRUST_CONSUMER_SERVICES) {
            Path srcRoot = serviceMainJavaRoot(service);
            if (!Files.exists(srcRoot)) {
                continue;
            }
            List<String> offendingFiles = findJavaFiles(srcRoot).stream()
                    .filter(path -> readFile(path).contains(HR_PAYROLL_JAVA_PACKAGE))
                    .map(Path::toString)
                    .toList();

            assertThat(offendingFiles)
                    .withFailMessage("%s is an employment-trust consumer and must not reference the "
                            + "hr-payroll package (%s) — hr-payroll's employment_status is "
                            + "payroll-financial, not a trust signal. Offending file(s): %s",
                            service, HR_PAYROLL_JAVA_PACKAGE, offendingFiles)
                    .isEmpty();
        }
    }

    private Path repoRoot() {
        return Path.of("..", "..").toAbsolutePath().normalize();
    }

    private Path hrPayrollMainJavaRoot() {
        return serviceMainJavaRoot("hr-payroll-service");
    }

    private Path serviceMainJavaRoot(String service) {
        return repoRoot().resolve("services").resolve(service)
                .resolve("src").resolve("main").resolve("java");
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

    private String readFile(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
