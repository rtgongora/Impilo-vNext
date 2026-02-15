package zw.gov.mohcc.impilo.ia.api;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ia.core.AttestationOutcome;
import zw.gov.mohcc.impilo.ia.core.AttestationService;
import zw.gov.mohcc.impilo.ia.core.AttestationType;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;

@RestController
@RequestMapping("/internal/v1/attestations")
public class AttestationController {

    private final AttestationService attestationService;

    public AttestationController(AttestationService attestationService) {
        this.attestationService = attestationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AttestationEntity>> recordAttestation(
            @RequestBody RecordAttestationRequest request) {
        TrustContext ctx = TrustContextHolder.require();

        AttestationEntity attestation = attestationService.recordAttestation(
                ctx.tenantId(), ctx.actorId(), ctx.correlationId(),
                request.attestationType(), request.deviceFingerprint(),
                request.evidence(), request.outcome(), request.confidence());

        return ResponseEntity.status(201).body(
                ApiResponse.ok(attestation, ctx.correlationId().toString()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AttestationEntity>>> listAttestations() {
        TrustContext ctx = TrustContextHolder.require();

        List<AttestationEntity> attestations = attestationService.listAttestations(ctx.tenantId());

        return ResponseEntity.ok(
                ApiResponse.ok(attestations, ctx.correlationId().toString()));
    }

    public record RecordAttestationRequest(
            AttestationType attestationType,
            String deviceFingerprint,
            String evidence,
            AttestationOutcome outcome,
            BigDecimal confidence
    ) {}
}
