package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
