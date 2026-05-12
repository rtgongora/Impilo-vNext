package zw.gov.mohcc.impilo.experience.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.util.UriComponentsBuilder;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;

@RestController
@RequestMapping("/internal/v1/vito/slips")
public class VitoSlipsBffController {

    private final VitoServiceClient vitoServiceClient;

    public VitoSlipsBffController(VitoServiceClient vitoServiceClient) {
        this.vitoServiceClient = vitoServiceClient;
    }

    @GetMapping("/emergency-capsule.pdf")
    public ResponseEntity<byte[]> emergencyCapsule(
            @RequestParam String healthId,
            @RequestParam(required = false) Boolean hasAllergies,
            @RequestParam(required = false) Boolean hasMajorConditions,
            @RequestParam(required = false) String payerIndicator) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/v1/slips/emergency-capsule.pdf")
                .queryParam("healthId", healthId);
        if (hasAllergies != null) builder.queryParam("hasAllergies", hasAllergies);
        if (hasMajorConditions != null) builder.queryParam("hasMajorConditions", hasMajorConditions);
        if (payerIndicator != null && !payerIndicator.isBlank()) builder.queryParam("payerIndicator", payerIndicator);
        try {
            ResponseEntity<byte[]> upstream = vitoServiceClient.rawGetBytes(builder.toUriString());
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"emergency-capsule.pdf\"")
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }

    @GetMapping("/pickup.pdf")
    public ResponseEntity<byte[]> pickupSlip(
            @RequestParam String pickupToken,
            @RequestParam String otp,
            @RequestParam String delegateName,
            @RequestParam String facilityName,
            @RequestParam String expiresAt) {
        String path = UriComponentsBuilder.fromPath("/v1/slips/pickup.pdf")
                .queryParam("pickupToken", pickupToken)
                .queryParam("otp", otp)
                .queryParam("delegateName", delegateName)
                .queryParam("facilityName", facilityName)
                .queryParam("expiresAt", expiresAt)
                .toUriString();
        try {
            ResponseEntity<byte[]> upstream = vitoServiceClient.rawGetBytes(path);
            return ResponseEntity.status(upstream.getStatusCode())
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"pickup.pdf\"")
                    .body(upstream.getBody());
        } catch (HttpStatusCodeException ex) {
            return ResponseEntity.status(ex.getStatusCode()).build();
        }
    }
}
