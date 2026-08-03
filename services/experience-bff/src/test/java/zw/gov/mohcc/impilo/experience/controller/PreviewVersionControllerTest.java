package zw.gov.mohcc.impilo.experience.controller;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * /health/version digest truthfulness: the release-time digest map is a certification
 * artifact. When this pod's runtime commit no longer matches the certified estate commit
 * (targeted image update after certification), the endpoint must say the map is not
 * authoritative for this runtime instead of presenting stale digests as current truth.
 */
class PreviewVersionControllerTest {

    private static PreviewVersionController controller(String bffCommit, String estateCommit,
                                                       String certifiedCommit, String digestsJson) {
        // Pointed at a port nothing listens on: these cases are about the digest map, and the
        // shell being unreachable is the honest default for a unit test with no shell running.
        ShellCommitResolver resolver = new ShellCommitResolver();
        ReflectionTestUtils.setField(resolver, "shellVersionUrl", "http://127.0.0.1:1/api/health/version");
        PreviewVersionController controller = new PreviewVersionController(resolver);
        ReflectionTestUtils.setField(controller, "serviceName", "experience-bff");
        ReflectionTestUtils.setField(controller, "environment", "test");
        ReflectionTestUtils.setField(controller, "gitBranch", "test-branch");
        ReflectionTestUtils.setField(controller, "gitCommit", bffCommit);
        ReflectionTestUtils.setField(controller, "buildDate", "2026-08-01T00:00:00Z");
        ReflectionTestUtils.setField(controller, "deploymentMode", "test");
        ReflectionTestUtils.setField(controller, "shellGitCommit", "");
        ReflectionTestUtils.setField(controller, "bffGitCommit", bffCommit);
        ReflectionTestUtils.setField(controller, "fullEstateCommit", estateCommit);
        ReflectionTestUtils.setField(controller, "fullEstateCertifiedCommit", certifiedCommit);
        ReflectionTestUtils.setField(controller, "helmRelease", "impilo-full-preview");
        ReflectionTestUtils.setField(controller, "helmRevision", "9");
        ReflectionTestUtils.setField(controller, "imageDigestsJson", digestsJson);
        ReflectionTestUtils.setField(controller, "versionGeneratedAt", "2026-07-25T03:38:44Z");
        return controller;
    }

    @Test
    @DisplayName("Digest map certified for a different estate commit is flagged non-authoritative")
    void staleDigestMapFlagged() {
        Map<String, Object> body = controller(
                "bff-commit-new", "estate-commit-old", "estate-commit-old",
                "{\"experience-bff\":\"sha256:old\"}").version();

        assertEquals(Boolean.FALSE, body.get("imageDigestsAuthoritativeForThisRuntime"));
        assertEquals("estate-commit-old", body.get("imageDigestsCertifiedForCommit"));
        assertEquals("2026-07-25T03:38:44Z", body.get("imageDigestsCertifiedAt"));
    }

    @Test
    @DisplayName("Digest map matching the runtime commit is authoritative")
    void matchingDigestMapAuthoritative() {
        Map<String, Object> body = controller(
                "same-commit", "same-commit", "same-commit",
                "{\"experience-bff\":\"sha256:current\"}").version();

        assertEquals(Boolean.TRUE, body.get("imageDigestsAuthoritativeForThisRuntime"));
    }

    @Test
    @DisplayName("Absent digest map does not fabricate a staleness verdict")
    void absentDigestMapNeutral() {
        Map<String, Object> body = controller(
                "bff-commit", "estate-commit", "", "").version();

        assertEquals(Boolean.TRUE, body.get("imageDigestsAuthoritativeForThisRuntime"));
        assertEquals(Map.of(), body.get("imageDigests"));
    }

    @Test
    @DisplayName("An unreachable shell never reports shellCommit as verified")
    void unverifiedShellCommitIsLabelled() {
        Map<String, Object> body = controller(
                "bff-commit", "estate-commit", "", "").version();

        assertEquals(Boolean.FALSE, body.get("shellCommitVerified"));
        assertEquals("UNKNOWN", body.get("shellCommitSource"));
    }

    @Test
    @DisplayName("shellCommit never falls back to the BFF's own commit")
    void shellCommitDoesNotBorrowTheBffCommit() {
        // This is the original defect in its purest form. The old chain was
        // nonBlank(shellGitCommit, effectiveBffCommit), so with no shell value configured the
        // endpoint reported the BFF's SHA under the name shellCommit — a wrong answer that looked
        // exactly like a right one.
        Map<String, Object> body = controller(
                "bff-commit", "estate-commit", "", "").version();

        assertEquals("", body.get("shellCommit"));
        assertEquals("bff-commit", body.get("bffCommit"));
    }
}
