package zw.gov.mohcc.impilo.credential.api.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.credential.api.dto.MeshPayeeVerifyRequest;
import zw.gov.mohcc.impilo.credential.api.dto.MeshPayeeVerifyResponse;
import zw.gov.mohcc.impilo.credential.core.CredentialService;

/**
 * Public mesh path for MUSHEX payee credential verification (no JWT).
 * Restrict at the network perimeter in production.
 */
@RestController
public class MeshCredentialVerifyController {

    private final CredentialService credentialService;

    public MeshCredentialVerifyController(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    @PostMapping("/v1/credentials/verify")
    public ResponseEntity<MeshPayeeVerifyResponse> verifyPayee(@Valid @RequestBody MeshPayeeVerifyRequest request) {
        MeshPayeeVerifyResponse response = credentialService.verifyPayeeForMesh(request);
        return ResponseEntity.ok(response);
    }
}
