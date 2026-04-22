package zw.gov.mohcc.impilo.varapi.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.varapi.api.dto.GenericResponse;
import zw.gov.mohcc.impilo.varapi.core.CertificateService;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCertificateEntity;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * REST controller for provider practising certificates.
 */
@RestController
@RequestMapping("/v1/internal/providers/certificates")
public class CertificateController {

    private final CertificateService certificateService;

    public CertificateController(CertificateService certificateService) {
        this.certificateService = certificateService;
    }

    @PostMapping
    public ResponseEntity<GenericResponse> createCertificate(@RequestBody Map<String, Object> request) {
        Long providerId = Long.valueOf(request.get("providerId").toString());
        String certificateType = (String) request.get("certificateType");
        String certificateNumber = (String) request.get("certificateNumber");
        LocalDate issueDate = request.get("issueDate") != null ?
                LocalDate.parse((String) request.get("issueDate")) : LocalDate.now();
        LocalDate expiryDate = request.get("expiryDate") != null ?
                LocalDate.parse((String) request.get("expiryDate")) : null;
        String issuingAuthority = (String) request.get("issuingAuthority");
        String notes = (String) request.get("notes");

        ProviderCertificateEntity certificate = certificateService.createCertificate(
                providerId, certificateType, certificateNumber, issueDate, expiryDate,
                issuingAuthority, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Certificate created",
                Map.of("certificateId", certificate.getId(), "status", certificate.getStatus())
        ));
    }

    @PostMapping("/{certificateId}/issue")
    public ResponseEntity<GenericResponse> issueCertificate(
            @PathVariable Long certificateId,
            @RequestBody Map<String, Object> request) {
        String certificateNumber = (String) request.get("certificateNumber");
        LocalDate issueDate = request.get("issueDate") != null ?
                LocalDate.parse((String) request.get("issueDate")) : LocalDate.now();
        LocalDate expiryDate = request.get("expiryDate") != null ?
                LocalDate.parse((String) request.get("expiryDate")) : null;

        ProviderCertificateEntity certificate = certificateService.issueCertificate(
                certificateId, certificateNumber, issueDate, expiryDate);

        return ResponseEntity.ok(GenericResponse.success(
                "Certificate issued",
                Map.of("certificateId", certificate.getId(), "certificateNumber", certificate.getCertificateNumber())
        ));
    }

    @PostMapping("/{certificateId}/renew")
    public ResponseEntity<GenericResponse> renewCertificate(
            @PathVariable Long certificateId,
            @RequestBody Map<String, Object> request) {
        LocalDate newExpiryDate = LocalDate.parse((String) request.get("newExpiryDate"));
        String renewalReference = (String) request.get("renewalReference");
        String notes = (String) request.get("notes");

        ProviderCertificateEntity certificate = certificateService.renewCertificate(
                certificateId, newExpiryDate, renewalReference, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Certificate renewed",
                Map.of("certificateId", certificate.getId(), "expiryDate", certificate.getExpiryDate())
        ));
    }

    @PostMapping("/{certificateId}/suspend")
    public ResponseEntity<GenericResponse> suspendCertificate(
            @PathVariable Long certificateId,
            @RequestBody Map<String, Object> request) {
        LocalDate suspensionDate = request.get("suspensionDate") != null ?
                LocalDate.parse((String) request.get("suspensionDate")) : LocalDate.now();
        String reason = (String) request.get("reason");
        String notes = (String) request.get("notes");

        ProviderCertificateEntity certificate = certificateService.suspendCertificate(
                certificateId, suspensionDate, reason, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Certificate suspended",
                Map.of("certificateId", certificate.getId(), "status", certificate.getStatus())
        ));
    }

    @PostMapping("/{certificateId}/reinstate")
    public ResponseEntity<GenericResponse> reinstateCertificate(
            @PathVariable Long certificateId,
            @RequestBody Map<String, Object> request) {
        LocalDate reinstatementDate = request.get("reinstatementDate") != null ?
                LocalDate.parse((String) request.get("reinstatementDate")) : LocalDate.now();
        String notes = (String) request.get("notes");

        ProviderCertificateEntity certificate = certificateService.reinstateCertificate(
                certificateId, reinstatementDate, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Certificate reinstated",
                Map.of("certificateId", certificate.getId(), "status", certificate.getStatus())
        ));
    }

    @PostMapping("/{certificateId}/revoke")
    public ResponseEntity<GenericResponse> revokeCertificate(
            @PathVariable Long certificateId,
            @RequestBody Map<String, Object> request) {
        LocalDate revocationDate = request.get("revocationDate") != null ?
                LocalDate.parse((String) request.get("revocationDate")) : LocalDate.now();
        String reason = (String) request.get("reason");
        String notes = (String) request.get("notes");

        ProviderCertificateEntity certificate = certificateService.revokeCertificate(
                certificateId, revocationDate, reason, notes);

        return ResponseEntity.ok(GenericResponse.success(
                "Certificate revoked",
                Map.of("certificateId", certificate.getId(), "status", certificate.getStatus())
        ));
    }

    @GetMapping("/{certificateId}")
    public ResponseEntity<ProviderCertificateEntity> getCertificate(@PathVariable Long certificateId) {
        return ResponseEntity.ok(certificateService.getCertificate(certificateId));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ProviderCertificateEntity>> getCertificatesByProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(certificateService.getCertificatesByProvider(providerId));
    }

    @GetMapping("/provider/{providerId}/valid")
    public ResponseEntity<List<ProviderCertificateEntity>> getValidCertificates(@PathVariable Long providerId) {
        return ResponseEntity.ok(certificateService.getValidCertificates(providerId));
    }

    @GetMapping("/provider/{providerId}/current")
    public ResponseEntity<ProviderCertificateEntity> getCurrentCertificate(@PathVariable Long providerId) {
        ProviderCertificateEntity certificate = certificateService.getCurrentCertificate(providerId);
        return certificate != null ? ResponseEntity.ok(certificate) : ResponseEntity.notFound().build();
    }

    @GetMapping("/number/{certificateNumber}")
    public ResponseEntity<ProviderCertificateEntity> getCertificateByNumber(@PathVariable String certificateNumber) {
        ProviderCertificateEntity certificate = certificateService.getCertificateByNumber(certificateNumber);
        return certificate != null ? ResponseEntity.ok(certificate) : ResponseEntity.notFound().build();
    }

    @GetMapping("/expiring")
    public ResponseEntity<List<ProviderCertificateEntity>> getExpiringCertificates(
            @RequestParam(defaultValue = "30") int daysAhead) {
        return ResponseEntity.ok(certificateService.getExpiringCertificates(daysAhead));
    }

    @GetMapping("/expired")
    public ResponseEntity<List<ProviderCertificateEntity>> getExpiredCertificates() {
        return ResponseEntity.ok(certificateService.getExpiredCertificates());
    }
}