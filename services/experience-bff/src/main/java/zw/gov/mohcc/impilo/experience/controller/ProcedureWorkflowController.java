package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.InpatientServiceClient;
import zw.gov.mohcc.impilo.experience.client.InventoryServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.OrosServiceClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Perioperative procedure episode workflow — booking through post-procedure recovery.
 * Sovereign state in inpatient-service; BFF composes OROS preop orders on create.
 */
@RestController
@RequestMapping("/internal/v1/procedures")
public class ProcedureWorkflowController {

    private static final Logger log = LoggerFactory.getLogger(ProcedureWorkflowController.class);
    private static final String PROCEDURE_CONSENT_TEMPLATE_ID = "11111111-1111-1111-1111-111111111105";

    private final InpatientServiceClient inpatientClient;
    private final OrosServiceClient orosClient;
    private final MvumoServiceClient mvumoClient;
    private final DocumentServiceClient documentClient;
    private final InventoryServiceClient inventoryClient;
    private final ObjectMapper objectMapper;

    public ProcedureWorkflowController(InpatientServiceClient inpatientClient,
                                       OrosServiceClient orosClient,
                                       MvumoServiceClient mvumoClient,
                                       DocumentServiceClient documentClient,
                                       InventoryServiceClient inventoryClient,
                                       ObjectMapper objectMapper) {
        this.inpatientClient = inpatientClient;
        this.orosClient = orosClient;
        this.mvumoClient = mvumoClient;
        this.documentClient = documentClient;
        this.inventoryClient = inventoryClient;
        this.objectMapper = objectMapper;
    }

    private static JsonNode requirePayload(JsonNode node, String operation) {
        if (node == null || node.isNull()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + ": upstream returned no payload");
        }
        return node;
    }

    private static ResponseStatusException upstreamFailure(String operation, Exception cause) {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, operation + " failed", cause);
    }

    @PostMapping("/episodes")
    public ResponseEntity<Map<String, Object>> createEpisode(@RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.createProcedureEpisode(body), "Inpatient createProcedureEpisode");
            maybePlacePreopOrders(body, created);
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient createProcedureEpisode", e);
        }
    }

    @GetMapping("/episodes")
    public ResponseEntity<Map<String, Object>> listEpisodes(@RequestParam String patientId) {
        try {
            JsonNode data = requirePayload(inpatientClient.listProcedureEpisodes(patientId), "Inpatient listProcedureEpisodes");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient listProcedureEpisodes", e);
        }
    }

    @GetMapping("/episodes/{id}")
    public ResponseEntity<Map<String, Object>> getEpisode(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(inpatientClient.getProcedureEpisode(id.toString()), "Inpatient getProcedureEpisode");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient getProcedureEpisode", e);
        }
    }

    @PostMapping("/episodes/{id}/preop")
    public ResponseEntity<Map<String, Object>> submitPreop(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.submitProcedurePreop(id.toString(), body), "Inpatient submitProcedurePreop");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient submitProcedurePreop", e);
        }
    }

    @PostMapping("/episodes/{id}/checklist/{phase}")
    public ResponseEntity<Map<String, Object>> completeChecklist(
            @PathVariable UUID id, @PathVariable String phase, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(
                    inpatientClient.completeProcedureChecklist(id.toString(), phase, body),
                    "Inpatient completeProcedureChecklist");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient completeProcedureChecklist", e);
        }
    }

    @PostMapping("/episodes/{id}/start")
    public ResponseEntity<Map<String, Object>> startProcedure(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.startProcedureEpisode(id.toString(), body), "Inpatient startProcedure");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient startProcedure", e);
        }
    }

    @PostMapping("/episodes/{id}/intraop/events")
    public ResponseEntity<Map<String, Object>> recordIntraop(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode created = requirePayload(inpatientClient.recordProcedureIntraop(id.toString(), body), "Inpatient recordIntraop");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", created));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient recordIntraop", e);
        }
    }

    @PostMapping("/episodes/{id}/pacu")
    public ResponseEntity<Map<String, Object>> enterPacu(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.enterProcedurePacu(id.toString(), body), "Inpatient enterPacu");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient enterPacu", e);
        }
    }

    @PostMapping("/episodes/{id}/postop")
    public ResponseEntity<Map<String, Object>> completePostop(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.completeProcedurePostop(id.toString(), body), "Inpatient completePostop");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient completePostop", e);
        }
    }

    @PostMapping("/episodes/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeEpisode(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.completeProcedureEpisode(id.toString(), body), "Inpatient completeEpisode");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Inpatient completeEpisode", e);
        }
    }

    @PostMapping("/episodes/{id}/consent")
    public ResponseEntity<Map<String, Object>> requestConsent(@PathVariable UUID id) {
        try {
            JsonNode episode = requirePayload(inpatientClient.getProcedureEpisode(id.toString()), "Inpatient getProcedureEpisode");
            JsonNode existing = inpatientClient.getProcedureConsentStatus(id.toString());
            if (existing != null && existing.has("mvumo_consent_request_id")
                    && !existing.get("mvumo_consent_request_id").isNull()) {
                return ResponseEntity.ok(Map.of("data", existing));
            }
            String patientRef = "Patient/" + episode.path("patient_id").asText();
            Map<String, Object> consentRequest = new LinkedHashMap<>();
            consentRequest.put("subjectPatientRef", patientRef);
            consentRequest.put("consentType", "CLINICAL_PROCEDURE");
            consentRequest.put("templateId", PROCEDURE_CONSENT_TEMPLATE_ID);
            consentRequest.put("workflowRef", "procedure:" + id);
            if (episode.has("encounter_id") && !episode.get("encounter_id").isNull()) {
                consentRequest.put("encounterRef", episode.get("encounter_id").asText());
            }
            consentRequest.put("context", Map.of(
                    "episodeId", id.toString(),
                    "procedureName", episode.path("procedure_name").asText("procedure")));
            JsonNode mvumo = requirePayload(mvumoClient.createConsentRequest(consentRequest), "MVUMO createConsentRequest");
            Map<String, Object> bind = new LinkedHashMap<>();
            bind.put("mvumoConsentRequestId", mvumo.path("id").asText());
            bind.put("consentStatus", mvumo.path("state").asText("PENDING"));
            if (mvumo.has("tshepoConsentId") && !mvumo.get("tshepoConsentId").isNull()) {
                bind.put("tshepoConsentId", mvumo.get("tshepoConsentId").asText());
            }
            if (mvumo.has("proofRef") && !mvumo.get("proofRef").isNull()) {
                bind.put("consentProofRef", mvumo.get("proofRef").asText());
            }
            JsonNode data = requirePayload(inpatientClient.bindProcedureConsentEvidence(id.toString(), bind),
                    "Inpatient bindProcedureConsentEvidence");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consent request", e);
        }
    }

    @GetMapping("/episodes/{id}/consent")
    public ResponseEntity<Map<String, Object>> getConsent(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(inpatientClient.getProcedureConsentStatus(id.toString()),
                    "Inpatient getProcedureConsentStatus");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consent status", e);
        }
    }

    @PostMapping("/episodes/{id}/consent/sync")
    public ResponseEntity<Map<String, Object>> syncConsent(@PathVariable UUID id) {
        try {
            JsonNode data = syncMvumoConsentToEpisode(id);
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consent sync", e);
        }
    }

    @GetMapping("/episodes/{id}/consent/live")
    public ResponseEntity<Map<String, Object>> liveConsent(@PathVariable UUID id) {
        try {
            String mvumoId = requireMvumoConsentRequestId(id);
            JsonNode mvumo = requirePayload(mvumoClient.getConsentRequest(mvumoId), "MVUMO getConsentRequest");
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("mvumo", mvumo);
            envelope.put("episode", inpatientClient.getProcedureConsentStatus(id.toString()));
            return ResponseEntity.ok(Map.of("data", envelope));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure live consent", e);
        }
    }

    @PostMapping("/episodes/{id}/consent/explanation")
    public ResponseEntity<Map<String, Object>> provideConsentExplanation(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return consentLifecycleStep(id, "explanation", body, mvumoClient::provideConsentExplanation);
    }

    @PostMapping("/episodes/{id}/consent/verify-identity")
    public ResponseEntity<Map<String, Object>> verifyConsentIdentity(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return consentLifecycleStep(id, "verify-identity", body, mvumoClient::verifyConsentIdentity);
    }

    @PostMapping("/episodes/{id}/consent/grant")
    public ResponseEntity<Map<String, Object>> grantConsent(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return consentLifecycleStep(id, "grant", body, mvumoClient::grantConsentRequest);
    }

    @PostMapping("/episodes/{id}/consent/refuse")
    public ResponseEntity<Map<String, Object>> refuseConsent(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        return consentLifecycleStep(id, "refuse", body, mvumoClient::refuseConsentRequest);
    }

    @PostMapping("/episodes/{id}/consent/remote-session")
    public ResponseEntity<Map<String, Object>> createConsentRemoteSession(
            @PathVariable UUID id, @RequestBody(required = false) Map<String, Object> body) {
        try {
            String mvumoId = requireMvumoConsentRequestId(id);
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("consentRequestId", mvumoId);
            payload.put("channel", body != null && body.get("channel") != null ? body.get("channel") : "TOKEN_LINK");
            if (body != null && body.get("ttlMinutes") != null) {
                payload.put("ttlMinutes", body.get("ttlMinutes"));
            }
            JsonNode session = requirePayload(mvumoClient.createRemoteSession(payload), "MVUMO createRemoteSession");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", session));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consent remote session", e);
        }
    }

    @PostMapping("/episodes/{id}/consent/remote-session/{sessionId}/verify")
    public ResponseEntity<Map<String, Object>> verifyConsentRemoteSession(
            @PathVariable UUID id, @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            requireMvumoConsentRequestId(id);
            JsonNode verified = requirePayload(
                    mvumoClient.verifyRemoteSession(sessionId, body != null ? body : Map.of()),
                    "MVUMO verifyRemoteSession");
            JsonNode synced = syncMvumoConsentToEpisode(id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("session", verified);
            data.put("episode", synced);
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure remote session verify", e);
        }
    }

    @PostMapping("/episodes/{id}/consent/remote-session/{sessionId}/grant")
    public ResponseEntity<Map<String, Object>> grantConsentRemoteSession(
            @PathVariable UUID id, @PathVariable String sessionId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            requireMvumoConsentRequestId(id);
            JsonNode granted = requirePayload(
                    mvumoClient.grantRemoteSession(sessionId, body != null ? body : Map.of()),
                    "MVUMO grantRemoteSession");
            JsonNode synced = syncMvumoConsentToEpisode(id);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("session", granted);
            data.put("episode", synced);
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure remote session grant", e);
        }
    }

    @FunctionalInterface
    private interface MvumoConsentMutator {
        JsonNode apply(String consentRequestId, Map<String, Object> body);
    }

    private ResponseEntity<Map<String, Object>> consentLifecycleStep(
            UUID episodeId, String step, Map<String, Object> body, MvumoConsentMutator mutator) {
        try {
            String mvumoId = requireMvumoConsentRequestId(episodeId);
            Map<String, Object> mvumoBody = buildConsentStepPayload(step, body);
            JsonNode mvumo = requirePayload(mutator.apply(mvumoId, mvumoBody), "MVUMO " + step);
            JsonNode synced = bindMvumoViewToEpisode(episodeId, mvumoId, mvumo);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("mvumo", mvumo);
            data.put("episode", synced);
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consent " + step, e);
        }
    }

    private String requireMvumoConsentRequestId(UUID episodeId) {
        JsonNode status = requirePayload(inpatientClient.getProcedureConsentStatus(episodeId.toString()),
                "Inpatient getProcedureConsentStatus");
        String mvumoId = status.path("mvumo_consent_request_id").asText(null);
        if (mvumoId == null || mvumoId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No MVUMO consent request linked — call POST /consent first");
        }
        return mvumoId;
    }

    private JsonNode syncMvumoConsentToEpisode(UUID episodeId) throws Exception {
        String mvumoId = requireMvumoConsentRequestId(episodeId);
        JsonNode mvumo = requirePayload(mvumoClient.getConsentRequest(mvumoId), "MVUMO getConsentRequest");
        return bindMvumoViewToEpisode(episodeId, mvumoId, mvumo);
    }

    private JsonNode bindMvumoViewToEpisode(UUID episodeId, String mvumoId, JsonNode mvumo) {
        Map<String, Object> bind = new LinkedHashMap<>();
        bind.put("mvumoConsentRequestId", mvumoId);
        bind.put("consentStatus", mvumo.path("state").asText("PENDING"));
        if (mvumo.has("tshepoConsentId") && !mvumo.get("tshepoConsentId").isNull()) {
            bind.put("tshepoConsentId", mvumo.get("tshepoConsentId").asText());
        }
        if (mvumo.has("proofRef") && !mvumo.get("proofRef").isNull()) {
            bind.put("consentProofRef", mvumo.get("proofRef").asText());
        } else if ("GRANTED".equals(mvumo.path("state").asText())) {
            JsonNode proof = mvumoClient.getConsentProof(mvumoId);
            if (proof != null && proof.has("proofRef")) {
                bind.put("consentProofRef", proof.get("proofRef").asText());
            }
        }
        return requirePayload(inpatientClient.bindProcedureConsentEvidence(episodeId.toString(), bind),
                "Inpatient bindProcedureConsentEvidence");
    }

    private static Map<String, Object> buildConsentStepPayload(String step, Map<String, Object> body) {
        Map<String, Object> payload = new LinkedHashMap<>(body != null ? body : Map.of());
        if ("explanation".equals(step)) {
            payload.putIfAbsent("actualMethod", "FACILITY_TABLET");
            payload.putIfAbsent("explanationNotes", "Procedure risks, benefits, and alternatives explained to patient.");
        } else if ("verify-identity".equals(step)) {
            payload.putIfAbsent("actualMethod", "FACILITY_WITNESSED");
            payload.putIfAbsent("identityVerified", true);
        } else if ("grant".equals(step)) {
            payload.putIfAbsent("actualMethod", "FACILITY_WITNESSED");
            payload.putIfAbsent("actualAssurance", "L3_WITNESSED_HIGH_ASSURANCE");
            payload.putIfAbsent("grantedByRef", "Provider/theatre-clinician");
        }
        return payload;
    }

    @PostMapping(value = "/episodes/{id}/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadOperativeDocument(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "OPERATIVE_NOTE") String documentType,
            @RequestParam(required = false) String title) throws Exception {
        try {
            JsonNode episode = requirePayload(inpatientClient.getProcedureEpisode(id.toString()), "Inpatient getProcedureEpisode");
            Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("episode_id", id.toString());
            metadata.put("patient_id", episode.path("patient_id").asText());
            metadata.put("document_type", documentType);
            if (episode.has("encounter_id") && !episode.get("encounter_id").isNull()) {
                metadata.put("encounter_id", episode.get("encounter_id").asText());
            }
            JsonNode stored = requirePayload(
                    documentClient.uploadObject(
                            file.getBytes(),
                            file.getOriginalFilename(),
                            file.getContentType() != null ? file.getContentType() : MediaType.APPLICATION_OCTET_STREAM_VALUE,
                            metadata),
                    "DocumentService uploadObject");
            Map<String, Object> link = new LinkedHashMap<>();
            link.put("documentObjectId", stored.path("id").asText(stored.path("objectId").asText()));
            link.put("documentType", documentType);
            link.put("title", title != null ? title : file.getOriginalFilename());
            if (stored.has("storageKey")) {
                link.put("storageKey", stored.get("storageKey").asText());
            }
            JsonNode linked = requirePayload(inpatientClient.linkProcedureDocument(id.toString(), link),
                    "Inpatient linkProcedureDocument");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", linked));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure document upload", e);
        }
    }

    @GetMapping("/episodes/{id}/documents")
    public ResponseEntity<Map<String, Object>> listDocuments(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(inpatientClient.listProcedureDocuments(id.toString()),
                    "Inpatient listProcedureDocuments");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure documents list", e);
        }
    }

    @PostMapping("/episodes/{id}/consumables")
    public ResponseEntity<Map<String, Object>> recordConsumable(@PathVariable UUID id,
                                                                @RequestBody Map<String, Object> body) {
        try {
            ObjectNode consumption = objectMapper.createObjectNode();
            consumption.put("facilityId", String.valueOf(body.get("facilityId")));
            consumption.put("storeId", String.valueOf(body.get("storeId")));
            consumption.put("itemCode", String.valueOf(body.get("itemCode")));
            consumption.put("qty", body.get("quantity") instanceof Number n ? n.intValue()
                    : Integer.parseInt(String.valueOf(body.get("quantity"))));
            consumption.put("refType", "PROCEDURE");
            consumption.put("orderId", id.toString());
            JsonNode ledger = inventoryClient.postClinicalConsumption(consumption);
            String ledgerRef = ledger != null && ledger.has("data") && ledger.get("data").has("id")
                    ? ledger.get("data").get("id").asText()
                    : (ledger != null && ledger.has("id") ? ledger.get("id").asText() : null);
            Map<String, Object> record = new LinkedHashMap<>(body);
            if (ledgerRef != null) {
                record.put("inventoryLedgerRef", ledgerRef);
            }
            JsonNode data = requirePayload(inpatientClient.recordProcedureConsumable(id.toString(), record),
                    "Inpatient recordProcedureConsumable");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consumable", e);
        }
    }

    @GetMapping("/episodes/{id}/consumables")
    public ResponseEntity<Map<String, Object>> listConsumables(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(inpatientClient.listProcedureConsumables(id.toString()),
                    "Inpatient listProcedureConsumables");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Procedure consumables list", e);
        }
    }

    @GetMapping("/episodes/{id}/anaesthesia/score-suggestions")
    public ResponseEntity<Map<String, Object>> anaesthesiaScoreSuggestions(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(inpatientClient.getAnaesthesiaScoreSuggestions(id.toString()),
                    "Inpatient anaesthesia score suggestions");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Anaesthesia score suggestions", e);
        }
    }

    @GetMapping("/episodes/{id}/anaesthesia/scores")
    public ResponseEntity<Map<String, Object>> listAnaesthesiaScores(@PathVariable UUID id) {
        try {
            JsonNode data = requirePayload(inpatientClient.listAnaesthesiaScores(id.toString()),
                    "Inpatient list anaesthesia scores");
            return ResponseEntity.ok(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Anaesthesia scores list", e);
        }
    }

    @PostMapping("/episodes/{id}/anaesthesia/scores")
    public ResponseEntity<Map<String, Object>> recordAnaesthesiaScore(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.recordAnaesthesiaScore(id.toString(), body),
                    "Inpatient record anaesthesia score");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Record anaesthesia score", e);
        }
    }

    @PostMapping("/episodes/{id}/intraop/vitals")
    public ResponseEntity<Map<String, Object>> recordIntraopVitals(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        try {
            JsonNode data = requirePayload(inpatientClient.recordIntraopVitals(id.toString(), body),
                    "Inpatient record intraop vitals");
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("data", data));
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw upstreamFailure("Record intraop vitals", e);
        }
    }

    private void maybePlacePreopOrders(Map<String, Object> body, JsonNode created) {
        if (!Boolean.TRUE.equals(body.get("placePreopOrders"))) {
            return;
        }
        String patientId = body.get("patientId") != null ? body.get("patientId").toString()
                : String.valueOf(body.get("patient_id"));
        String encounterRef = created.has("encounter_id") && !created.get("encounter_id").isNull()
                ? created.get("encounter_id").asText() : null;
        try {
            List<Map<String, Object>> items = List.of(
                    Map.of("code", "FBC", "displayName", "Full Blood Count", "quantity", 1),
                    Map.of("code", "UE", "displayName", "Urea & Electrolytes", "quantity", 1),
                    Map.of("code", "ECG", "displayName", "12-Lead ECG", "quantity", 1));
            orosClient.placeOrder(
                    "PROCEDURE",
                    "ROUTINE",
                    patientId,
                    encounterRef,
                    "Pre-operative workup for " + created.path("procedure_name").asText("procedure"),
                    items);
        } catch (Exception e) {
            log.warn("Preop OROS order placement failed (non-blocking): {}", e.getMessage());
        }
    }
}
