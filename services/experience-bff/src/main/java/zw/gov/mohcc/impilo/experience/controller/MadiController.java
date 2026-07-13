package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.MadiServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Experience BFF proxy for madi-service ({@code /internal/v1/madi/**}).
 */
@RestController
@RequestMapping("/internal/v1/madi")
public class MadiController {

    private static final Logger log = LoggerFactory.getLogger(MadiController.class);

    private final MadiServiceClient client;

    public MadiController(MadiServiceClient client) {
        this.client = client;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> dashboard(
            @RequestParam(defaultValue = "local") String scope,
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestParam(name = "drive_id", required = false) String driveId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.dashboard(scope, facilityId, driveId), requestId, correlationId, false);
    }

    @PostMapping("/donors")
    public ResponseEntity<Map<String, Object>> registerDonor(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.registerDonor(body), requestId, correlationId, true);
    }

    @GetMapping("/donors/by-person")
    public ResponseEntity<Map<String, Object>> donorByPerson(
            @RequestParam("person_cpid") String personCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        // A person with no donor profile is a legitimate "not a donor yet" state,
        // not a service failure — the upstream 404 must surface as 200 {data:null}
        // so the profile page renders the "Register as a donor" empty state instead
        // of a red error banner.
        return proxy(() -> client.donorByPerson(personCpid), requestId, correlationId, false, true);
    }

    @PostMapping("/donors/{donorId}/screenings")
    public ResponseEntity<Map<String, Object>> screenDonor(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.screenDonor(donorId, body), requestId, correlationId, true);
    }

    @PostMapping("/donors/{donorId}/deferrals")
    public ResponseEntity<Map<String, Object>> deferDonor(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.deferDonor(donorId, body), requestId, correlationId, true);
    }

    @PutMapping("/donors/{donorId}/preferences")
    public ResponseEntity<Map<String, Object>> donorPreferences(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.donorPreferences(donorId, body), requestId, correlationId, false);
    }

    @PostMapping("/donors/{donorId}/feedback")
    public ResponseEntity<Map<String, Object>> donorFeedback(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.donorFeedback(donorId, body), requestId, correlationId, true);
    }

    @GetMapping("/donors/{donorId}/history")
    public ResponseEntity<Map<String, Object>> donorHistory(
            @PathVariable String donorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.donorHistory(donorId), requestId, correlationId, false);
    }

    @GetMapping("/donors/{donorId}/next-eligibility")
    public ResponseEntity<Map<String, Object>> donorNextEligibility(
            @PathVariable String donorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.donorNextEligibility(donorId), requestId, correlationId, false);
    }

    @PostMapping("/donors/{donorId}/pre-screening")
    public ResponseEntity<Map<String, Object>> submitPreScreening(
            @PathVariable String donorId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.submitPreScreening(donorId, body), requestId, correlationId, true);
    }

    @GetMapping("/donors/{donorId}/pre-screening/latest")
    public ResponseEntity<Map<String, Object>> latestPreScreening(
            @PathVariable String donorId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.latestPreScreening(donorId), requestId, correlationId, false);
    }

    @GetMapping("/donors/drives-near-me")
    public ResponseEntity<Map<String, Object>> drivesNearMe(
            @RequestParam("ndila_site_ref") String ndilaSiteRef,
            @RequestParam(value = "radius_km", defaultValue = "25") double radiusKm,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.drivesNearMe(ndilaSiteRef, radiusKm), requestId, correlationId, false);
    }

    @PostMapping("/drives")
    public ResponseEntity<Map<String, Object>> createDrive(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.createDrive(body), requestId, correlationId, true);
    }

    @GetMapping("/drives")
    public ResponseEntity<Map<String, Object>> listDrives(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listDrives(), requestId, correlationId, false);
    }

    @PostMapping("/drives/{driveId}/publish")
    public ResponseEntity<Map<String, Object>> publishDrive(
            @PathVariable String driveId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.publishDrive(driveId, body), requestId, correlationId, false);
    }

    @PostMapping("/drives/{driveId}/register")
    public ResponseEntity<Map<String, Object>> registerAtDrive(
            @PathVariable String driveId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.registerAtDrive(driveId, body), requestId, correlationId, true);
    }

    @PostMapping("/drives/{driveId}/screen")
    public ResponseEntity<Map<String, Object>> screenAtDrive(
            @PathVariable String driveId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.screenAtDrive(driveId, body), requestId, correlationId, false);
    }

    @PostMapping("/drives/{driveId}/donations")
    public ResponseEntity<Map<String, Object>> recordDonation(
            @PathVariable String driveId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.recordDonation(driveId, body), requestId, correlationId, true);
    }

    @PostMapping("/drives/{driveId}/close")
    public ResponseEntity<Map<String, Object>> closeDrive(
            @PathVariable String driveId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.closeDrive(driveId), requestId, correlationId, false);
    }

    @PostMapping("/processing/receive")
    public ResponseEntity<Map<String, Object>> receiveUnit(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.receiveUnit(body), requestId, correlationId, true);
    }

    @PostMapping("/processing/{batchId}/test")
    public ResponseEntity<Map<String, Object>> testBatch(
            @PathVariable String batchId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.testBatch(batchId, body), requestId, correlationId, false);
    }

    @PostMapping("/processing/{batchId}/quarantine")
    public ResponseEntity<Map<String, Object>> quarantineBatch(
            @PathVariable String batchId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.quarantineBatch(batchId, body), requestId, correlationId, false);
    }

    @PostMapping("/processing/{batchId}/release")
    public ResponseEntity<Map<String, Object>> releaseBatch(
            @PathVariable String batchId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.releaseBatch(batchId, body), requestId, correlationId, false);
    }

    @PostMapping("/processing/components")
    public ResponseEntity<Map<String, Object>> prepareComponent(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.prepareComponent(body), requestId, correlationId, true);
    }

    @PostMapping("/blood-banks")
    public ResponseEntity<Map<String, Object>> createBloodBank(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.createBloodBank(body), requestId, correlationId, true);
    }

    @GetMapping("/blood-banks/{bloodBankId}/stock")
    public ResponseEntity<Map<String, Object>> bloodBankStock(
            @PathVariable String bloodBankId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.bloodBankStock(bloodBankId), requestId, correlationId, false);
    }

    @PostMapping("/blood-banks/{bloodBankId}/movements")
    public ResponseEntity<Map<String, Object>> bloodBankMovement(
            @PathVariable String bloodBankId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.bloodBankMovement(bloodBankId, body), requestId, correlationId, true);
    }

    @PostMapping("/blood-banks/{bloodBankId}/reconciliations")
    public ResponseEntity<Map<String, Object>> bloodBankReconciliation(
            @PathVariable String bloodBankId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.bloodBankReconciliation(bloodBankId, body), requestId, correlationId, true);
    }

    @PostMapping("/blood-banks/{bloodBankId}/wastage")
    public ResponseEntity<Map<String, Object>> bloodBankWastage(
            @PathVariable String bloodBankId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.bloodBankWastage(bloodBankId, body), requestId, correlationId, true);
    }

    @PostMapping("/blood-banks/{bloodBankId}/returns")
    public ResponseEntity<Map<String, Object>> bloodBankReturn(
            @PathVariable String bloodBankId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.bloodBankReturn(bloodBankId, body), requestId, correlationId, true);
    }

    @GetMapping("/blood-banks/fridges")
    public ResponseEntity<Map<String, Object>> listFridges(
            @RequestParam(name = "blood_bank_id") String bloodBankId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listFridges(bloodBankId), requestId, correlationId, false);
    }

    @GetMapping("/blood-banks/fridges/{fridgeId}/readings")
    public ResponseEntity<Map<String, Object>> fridgeReadings(
            @PathVariable String fridgeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.fridgeReadings(fridgeId), requestId, correlationId, false);
    }

    @PostMapping("/blood-banks/fridges/{fridgeId}/sync-iot")
    public ResponseEntity<Map<String, Object>> syncFridgeIot(
            @PathVariable String fridgeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.syncFridgeIot(fridgeId), requestId, correlationId, false);
    }

    @GetMapping("/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "patient_cpid", required = false) String patientCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listOrders(status, patientCpid), requestId, correlationId, false);
    }

    @PostMapping("/orders")
    public ResponseEntity<Map<String, Object>> createOrder(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.createOrder(body), requestId, correlationId, true);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getOrder(orderId), requestId, correlationId, false);
    }

    @PostMapping("/orders/{orderId}/submit")
    public ResponseEntity<Map<String, Object>> submitOrder(
            @PathVariable String orderId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.submitOrder(orderId), requestId, correlationId, false);
    }

    @PostMapping("/orders/{orderId}/samples")
    public ResponseEntity<Map<String, Object>> collectSample(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.collectSample(orderId, body), requestId, correlationId, true);
    }

    @PostMapping("/orders/{orderId}/crossmatch")
    public ResponseEntity<Map<String, Object>> crossmatchOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.crossmatchOrder(orderId, body), requestId, correlationId, true);
    }

    @PostMapping("/orders/{orderId}/reserve")
    public ResponseEntity<Map<String, Object>> reserveOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.reserveOrder(orderId, body), requestId, correlationId, true);
    }

    @PostMapping("/orders/{orderId}/issue")
    public ResponseEntity<Map<String, Object>> issueOrder(
            @PathVariable String orderId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.issueOrder(orderId, body), requestId, correlationId, true);
    }

    @GetMapping("/transfusions")
    public ResponseEntity<Map<String, Object>> listTransfusions(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "patient_cpid", required = false) String patientCpid,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listTransfusions(status, patientCpid), requestId, correlationId, false);
    }

    @PostMapping("/transfusions")
    public ResponseEntity<Map<String, Object>> startTransfusion(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.startTransfusion(body), requestId, correlationId, true);
    }

    @GetMapping("/transfusions/{episodeId}")
    public ResponseEntity<Map<String, Object>> getTransfusionEpisode(
            @PathVariable String episodeId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.getTransfusionEpisode(episodeId), requestId, correlationId, false);
    }

    @PostMapping("/transfusions/{episodeId}/pre-verify")
    public ResponseEntity<Map<String, Object>> preVerifyTransfusion(
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.preVerifyTransfusion(episodeId, body), requestId, correlationId, false);
    }

    @PostMapping("/transfusions/{episodeId}/observations")
    public ResponseEntity<Map<String, Object>> transfusionObservation(
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.transfusionObservation(episodeId, body), requestId, correlationId, true);
    }

    @PostMapping("/transfusions/{episodeId}/complete")
    public ResponseEntity<Map<String, Object>> completeTransfusion(
            @PathVariable String episodeId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.completeTransfusion(episodeId, body), requestId, correlationId, false);
    }

    @PostMapping("/transfusions/{episodeId}/verify")
    public ResponseEntity<Map<String, Object>> verifyTransfusion(
            @PathVariable String episodeId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.verifyTransfusion(episodeId, body), requestId, correlationId, false);
    }

    @PostMapping("/haemovigilance/reactions")
    public ResponseEntity<Map<String, Object>> reportReaction(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.reportReaction(body), requestId, correlationId, true);
    }

    @PostMapping("/haemovigilance/cases")
    public ResponseEntity<Map<String, Object>> openHaemovigilanceCase(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.openHaemovigilanceCase(body), requestId, correlationId, true);
    }

    @PostMapping("/haemovigilance/cases/{caseId}/investigate")
    public ResponseEntity<Map<String, Object>> investigateCase(
            @PathVariable String caseId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.investigateCase(caseId, body), requestId, correlationId, false);
    }

    @PostMapping("/haemovigilance/cases/{caseId}/close")
    public ResponseEntity<Map<String, Object>> closeCase(
            @PathVariable String caseId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.closeCase(caseId, body), requestId, correlationId, false);
    }

    @GetMapping("/haemovigilance/national-dashboard")
    public ResponseEntity<Map<String, Object>> nationalHaemovigilanceDashboard(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.nationalHaemovigilanceDashboard(), requestId, correlationId, false);
    }

    @PostMapping("/drives/sync-conflicts")
    public ResponseEntity<Map<String, Object>> recordSyncConflict(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.recordSyncConflict(body), requestId, correlationId, true);
    }

    @PostMapping("/drives/sync-conflicts/{conflictId}/resolve")
    public ResponseEntity<Map<String, Object>> resolveSyncConflict(
            @PathVariable String conflictId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.resolveSyncConflict(conflictId, body), requestId, correlationId, false);
    }

    @GetMapping("/central-bank/metrics")
    public ResponseEntity<Map<String, Object>> centralBankMetrics(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.centralBankMetrics(), requestId, correlationId, false);
    }

    @GetMapping("/dashboard/forecast")
    public ResponseEntity<Map<String, Object>> dashboardForecast(
            @RequestParam(name = "facility_id", required = false) String facilityId,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.dashboardForecast(facilityId), requestId, correlationId, false);
    }

    @GetMapping("/central-bank/emergency-redistributions")
    public ResponseEntity<Map<String, Object>> listEmergencyRedistributions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.listEmergencyRedistributions(), requestId, correlationId, false);
    }

    @PostMapping("/central-bank/emergency-redistributions")
    public ResponseEntity<Map<String, Object>> requestEmergencyRedistribution(
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.requestEmergencyRedistribution(body), requestId, correlationId, true);
    }

    @PostMapping("/central-bank/emergency-redistributions/{redistributionId}/approve")
    public ResponseEntity<Map<String, Object>> approveEmergencyRedistribution(
            @PathVariable String redistributionId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.approveEmergencyRedistribution(redistributionId, body), requestId, correlationId, false);
    }

    @PostMapping("/central-bank/emergency-redistributions/{redistributionId}/handoff")
    public ResponseEntity<Map<String, Object>> initiateEmergencyHandoff(
            @PathVariable String redistributionId,
            @RequestBody(required = false) Map<String, Object> body,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.initiateEmergencyHandoff(redistributionId, body), requestId, correlationId, false);
    }

    @GetMapping("/terminology/component-pins")
    public ResponseEntity<Map<String, Object>> componentTerminologyPins(
            @RequestParam(value = "jurisdiction", required = false, defaultValue = "ZW") String jurisdiction,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return proxy(() -> client.componentTerminologyPins(jurisdiction), requestId, correlationId, false);
    }

    @FunctionalInterface
    private interface MadiCall {
        JsonNode call() throws Exception;
    }

    private ResponseEntity<Map<String, Object>> proxy(
            MadiCall call,
            String requestId,
            String correlationId,
            boolean created) {
        return proxy(call, requestId, correlationId, created, false);
    }

    private ResponseEntity<Map<String, Object>> proxy(
            MadiCall call,
            String requestId,
            String correlationId,
            boolean created,
            boolean nullOn404) {
        try {
            JsonNode body = call.call();
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("data", body != null ? body : Map.of());
            response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
            return created
                    ? ResponseEntity.status(HttpStatus.CREATED).body(response)
                    : ResponseEntity.ok(response);
        } catch (HttpClientErrorException ce) {
            // A non-donor lookup (404) is a legitimate empty state, not a failure.
            if (nullOn404 && ce.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                Map<String, Object> response = new LinkedHashMap<>();
                response.put("data", null);
                response.put("meta", Map.of("request_id", requestId, "correlation_id", correlationId));
                return ResponseEntity.ok(response);
            }
            // Client (4xx) errors are meaningful to the caller — e.g. a duplicate
            // donor (409) or a validation failure (400). Pass the upstream status
            // and message through instead of masking everything as MADI_UNAVAILABLE.
            String message = extractUpstreamMessage(ce);
            log.warn("MADI proxy client error {}: {}", ce.getStatusCode(), message);
            return ResponseEntity.status(ce.getStatusCode()).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "MADI_" + ce.getStatusCode().value(), "message", message),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        } catch (Exception e) {
            // 5xx / connection failures — the service itself is unreachable/broken.
            log.warn("MADI proxy failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "data", List.of(),
                    "error", Map.of("code", "MADI_UNAVAILABLE", "message", e.getMessage()),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }

    private static final ObjectMapper ERROR_MAPPER = new ObjectMapper();

    /** Pull the human-readable message out of an upstream madi-service error body ({"error": "..."}). */
    private String extractUpstreamMessage(HttpClientErrorException ce) {
        String body = ce.getResponseBodyAsString();
        if (body != null && !body.isBlank()) {
            try {
                JsonNode node = ERROR_MAPPER.readTree(body);
                JsonNode err = node.get("error");
                if (err != null && err.isTextual() && !err.asText().isBlank()) {
                    return err.asText();
                }
                JsonNode msg = node.get("message");
                if (msg != null && msg.isTextual() && !msg.asText().isBlank()) {
                    return msg.asText();
                }
            } catch (Exception ignored) {
                // fall through to status reason
            }
        }
        return ce.getStatusText();
    }
}
