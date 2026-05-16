package zw.gov.mohcc.impilo.experience.controller.mobile.provider;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;
import zw.gov.mohcc.impilo.experience.publichealth.PublicHealthGovernanceService;

import java.util.Map;

/**
 * Native mobile surface for public-health field operations (task board + ops summary).
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider/public-health")
public class ProviderPublicHealthController {

    private final RestTemplate restTemplate;
    private final String surveillanceUrl;
    private final PublicHealthGovernanceService governance;

    public ProviderPublicHealthController(
            RestTemplate serviceRestTemplate,
            ServiceClientConfig.ServiceEndpoints endpoints,
            PublicHealthGovernanceService governance) {
        this.restTemplate = serviceRestTemplate;
        this.surveillanceUrl = endpoints.surveillanceBaseUrl();
        this.governance = governance;
    }

    @GetMapping("/operations-home")
    public ResponseEntity<?> operationsHome(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return restTemplate.getForEntity(
                surveillanceUrl + "/internal/v1/public-health/operations-home",
                Map.class);
    }

    @GetMapping("/field-tasks")
    public ResponseEntity<?> fieldTasks(@RequestHeader(CompanionHeaders.REQUEST_ID) String requestId) {
        governance.assertGovernedRead();
        return restTemplate.getForEntity(
                surveillanceUrl + "/internal/v1/public-health/field-tasks",
                Map.class);
    }

    @PostMapping("/field-tasks/{taskId}/transition")
    public ResponseEntity<?> transitionFieldTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> body) {
        governance.assertGovernedMutate();
        return restTemplate.postForEntity(
                surveillanceUrl + "/internal/v1/public-health/field-tasks/" + taskId + "/transition",
                body,
                Map.class);
    }
}
