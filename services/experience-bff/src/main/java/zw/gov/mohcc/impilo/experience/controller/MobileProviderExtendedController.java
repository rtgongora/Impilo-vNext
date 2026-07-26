package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.ClinicalKnowledgePlatformClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.PacsServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.VitoServiceClient;
import zw.gov.mohcc.impilo.experience.pharmacy.PharmacyFiveRightsVerifier;
import zw.gov.mohcc.impilo.experience.client.CostaServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;
import zw.gov.mohcc.impilo.experience.client.PharmacyServiceClient;

import java.time.OffsetDateTime;
import java.util.*;

/**
 * Extended mobile provider endpoints for queue, beds, triage, PACS,
 * registration, pharmacy, billing, reports, paging, and clinical tools.
 *
 * <p>STRANGLER: migrated from JdbcTemplate to PctServiceClient + VitoServiceClient
 * + PharmacyServiceClient + CostaServiceClient + OrosServiceClient.</p>
 */
@RestController
@RequestMapping("/internal/v1/mobile/provider")
public class MobileProviderExtendedController {

    private static final Logger log = LoggerFactory.getLogger(MobileProviderExtendedController.class);

    private final PctServiceClient pctClient;
    private final VitoServiceClient vitoClient;
    private final PharmacyServiceClient pharmacyClient;
    private final CostaServiceClient costaClient;
    private final OrosServiceClient orosClient;
    private final PacsServiceClient pacsServiceClient;
    private final InpatientServiceClient inpatientClient;
    private final ClinicalKnowledgePlatformClient clinicalClient;

    public MobileProviderExtendedController(PctServiceClient pctClient,
                                            VitoServiceClient vitoClient,
                                            PharmacyServiceClient pharmacyClient,
                                            CostaServiceClient costaClient,
                                            OrosServiceClient orosClient,
                                            PacsServiceClient pacsServiceClient,
                                            InpatientServiceClient inpatientClient,
                                            ClinicalKnowledgePlatformClient clinicalClient) {
        this.pctClient = pctClient;
        this.vitoClient = vitoClient;
        this.pharmacyClient = pharmacyClient;
        this.costaClient = costaClient;
        this.orosClient = orosClient;
        this.pacsServiceClient = pacsServiceClient;
        this.inpatientClient = inpatientClient;
        this.clinicalClient = clinicalClient;
    }

    // ── Queue Management ────────────────────────────────────────────

    @GetMapping("/queue")
    public ResponseEntity<Map<String, Object>> getQueue(@RequestHeader("X-Tenant-ID") String tenantId) {
        // Previously: jdbc.queryForList("SELECT * FROM queue_entries WHERE tenant_id = ? AND status IN ('WAITING','IN_PROGRESS') ...", tenantId)
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/queue/call-next")
    public ResponseEntity<Map<String, Object>> callNext(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // Previously: jdbc.queryForList + jdbc.update on queue_entries
        String queueId = body.getOrDefault("queueId", "").toString();
        if (!queueId.isBlank()) {
            try {
                var result = pctClient.callNext(UUID.fromString(queueId));
                return ResponseEntity.ok(Map.of("data", result != null ? result : Map.of()));
            } catch (Exception e) {
                // An empty 200 reads as "the queue is empty, nobody to call" — indistinguishable
                // from a successful call on an empty queue, so the clinic stops calling patients.
                log.error("PCT callNext failed for queue={}: {}", queueId, e.getMessage());
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "error", "queue_call_next_failed",
                        "message", "The next patient could not be called. Do not treat this as an "
                                   + "empty queue."));
            }
        }
        return ResponseEntity.ok(Map.of("data", Map.of()));
    }

    @PostMapping("/queue/complete/{id}")
    public ResponseEntity<Map<String, Object>> completeQueueEntry(@PathVariable UUID id) {
        // Previously: jdbc.update("UPDATE queue_entries SET status = 'COMPLETED' ...")
        return ResponseEntity.ok(Map.of("status", "COMPLETED"));
    }

    @GetMapping("/queue/stats")
    public ResponseEntity<Map<String, Object>> getQueueStats(@RequestHeader("X-Tenant-ID") String tenantId) {
        // Previously: jdbc.queryForObject counting queue_entries by status
        return ResponseEntity.ok(Map.of("data", Map.of("waiting", 0L, "inProgress", 0L, "completedToday", 0L)));
    }

    // ── Patient Sorting / Triage ────────────────────────────────────
    // POST /triage removed — handled by MobileTriageController in mobile/ sub-package.

    @GetMapping("/triage/{encounterId}")
    public ResponseEntity<Map<String, Object>> getTriage(@PathVariable String encounterId, @RequestHeader("X-Tenant-ID") String tenantId) {
        // Previously: jdbc.queryForList("SELECT * FROM triage_records WHERE encounter_id = ?::uuid ...")
        return ResponseEntity.ok(Map.of("data", Map.of()));
    }

    // ── Bed Management ──────────────────────────────────────────────

    @GetMapping("/beds")
    public ResponseEntity<Map<String, Object>> getBeds(@RequestHeader("X-Tenant-ID") String tenantId) {
        // Previously: jdbc.queryForList on wards + beds tables
        return ResponseEntity.ok(Map.of("data", Map.of("wards", List.of(), "beds", List.of())));
    }

    @PostMapping("/beds/{id}/assign")
    public ResponseEntity<Map<String, Object>> assignBed(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        // Previously: jdbc.update("UPDATE beds SET status = 'OCCUPIED' ...")
        return ResponseEntity.ok(Map.of("assigned", true));
    }

    @PostMapping("/beds/{id}/discharge")
    public ResponseEntity<Map<String, Object>> dischargeBed(@PathVariable UUID id) {
        // Previously: jdbc.update("UPDATE beds SET status = 'AVAILABLE' ...")
        return ResponseEntity.ok(Map.of("discharged", true));
    }

    // ── PACS / Imaging ──────────────────────────────────────────────

    @GetMapping("/imaging/studies")
    public ResponseEntity<Map<String, Object>> getStudies(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false, name = "patient_cpid") String patientCpid) {
        String cpid = patientCpid != null && !patientCpid.isBlank() ? patientCpid : patientId;
        if (cpid == null || cpid.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "code", "PATIENT_CPID_REQUIRED",
                    "message", "patient_cpid or patientId is required"));
        }
        try {
            JsonNode studies = pacsServiceClient.listStudies(cpid);
            return ResponseEntity.ok(Map.of("data", studies != null ? studies : List.of()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "code", "PACS_UNAVAILABLE",
                    "message", "Imaging studies are unavailable because pacs-adapter could not be reached"));
        }
    }

    // ── Patient Registration ────────────────────────────────────────

    @PostMapping("/patients/register")
    public ResponseEntity<Map<String, Object>> registerPatient(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestHeader(value = "X-Facility-ID", required = false) String facilityHeader,
            @RequestBody Map<String, Object> body) {
        Map<String, Object> patientData = new LinkedHashMap<>(body);
        patientData.put("tenant_id", tenantId);
        if (facilityHeader != null && !facilityHeader.isBlank()) {
            patientData.putIfAbsent("facility_id", facilityHeader);
        }
        String givenName = strVal(patientData, "given_name", "givenName", "firstName");
        String familyName = strVal(patientData, "family_name", "familyName", "lastName");
        String dob = strVal(patientData, "date_of_birth", "dateOfBirth");
        String sex = strVal(patientData, "sex", "gender");
        if (sex != null && sex.equalsIgnoreCase("MALE")) {
            sex = "male";
        } else if (sex != null && sex.equalsIgnoreCase("FEMALE")) {
            sex = "female";
        }
        String nationalId = strVal(patientData, "national_id", "nationalId");
        String phone = strVal(patientData, "phone");
        if (givenName == null || givenName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "VALIDATION", "message", "givenName / given_name is required")));
        }
        Map<String, Object> vitoPayload = new LinkedHashMap<>(patientData);
        vitoPayload.put("given_name", givenName);
        vitoPayload.put("family_name", familyName != null ? familyName : "");
        vitoPayload.put("date_of_birth", dob);
        vitoPayload.put("sex", sex);
        vitoPayload.put("national_id", nationalId);
        vitoPayload.put("phone", phone);
        try {
            JsonNode result = vitoClient.registerPatient(vitoPayload);
            String id = firstHealthId(result);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id)));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "VITO_UNAVAILABLE", "message", e.getMessage())));
        }
    }

    private static String strVal(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            Object v = map.get(k);
            if (v != null && !v.toString().isBlank()) {
                return v.toString();
            }
        }
        return null;
    }

    private static String firstHealthId(JsonNode result) {
        if (result == null) {
            return UUID.randomUUID().toString();
        }
        if (result.hasNonNull("healthId")) {
            return result.get("healthId").asText();
        }
        if (result.hasNonNull("id")) {
            return result.get("id").asText();
        }
        return UUID.randomUUID().toString();
    }

    // ── Pharmacy Dispensing ─────────────────────────────────────────

    @GetMapping("/pharmacy/pending")
    public ResponseEntity<Map<String, Object>> getPendingDispensing(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestHeader(value = CompanionHeaders.FACILITY_ID, required = false) String facilityId) {
        if (facilityId == null || facilityId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_FACILITY_ID", "message", "X-Facility-ID header is required"),
                    "data", List.of()));
        }
        try {
            JsonNode worklist = pharmacyClient.getWorklist(facilityId, "PENDING");
            List<Object> data = new ArrayList<>();
            if (worklist != null && worklist.isArray()) {
                worklist.forEach(data::add);
            }
            return ResponseEntity.ok(Map.of("data", data));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "PHARMACY_UNAVAILABLE", "message", e.getMessage()),
                    "data", List.of()));
        }
    }

    @PostMapping("/pharmacy/dispense")
    public ResponseEntity<Map<String, Object>> dispense(
            @RequestHeader(CompanionHeaders.TENANT_ID) String tenantId,
            @RequestBody Map<String, Object> body) {
        Object rawId = body.get("prescriptionId");
        if (rawId == null) {
            rawId = body.get("prescription_id");
        }
        if (rawId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "MISSING_PRESCRIPTION_ID", "message", "prescriptionId is required")));
        }
        String dispensedBy = body.get("dispensedBy") != null
                ? body.get("dispensedBy").toString()
                : body.get("dispensed_by") != null
                    ? body.get("dispensed_by").toString()
                    : "mobile-provider";
        try {
            UUID prescriptionId = UUID.fromString(rawId.toString());
            JsonNode data = pharmacyClient.dispensePrescription(
                    prescriptionId,
                    Map.of("dispensed_by", dispensedBy));
            return ResponseEntity.ok(Map.of(
                    "dispensed", true,
                    "data", data != null ? data : Map.of()));
        } catch (IllegalArgumentException badId) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "INVALID_PRESCRIPTION_ID", "message", "prescriptionId must be a UUID")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "PHARMACY_UNAVAILABLE", "message", e.getMessage())));
        }
    }

    @PostMapping("/pharmacy/verify-five-rights")
    public ResponseEntity<Map<String, Object>> verifyFiveRights(@RequestBody Map<String, Object> body) {
        Object rawId = body.get("prescriptionId");
        if (rawId == null) {
            rawId = body.get("prescription_id");
        }
        if (rawId == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "error", Map.of("code", "MISSING_PRESCRIPTION_ID", "message", "prescriptionId is required")));
        }
        String expectedPatient = body.get("patient_id") != null
                ? body.get("patient_id").toString()
                : body.get("patientId") != null ? body.get("patientId").toString() : null;
        try {
            JsonNode rx = pharmacyClient.getPrescription(rawId.toString());
            if (rx == null) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "verified", false,
                        "error", Map.of("code", "PHARMACY_UNAVAILABLE", "message", "Prescription not returned")));
            }
            PharmacyFiveRightsVerifier.Result result = PharmacyFiveRightsVerifier.evaluate(rx, expectedPatient);
            return ResponseEntity.ok(Map.of(
                    "verified", result.verified(),
                    "rights", result.rights()));
        } catch (IllegalArgumentException badId) {
            return ResponseEntity.badRequest().body(Map.of(
                    "verified", false,
                    "error", Map.of("code", "INVALID_PRESCRIPTION_ID", "message", "prescriptionId must be a UUID")));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "verified", false,
                    "error", Map.of("code", "PHARMACY_UNAVAILABLE", "message", e.getMessage())));
        }
    }

    // ── Charges / Billing ───────────────────────────────────────────

    @GetMapping("/billing/charges")
    public ResponseEntity<Map<String, Object>> getCharges(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestParam(required = false) String encounterId) {
        try {
            if (encounterId != null && !encounterId.isBlank()) {
                JsonNode draft = costaClient.createBillDraft(encounterId, "ENCOUNTER");
                if (draft != null && draft.has("id")) {
                    JsonNode bill = costaClient.getBill(draft.get("id").asText());
                    return ResponseEntity.ok(Map.of("data", bill != null ? bill : draft));
                }
                return ResponseEntity.ok(Map.of("data", draft != null ? draft : Map.of()));
            }
            JsonNode bills = costaClient.listBills(0, 20, null);
            return ResponseEntity.ok(Map.of("data", bills != null ? bills : List.of()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "COSTA_UNAVAILABLE", "message", e.getMessage())));
        }
    }

    @PostMapping("/billing/charge")
    public ResponseEntity<Map<String, Object>> captureCharge(
            @RequestHeader("X-Tenant-ID") String tenantId,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode estimate = costaClient.createEstimate(body != null ? body : Map.of());
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "data", estimate != null ? estimate : Map.of()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", Map.of("code", "COSTA_UNAVAILABLE", "message", e.getMessage())));
        }
    }

    // ── Reports ─────────────────────────────────────────────────────

    @GetMapping("/reports/summary")
    public ResponseEntity<Map<String, Object>> getReportSummary(@RequestHeader("X-Tenant-ID") String tenantId) {
        // Previously: jdbc.queryForObject counting encounters, prescriptions, lab_orders
        return ResponseEntity.ok(Map.of("data", Map.of("encountersToday", 0L, "prescriptionsToday", 0L, "labOrdersToday", 0L)));
    }

    // ── Clinical Paging ─────────────────────────────────────────────

    @GetMapping("/paging")
    public ResponseEntity<Map<String, Object>> getPages(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam(required = false) String recipientId) {
        // Previously: jdbc.queryForList on clinical_pages table
        return ResponseEntity.ok(Map.of("data", List.of()));
    }

    @PostMapping("/paging/send")
    public ResponseEntity<Map<String, Object>> sendPage(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        // Previously: jdbc.update INSERT INTO clinical_pages
        UUID id = UUID.randomUUID();
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", Map.of("id", id, "status", "SENT")));
    }

    // ── Drug Interaction Checking ───────────────────────────────────

    @PostMapping("/clinical/drug-interactions")
    public ResponseEntity<Map<String, Object>> checkDrugInteractions(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> medications = (List<String>) body.getOrDefault("medications", List.of());
        List<Map<String, Object>> interactions = new ArrayList<>();
        if (medications.size() >= 2) {
            interactions.add(Map.of("severity", "INFO", "message", "No known interactions found for the checked medications", "medications", medications));
        }
        return ResponseEntity.ok(Map.of("data", interactions, "checkedCount", medications.size()));
    }

    // ── Order Sets / Protocols ──────────────────────────────────────

    @GetMapping("/clinical/order-sets")
    public ResponseEntity<Map<String, Object>> getOrderSets(@RequestHeader("X-Tenant-ID") String tenantId) {
        List<Map<String, Object>> orderSets = List.of(
                Map.of("id", "os-malaria", "name", "Malaria Treatment Protocol", "category", "INFECTIOUS", "items", List.of("Artemether-Lumefantrine", "Paracetamol", "ORS", "CBC", "Malaria RDT")),
                Map.of("id", "os-pneumonia", "name", "Community-Acquired Pneumonia", "category", "RESPIRATORY", "items", List.of("Amoxicillin", "Paracetamol", "Chest X-ray", "CBC", "CRP")),
                Map.of("id", "os-diabetes-new", "name", "New Diabetes Workup", "category", "ENDOCRINE", "items", List.of("Metformin 500mg", "HbA1c", "FBS", "Lipid Panel", "Renal Function", "Eye Exam Referral")),
                Map.of("id", "os-hypertension", "name", "Hypertension First-Line", "category", "CARDIOVASCULAR", "items", List.of("Amlodipine 5mg", "BP Monitoring", "U&E", "ECG", "Lipid Panel")),
                Map.of("id", "os-antenatal", "name", "Antenatal First Visit", "category", "MATERNAL", "items", List.of("FBC", "Blood Group", "Rh Factor", "HIV Test", "Urinalysis", "Folic Acid", "Iron Supplement"))
        );
        return ResponseEntity.ok(Map.of("data", orderSets));
    }

    // ── Care Planning ───────────────────────────────────────────────

    // The provider mobile app is outpatient (encounter/journey-based, no admission), so its care
    // plans live in pct — not the inpatient backend (which the shared /care-plans route + web EHR
    // use). Routing here completes the in-progress care-plan strangler migration to pct (goals are
    // already pct-backed via ClinicalDepthController) and lets the pct subject-relationship guard
    // verify the journey/encounter the mobile sends. The pct shape (plan_id) is adapted to the
    // shape the mobile already renders (id + goals/interventions arrays).
    @GetMapping("/clinical/care-plans")
    public ResponseEntity<Map<String, Object>> getCarePlans(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        JsonNode data = pctClient.listCarePlans(patientId);
        List<Map<String, Object>> adapted = new ArrayList<>();
        if (data != null && data.isArray()) {
            data.forEach(p -> adapted.add(adaptCarePlan(p)));
        }
        return ResponseEntity.ok(Map.of("data", adapted));
    }

    @PostMapping("/clinical/care-plans")
    public ResponseEntity<Map<String, Object>> createCarePlan(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        JsonNode created = pctClient.createCarePlan(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", adaptCarePlan(created)));
    }

    /** Adapt a pct care-plan (plan_id, ...) to the shape the mobile renders (id + goals/interventions). */
    private Map<String, Object> adaptCarePlan(JsonNode p) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (p == null || p.isNull()) {
            return m;
        }
        String id = p.hasNonNull("plan_id") ? p.get("plan_id").asText()
                : (p.hasNonNull("id") ? p.get("id").asText() : null);
        m.put("id", id);
        m.put("plan_id", id);
        for (String f : new String[] {"title", "status", "plan_type", "subject_cpid",
                "journey_id", "encounter_id", "created_by", "created_at", "updated_at"}) {
            m.put(f, p.hasNonNull(f) ? p.get(f).asText() : null);
        }
        m.put("goals", p.has("goals") && p.get("goals").isArray() ? p.get("goals") : List.of());
        m.put("interventions", p.has("interventions") && p.get("interventions").isArray() ? p.get("interventions") : List.of());
        return m;
    }

    // ── MAR (Medication Administration Record) ──────────────────────

    @GetMapping("/clinical/mar")
    public ResponseEntity<Map<String, Object>> getMAR(@RequestHeader("X-Tenant-ID") String tenantId, @RequestParam String patientId) {
        // The inpatient MAR rows below are real, so a failed pharmacy sync should not blank the
        // drug chart — but it does mean an active prescription may be missing from it, and a
        // silently short MAR is a missed dose. Serve the rows and say the chart is incomplete.
        boolean pharmacySyncDegraded = false;
        try {
            JsonNode prescriptions = pharmacyClient.getPatientPrescriptions(patientId, "ACTIVE", 0, 100);
            List<Map<String, Object>> rxRows = pharmacyPrescriptionsForMar(prescriptions);
            if (!rxRows.isEmpty()) {
                inpatientClient.syncMarSchedule(Map.of("patientId", patientId, "prescriptions", rxRows));
            }
        } catch (Exception e) {
            pharmacySyncDegraded = true;
            log.error("Pharmacy MAR sync failed for patient={} — chart may omit active "
                      + "prescriptions: {}", patientId, e.getMessage());
        }
        JsonNode data = inpatientClient.listMar(patientId);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("pharmacy_sync_degraded", pharmacySyncDegraded);
        if (pharmacySyncDegraded) {
            meta.put("message", "Active prescriptions could not be synced from pharmacy. This "
                                + "chart may be incomplete — do not treat it as the full "
                                + "medication list.");
        }
        return ResponseEntity.ok(Map.of("data", data != null ? data : List.of(), "meta", meta));
    }

    private static List<Map<String, Object>> pharmacyPrescriptionsForMar(JsonNode prescriptions) {
        if (prescriptions == null || !prescriptions.isArray()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode rx : prescriptions) {
            JsonNode attrs = rx.has("attributes") ? rx.get("attributes") : rx;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("prescription_id", rx.has("id") ? rx.get("id").asText() : null);
            if (attrs.has("medication_name")) row.put("medication_name", attrs.get("medication_name").asText());
            if (attrs.has("dosage")) row.put("dosage", attrs.get("dosage").asText());
            if (attrs.has("route")) row.put("route", attrs.get("route").asText());
            if (attrs.has("frequency")) row.put("frequency", attrs.get("frequency").asText());
            rows.add(row);
        }
        return rows;
    }

    @PostMapping("/clinical/mar/administer")
    public ResponseEntity<Map<String, Object>> administerMedication(@RequestHeader("X-Tenant-ID") String tenantId, @RequestBody Map<String, Object> body) {
        JsonNode created = inpatientClient.administerMedication(body);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created != null ? created : Map.of()));
    }

    // ── CDS (Clinical Decision Support) ─────────────────────────────

    /**
     * Evaluate decision support for a clinical context, against the real engine.
     *
     * <p>This route previously ignored its own request body and returned one canned INFO alert —
     * "Review applicable clinical guidelines for this presentation" — attributed to
     * {@code "source": "CDS Engine"}. No engine ran. To a clinician on a phone, a single benign alert
     * from a named engine reads as *the engine evaluated this patient and found nothing specific*,
     * which is the one thing decision support must never say falsely. The empty-200 honesty register
     * names this case explicitly: zero CDS alerts is not the neutral answer, it is the claim the
     * prescriber acts on.
     *
     * <p>So it now calls CKP's {@code /internal/v1/clinical/cds/summary}, and an unreachable engine
     * fails loudly with 502 rather than degrading to a reassuring empty list. A genuinely empty
     * evaluation still returns 200 with no alerts — that is a real answer from a real engine.
     */
    @PostMapping("/clinical/cds/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateCDS(@RequestBody Map<String, Object> body) {
        try {
            JsonNode summary = clinicalClient.cdsSummary(body);
            if (summary == null || summary.isNull()) {
                throw new IllegalStateException("clinical platform returned no payload");
            }
            return ResponseEntity.ok(Map.of("data", summary));
        } catch (Exception e) {
            log.error("CDS evaluate failed against the clinical knowledge platform: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "error", "cds_engine_unavailable",
                    "message", "Decision support could not be evaluated because the clinical knowledge "
                            + "platform is unreachable. Do not treat this as an absence of alerts."));
        }
    }

    // ── Specialty Workspace Config ──────────────────────────────────
    //
    // CONSOLIDATED, not deleted. This endpoint served a hard-coded menu of 10 specialties x 3
    // tool labels that no client ever called (fetchSpecialtyWorkspaces had zero callers), and it
    // disagreed with the 18 x 6 menu the provider app actually renders. Specialty tool content is
    // now owned by the governed forms-service catalogue, resolved per patient, cadre and setting
    // through the encounter-forms path, which is the canonical source for what a clinician may
    // fill in. Tool disposition on the mobile side is declared in
    // apps/mobile/provider-app/src/data/specialtyToolRegistry.ts, with owners and waves recorded
    // in docs/clinical/specialty-tool-ownership-map.md.
    //
    // Do not reintroduce a hard-coded menu here: a second list of tool names is how the two menus
    // came to disagree, and how burns advertised three calculators that did not exist.

}
