package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.Map;

/**
 * "My Regulatory Affairs" applicant lanes (ROM-U). The signed-in person's Health-ID rides the
 * forwarded trust context, so varapi resolves the caller's OWN records — never another person's.
 */
@RestController
@RequestMapping("/internal/v1/me/regulatory")
public class MeRegulatoryBffController {

    private final VarapiServiceClient varapiClient;

    public MeRegulatoryBffController(VarapiServiceClient varapiClient) {
        this.varapiClient = varapiClient;
    }

    @GetMapping("/summary")
    public ResponseEntity<JsonNode> summary() {
        return ResponseEntity.ok(varapiClient.getMyRegulatorySummary());
    }

    @GetMapping("/renewal-eligibility")
    public ResponseEntity<JsonNode> renewalEligibility() {
        return ResponseEntity.ok(varapiClient.getMeRegulatory("/renewal-eligibility"));
    }

    @GetMapping("/applications")
    public ResponseEntity<JsonNode> applications() {
        return ResponseEntity.ok(varapiClient.getMeRegulatory("/applications"));
    }

    /** Complaints involving me (ROM-U3) — disciplinary matters where I am the respondent. */
    @GetMapping("/complaints")
    public ResponseEntity<JsonNode> complaints() {
        return ResponseEntity.ok(varapiClient.getMeRegulatory("/complaints"));
    }

    /** The applicant's view of one application — APPLICANT-visible thread + RFIs (never internal notes). */
    @GetMapping("/applications/{applicationId}/correspondence")
    public ResponseEntity<JsonNode> applicationCorrespondence(@PathVariable Long applicationId) {
        return ResponseEntity.ok(varapiClient.getRegulatoryApplication(applicationId, "/correspondence/applicant"));
    }

    /** Applicant responds to an RFI. */
    @PostMapping("/applications/{applicationId}/rfis/{rfiId}/respond")
    public ResponseEntity<JsonNode> respondRfi(@PathVariable Long applicationId, @PathVariable Long rfiId,
                                               @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(varapiClient.postRegulatoryApplication(
                applicationId, "/correspondence/rfis/" + rfiId + "/respond", body));
    }

    /** Applicant posts a message on their application. */
    @PostMapping("/applications/{applicationId}/messages")
    public ResponseEntity<JsonNode> applicantMessage(@PathVariable Long applicationId,
                                                     @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(varapiClient.postRegulatoryApplication(
                applicationId, "/correspondence/applicant/messages", body));
    }
}
