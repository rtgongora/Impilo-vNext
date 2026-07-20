package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.experience.client.AbisBiometricClient;

import java.util.Map;

/**
 * Browser-facing BFF surface for the biometric enrol → verify workspace. Proxies
 * to the ABIS SoR (which delegates real matching to the core-infra matcher-engine).
 * The vertical slice: capture image → extract template → enrol under a subject →
 * verify a fresh capture → real MATCH / NO_MATCH.
 *
 * <p>Trust headers (X-Tenant-ID, actor context) are forwarded by the
 * serviceRestTemplate inside {@link AbisBiometricClient}.</p>
 */
@RestController
@RequestMapping("/internal/v1/identity/biometric/abis")
public class AbisBiometricBffController {

    private final AbisBiometricClient abis;

    public AbisBiometricBffController(AbisBiometricClient abis) {
        this.abis = abis;
    }

    /** image (+ optional width/height/dpi) → extracted template. */
    @PostMapping("/extract")
    public ResponseEntity<JsonNode> extract(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(abis.extract(body));
    }

    /** enrol a template under an opaque subject_ref. */
    @PostMapping("/enroll")
    public ResponseEntity<JsonNode> enroll(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(abis.enroll(body));
    }

    /** 1:1 verify a probe template against a subject → MATCH / NO_MATCH + confidence. */
    @PostMapping("/verify")
    public ResponseEntity<JsonNode> verify(@RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(abis.verify(body));
    }

    // --- Adjudication console + stats (W3c/W3e) ---
    // The inbound X-Actor-ID flows through the serviceRestTemplate to ABIS, which enforces
    // dual control on merge (the merge signer must differ from the decision recorder).

    @GetMapping("/adjudication/cases")
    public ResponseEntity<JsonNode> listCases(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size) {
        return ResponseEntity.ok(abis.listCases(status, page, size));
    }

    @GetMapping("/adjudication/cases/{id}")
    public ResponseEntity<JsonNode> getCase(@PathVariable long id) {
        return ResponseEntity.ok(abis.getCase(id));
    }

    @PostMapping("/adjudication/cases/{id}/assign")
    public ResponseEntity<JsonNode> assign(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(abis.assign(id, body));
    }

    @PostMapping("/adjudication/cases/{id}/decision")
    public ResponseEntity<JsonNode> decide(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(abis.decide(id, body));
    }

    @PostMapping("/adjudication/cases/{id}/merge")
    public ResponseEntity<JsonNode> merge(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(abis.merge(id, body));
    }

    @GetMapping("/stats")
    public ResponseEntity<JsonNode> stats() {
        return ResponseEntity.ok(abis.stats());
    }
}
