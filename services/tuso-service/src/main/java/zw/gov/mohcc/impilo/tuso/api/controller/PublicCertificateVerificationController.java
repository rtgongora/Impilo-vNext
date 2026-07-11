package zw.gov.mohcc.impilo.tuso.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityCertificateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityCertificateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public certificate verification (HPA-2017-V1: public registration truth).
 *
 * <p>Looks up by the certificate's verification code (QR/short-code) or the
 * certificate number. Discloses ONLY public fields — facility name, location,
 * certificate number, authority, licensed scope, effective/expiry dates and
 * current status. Never findings, practitioner personal data or enforcement
 * detail. Expired/suspended/superseded/revoked certificates are represented
 * honestly rather than hidden.</p>
 */
@RestController
@RequestMapping("/v1/public/facilities/certificates")
public class PublicCertificateVerificationController {

    private final FacilityCertificateRepository certificateRepository;
    private final FacilityUnitRepository facilityUnitRepository;

    public PublicCertificateVerificationController(FacilityCertificateRepository certificateRepository,
                                                   FacilityUnitRepository facilityUnitRepository) {
        this.certificateRepository = certificateRepository;
        this.facilityUnitRepository = facilityUnitRepository;
    }

    @GetMapping("/verify/{code}")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable String code) {
        String normalized = code == null ? "" : code.trim().toUpperCase();
        FacilityCertificateEntity certificate = certificateRepository.findByVerificationCode(normalized)
                .or(() -> certificateRepository.findByCertificateNumber(normalized))
                .orElse(null);
        if (certificate == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "verified", false,
                    "message", "No certificate matches this code. Verify the code and try again."));
        }

        Map<String, Object> disclosure = new LinkedHashMap<>();
        disclosure.put("verified", true);
        disclosure.put("certificateNumber", certificate.getCertificateNumber());
        disclosure.put("certificateType", certificate.getCertificateType());
        disclosure.put("status", certificate.getStatus() != null ? certificate.getStatus().name() : null);
        disclosure.put("issuedUnderAuthority", certificate.getIssuedUnderAuthority());
        disclosure.put("issueDate", certificate.getIssueDate());
        disclosure.put("expiryDate", certificate.getExpiryDate());
        disclosure.put("conditions", certificate.getConditions());
        if (certificate.getSupersedesCertificate() != null) {
            disclosure.put("supersedes", certificate.getSupersedesCertificate().getCertificateNumber());
        }
        if (certificate.getFacility() != null) {
            disclosure.put("facilityName", certificate.getFacility().getName());
            disclosure.put("province", certificate.getFacility().getProvince());
            disclosure.put("district", certificate.getFacility().getDistrict());
            disclosure.put("facilityType", certificate.getFacility().getFacilityType());
        }
        if (certificate.getFacilityUnitId() != null) {
            facilityUnitRepository.findById(certificate.getFacilityUnitId()).ifPresent(unit -> {
                disclosure.put("licensedUnit", unit.getName());
                disclosure.put("licensedUnitType", unit.getUnitType());
            });
        }
        return ResponseEntity.ok(disclosure);
    }
}
