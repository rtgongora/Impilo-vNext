package zw.gov.mohcc.impilo.tshepo.authz.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.tshepo.authz.dto.AuthorityUpdateRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.FederationPodRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.FederationPodResponse;
import zw.gov.mohcc.impilo.tshepo.authz.service.FederationControlService;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/internal/v1/federation/pods")
public class FederationController {

    private static final Logger log = LoggerFactory.getLogger(FederationController.class);

    private final FederationControlService federationControlService;

    public FederationController(FederationControlService federationControlService) {
        this.federationControlService = federationControlService;
    }

    @PostMapping
    public ResponseEntity<FederationPodResponse> registerPod(
            @Valid @RequestBody FederationPodRequest request) {
        try {
            FederationPodResponse response = federationControlService.registerPod(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            log.warn("Federation pod registration failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    @GetMapping
    public ResponseEntity<List<FederationPodResponse>> listPods() {
        return ResponseEntity.ok(federationControlService.listPods());
    }

    @PutMapping("/{podId}/authority")
    public ResponseEntity<FederationPodResponse> updateAuthority(
            @PathVariable String podId,
            @Valid @RequestBody AuthorityUpdateRequest request) {
        try {
            FederationPodResponse response = federationControlService.updateAuthority(podId, request);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            log.warn("Federation pod authority update failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{podId}/obligations")
    public ResponseEntity<Map<String, Object>> getObligations(@PathVariable String podId) {
        try {
            Map<String, Object> obligations = federationControlService.getObligations(podId);
            return ResponseEntity.ok(obligations != null ? obligations : Map.of());
        } catch (IllegalArgumentException e) {
            log.warn("Federation pod obligations query failed: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}
