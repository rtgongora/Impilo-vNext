package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PreviewVersionController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${spring.application.name:experience-bff}")
    private String serviceName;

    @Value("${IMPILO_ENV:local}")
    private String environment;

    @Value("${IMPILO_GIT_BRANCH:unknown}")
    private String gitBranch;

    @Value("${IMPILO_GIT_COMMIT:unknown}")
    private String gitCommit;

    @Value("${IMPILO_BUILD_DATE:unknown}")
    private String buildDate;

    @Value("${IMPILO_DEPLOYMENT_MODE:unknown}")
    private String deploymentMode;

    @Value("${IMPILO_SHELL_GIT_COMMIT:}")
    private String shellGitCommit;

    @Value("${IMPILO_BFF_GIT_COMMIT:}")
    private String bffGitCommit;

    @Value("${IMPILO_FULL_ESTATE_COMMIT:}")
    private String fullEstateCommit;

    @Value("${IMPILO_FULL_ESTATE_CERTIFIED_COMMIT:}")
    private String fullEstateCertifiedCommit;

    @Value("${IMPILO_HELM_RELEASE:}")
    private String helmRelease;

    @Value("${IMPILO_HELM_REVISION:}")
    private String helmRevision;

    @Value("${IMPILO_IMAGE_DIGESTS_JSON:}")
    private String imageDigestsJson;

    @Value("${IMPILO_VERSION_GENERATED_AT:}")
    private String versionGeneratedAt;

    @GetMapping("/health/version")
    public Map<String, Object> version() {
        String effectiveBffCommit = nonBlank(bffGitCommit, gitCommit);
        String effectiveShellCommit = nonBlank(shellGitCommit, effectiveBffCommit);
        String effectiveEstateCommit = nonBlank(fullEstateCommit, effectiveBffCommit);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", serviceName);
        body.put("environment", environment);
        body.put("branch", gitBranch);
        body.put("status", "ok");

        // Legacy single-commit field: BFF image/runtime commit (not shell-only deploy HEAD).
        body.put("commit", effectiveBffCommit);
        body.put("buildDate", buildDate);

        body.put("deploymentMode", deploymentMode);
        body.put("shellCommit", effectiveShellCommit);
        body.put("bffCommit", effectiveBffCommit);
        body.put("fullEstateCommit", effectiveEstateCommit);
        body.put("fullEstateCertifiedCommit", nonBlank(fullEstateCertifiedCommit, ""));
        body.put("helmRelease", nonBlank(helmRelease, ""));
        body.put("helmRevision", nonBlank(helmRevision, ""));
        body.put("imageDigests", parseImageDigests(imageDigestsJson));
        body.put("generatedAt", nonBlank(versionGeneratedAt, Instant.now().toString()));

        return body;
    }

    private static String nonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback != null ? fallback : "";
    }

    private static Map<String, String> parseImageDigests(String raw) {
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, String> parsed = MAPPER.readValue(raw, new TypeReference<>() {});
            return parsed != null ? parsed : Map.of();
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
