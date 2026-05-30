package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class PreviewVersionController {

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

    @GetMapping("/health/version")
    public Map<String, Object> version() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", serviceName);
        body.put("environment", environment);
        body.put("branch", gitBranch);
        body.put("commit", gitCommit);
        body.put("buildDate", buildDate);
        body.put("status", "ok");
        return body;
    }
}
