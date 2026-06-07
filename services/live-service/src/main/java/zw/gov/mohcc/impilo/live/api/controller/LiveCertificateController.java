package zw.gov.mohcc.impilo.live.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.live.api.dto.LiveDtos;
import zw.gov.mohcc.impilo.live.core.CertificateService;
import zw.gov.mohcc.impilo.live.persistence.entity.LiveEventCertificateEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/live/certificates")
public class LiveCertificateController {

    private final CertificateService certificateService;

    public LiveCertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping("/{eventId}/attendance/{participantId}")
    public ResponseEntity<LiveDtos.CertificateResponse> issueAttendance(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(
                certificateService.issueAttendanceCertificate(tenantId, eventId, participantId)));
    }

    @PostMapping("/{eventId}/cpd/{participantId}")
    public ResponseEntity<LiveDtos.CertificateResponse> issueCpd(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId,
            @PathVariable String participantId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(
                certificateService.issueCpdCertificate(tenantId, eventId, participantId)));
    }

    @GetMapping("/{eventId}")
    public List<LiveDtos.CertificateResponse> list(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable UUID eventId) {
        return certificateService.listForEvent(tenantId, eventId).stream()
                .map(this::toResponse).toList();
    }

    @GetMapping("/verify/{verificationCode}")
    public LiveDtos.CertificateResponse verify(@PathVariable String verificationCode) {
        return toResponse(certificateService.verify(verificationCode));
    }

    private LiveDtos.CertificateResponse toResponse(LiveEventCertificateEntity c) {
        return new LiveDtos.CertificateResponse(
                c.getId(), c.getEventId(), c.getParticipantId(),
                c.getCertificateType(), c.getVerificationCode(), c.getIssuedAt());
    }
}
