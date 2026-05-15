package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DocumentServiceClient;
import zw.gov.mohcc.impilo.experience.client.MvumoServiceClient;
import zw.gov.mohcc.impilo.experience.client.PctServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.experience.client.VarapiServiceClient;

import java.util.*;

/**
 * Teleconsultation lifecycle endpoints backed by canonical PCT + MVUMO flows.
 *
 * <p>Message transport endpoints remain fail-closed while real-time channel
 * infrastructure is not part of the canonical stack.</p>
 */
@RestController
@RequestMapping("/internal/v1/teleconsult")
public class TeleconsultController {

    private static final Logger log = LoggerFactory.getLogger(TeleconsultController.class);
    private static final Set<String> UNSUPPORTED_ROUTING_TYPES = Set.of("ON_CALL", "TEAM", "SPECIALTY_POOL", "POOL", "NATIONAL_POOL");
    private static final Set<String> PRACTITIONER_ROUTING_TYPES = Set.of("PRACTITIONER", "PROVIDER");

    private final PctServiceClient pctClient;
    private final MvumoServiceClient mvumoClient;
    private final DocumentServiceClient documentClient;
    private final VarapiServiceClient varapiClient;
    private final TusoServiceClient tusoClient;
    private final ObjectMapper objectMapper;

    public TeleconsultController(PctServiceClient pctClient,
                                 MvumoServiceClient mvumoClient,
                                 DocumentServiceClient documentClient,
                                 VarapiServiceClient varapiClient,
                                 TusoServiceClient tusoClient,
                                 ObjectMapper objectMapper) {
        this.pctClient = pctClient;
        this.mvumoClient = mvumoClient;
        this.documentClient = documentClient;
        this.varapiClient = varapiClient;
        this.tusoClient = tusoClient;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("encounter_id", val(body, "encounterId", "encounter_id"));
            payload.put("patient_id", val(body, "patientId", "patient_id"));
            payload.put("provider_id", actorId != null ? actorId : val(body, "providerId", "provider_id"));
            payload.put("urgency", val(body, "urgency"));
            payload.put("specialty", val(body, "specialty"));
            payload.put("clinical_question", val(body, "clinicalQuestion", "reason"));
            payload.put("modality", "virtual");
            payload.put("virtual_mode", "video");
            payload.put("consent_required", true);
            var created = pctClient.createReferral(payload);
            if (created == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            return ok(created, requestId, correlationId, HttpStatus.CREATED);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PutMapping("/sessions/{id}/referral")
    public ResponseEntity<Map<String, Object>> updateReferral(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            List<String> attachmentRefs = extractAttachmentReferences(body.get("attachments"));
            ValidationError attachmentValidation = validateAttachmentReferences(attachmentRefs);
            if (attachmentValidation != null) {
                return attachmentValidation.toResponse(requestId, correlationId);
            }

            String routingType = normalizedRoutingType(val(body, "routingType", "routing_type"));
            String routingTarget = val(body, "routingTarget", "routing_target_ref", "routing_target");
            ValidationError routingValidation = validateRoutingTarget(routingType, routingTarget, body);
            if (routingValidation != null) {
                return routingValidation.toResponse(requestId, correlationId);
            }

            Map<String, Object> update = new LinkedHashMap<>();
            update.put("stage", inferStage(body));
            update.put("referral_letter", val(body, "referralLetter"));
            update.put("patient_summary", val(body, "patientSummary"));
            update.put("visit_summary", val(body, "visitSummary"));
            update.put("clinical_question", val(body, "clinicalQuestion"));
            update.put("attachment_document_ids", attachmentRefs);
            if (routingType != null) {
                update.put("routing_target", Map.of(
                        "type", routingType,
                        "target_ref", routingTarget == null ? "" : routingTarget));
            }
            update.put("preferredMode", val(body, "preferredMode"));
            var updated = pctClient.updateReferralStage(id, update);
            if (updated == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral update payload returned", requestId, correlationId);
            }
            return ok(normalizeReferralPayload(updated), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/consent")
    public ResponseEntity<Map<String, Object>> recordConsent(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            String patientRef = val(body, "patientId", "patient_id");
            if (patientRef == null || patientRef.isBlank()) {
                var referral = pctClient.getReferral(id);
                if (referral != null && referral.get("patientCpid") != null) {
                    patientRef = referral.get("patientCpid").asText();
                }
            }
            Map<String, Object> consentRequest = new LinkedHashMap<>();
            consentRequest.put("subjectPatientRef", patientRef);
            consentRequest.put("consentType", val(body, "type", "consentType"));
            consentRequest.put("workflowRef", "referral:" + id);
            consentRequest.put("encounterRef", val(body, "encounterId", "encounter_id"));
            consentRequest.put("context", Map.of("referralId", id));

            var mvumo = mvumoClient.createConsentRequest(consentRequest);
            String consentId = mvumo != null && mvumo.get("id") != null ? mvumo.get("id").asText() : null;
            String tshepoConsentId = mvumo != null && mvumo.get("tshepoConsentId") != null ? mvumo.get("tshepoConsentId").asText() : null;

            Map<String, Object> pctConsent = new LinkedHashMap<>();
            pctConsent.put("consent_type", val(body, "type", "consentType"));
            pctConsent.put("consent_status", "PENDING");
            pctConsent.put("consent_reference", consentId);
            pctConsent.put("mvumo_session_id", consentId);
            pctConsent.put("tshepo_decision_id", tshepoConsentId);
            pctClient.updateReferralConsent(id, pctConsent);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("consentToken", consentId);
            response.put("consentReference", consentId);
            response.put("mvumoSessionId", consentId);
            response.put("tshepoDecisionId", tshepoConsentId);
            response.put("status", "PENDING");
            return ok(response, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("MVUMO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitReferral(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            JsonNode referral = pctClient.getReferral(id);
            ValidationError submitValidation = validateStoredRoutingAndAttachments(referral);
            if (submitValidation != null) {
                return submitValidation.toResponse(requestId, correlationId);
            }
            var submitted = pctClient.submitReferral(id);
            if (submitted == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No referral submit payload returned", requestId, correlationId);
            }
            return ok(normalizeReferralPayload(submitted), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String referrerId) {
        try {
            var list = pctClient.listPatientReferrals(patientId, 0, 50);
            if (list == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult list payload returned", requestId, correlationId);
            }
            return ok(normalizeReferralPayload(list), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        try {
            var referral = pctClient.getReferral(id);
            if (referral == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No teleconsult session payload returned", requestId, correlationId);
            }
            return ok(normalizeReferralPayload(referral), requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {
        try {
            var accepted = pctClient.acceptReferral(id, Map.of(
                    "accepted_by", actorId != null ? actorId : "unknown"));
            return ok(accepted, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", Map.of("code", "REALTIME_CHANNEL_UNAVAILABLE",
                        "message", "Live decline/chat transport is not implemented in canonical backend"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", Map.of("code", "REALTIME_CHANNEL_UNAVAILABLE",
                        "message", "Live chat transport is not implemented in canonical backend"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).body(Map.of(
                "error", Map.of("code", "REALTIME_CHANNEL_UNAVAILABLE",
                        "message", "Live chat history is not implemented in canonical backend"),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @PostMapping("/sessions/{id}/response")
    public ResponseEntity<Map<String, Object>> submitResponse(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            var response = pctClient.respondReferral(id, body);
            if (response == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No consultation response payload returned", requestId, correlationId);
            }
            return ok(response, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {
        try {
            var completed = pctClient.completeReferral(id, body == null ? Map.of() : body);
            if (completed == null) {
                return upstreamFailure("PCT_UNAVAILABLE", "No completion payload returned", requestId, correlationId);
            }
            return ok(completed, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("PCT_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/routing/providers")
    public ResponseEntity<Map<String, Object>> searchProviders(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("page", page);
            request.put("size", Math.min(Math.max(size, 1), 50));
            JsonNode providers = varapiClient.searchProviders(request);
            return ok(providers, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("VARAPI_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/routing/facilities")
    public ResponseEntity<Map<String, Object>> searchFacilities(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "q") String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("query", query);
            request.put("page", page);
            request.put("size", Math.min(Math.max(size, 1), 50));
            JsonNode facilities = tusoClient.searchFacilities(request);
            return ok(facilities, requestId, correlationId, HttpStatus.OK);
        } catch (Exception e) {
            return upstreamFailure("TUSO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    @GetMapping("/routing/workspaces")
    public ResponseEntity<Map<String, Object>> listWorkspaces(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(name = "facility_id") String facilityId) {
        try {
            long parsedFacilityId = Long.parseLong(facilityId);
            JsonNode workspaces = tusoClient.listWorkspaces(parsedFacilityId);
            return ok(workspaces, requestId, correlationId, HttpStatus.OK);
        } catch (NumberFormatException nfe) {
            return error(HttpStatus.BAD_REQUEST, "INVALID_FACILITY_ID",
                    "facility_id must be numeric", requestId, correlationId);
        } catch (Exception e) {
            return upstreamFailure("TUSO_UNAVAILABLE", e.getMessage(), requestId, correlationId);
        }
    }

    private int inferStage(Map<String, Object> body) {
        if (body.containsKey("type") || body.containsKey("consentType")) return 7;
        if (body.containsKey("preferredMode")) return 6;
        if (body.containsKey("routingType")) return 5;
        if (body.containsKey("attachments")) return 4;
        if (body.containsKey("visitSummary")) return 3;
        if (body.containsKey("patientSummary")) return 2;
        return 1;
    }

    private String val(Map<String, Object> body, String... keys) {
        if (body == null) return null;
        for (String key : keys) {
            Object value = body.get(key);
            if (value != null) return value.toString();
        }
        return null;
    }

    private ResponseEntity<Map<String, Object>> ok(Object data, String requestId, String correlationId, HttpStatus status) {
        return ResponseEntity.status(status).body(Map.of(
                "data", data,
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String requestId, String correlationId) {
        return ResponseEntity.status(status).body(Map.of(
                "error", Map.of("code", code, "message", message),
                "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    private ResponseEntity<Map<String, Object>> upstreamFailure(String code, String message, String requestId, String correlationId) {
        log.warn("Teleconsult upstream failure: {}", message);
        return error(HttpStatus.BAD_GATEWAY, code,
                message != null ? message : "Teleconsult upstream unavailable", requestId, correlationId);
    }

    private ValidationError validateStoredRoutingAndAttachments(JsonNode referral) {
        if (referral == null || referral.isNull()) {
            return new ValidationError(HttpStatus.BAD_GATEWAY, "PCT_UNAVAILABLE", "Unable to load referral for pre-submit validation");
        }
        List<String> attachments = extractAttachmentReferencesFromReferral(referral);
        ValidationError attachmentValidation = validateAttachmentReferences(attachments);
        if (attachmentValidation != null) return attachmentValidation;

        String routingType = null;
        String routingTarget = null;
        JsonNode routingNode = parseRoutingTarget(referral.path("routingTarget"));
        if (routingNode != null && routingNode.isObject()) {
            routingType = routingNode.path("type").asText(null);
            routingTarget = routingNode.hasNonNull("target_ref")
                    ? routingNode.path("target_ref").asText()
                    : routingNode.path("target").asText(null);
        }
        return validateRoutingTarget(normalizedRoutingType(routingType), routingTarget, Map.of());
    }

    private ValidationError validateAttachmentReferences(List<String> attachmentRefs) {
        for (String ref : attachmentRefs) {
            UUID documentId;
            try {
                documentId = UUID.fromString(ref);
            } catch (IllegalArgumentException ex) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_REFERENCE",
                        "Attachment reference is not a UUID: " + ref);
            }
            try {
                documentClient.getObjectMetadata(documentId);
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ATTACHMENT_REFERENCE",
                        "Attachment document id does not exist: " + documentId);
            } catch (HttpStatusCodeException clientError) {
                if (clientError.getStatusCode().value() == 403 || clientError.getStatusCode().value() == 401) {
                    return new ValidationError(HttpStatus.FORBIDDEN, "ATTACHMENT_INACCESSIBLE",
                            "Attachment is not accessible: " + documentId);
                }
                return new ValidationError(HttpStatus.BAD_GATEWAY, "DOCUMENT_SERVICE_UNAVAILABLE",
                        "Document service lookup failed for attachment validation");
            } catch (ResourceAccessException ex) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "DOCUMENT_SERVICE_UNAVAILABLE",
                        "Document service is unavailable for attachment validation");
            }
        }
        return null;
    }

    private ValidationError validateRoutingTarget(String routingType, String routingTarget, Map<String, Object> body) {
        if (routingType == null || routingType.isBlank()) {
            return null;
        }
        if (UNSUPPORTED_ROUTING_TYPES.contains(routingType)) {
            return new ValidationError(HttpStatus.NOT_IMPLEMENTED, "ROUTING_TYPE_UNAVAILABLE",
                    routingType + " routing requires future on-call/team/pool directory capability");
        }
        if (routingTarget == null || routingTarget.isBlank()) {
            return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                    "routingTarget is required when routingType is set");
        }
        if (PRACTITIONER_ROUTING_TYPES.contains(routingType)) {
            try {
                JsonNode provider = varapiClient.getProvider(routingTarget.trim());
                if (provider == null || provider.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Provider routing target was not found in VARAPI");
                }
                return null;
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Provider routing target was not found in VARAPI");
            } catch (Exception e) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "VARAPI_UNAVAILABLE",
                        "VARAPI provider lookup failed for routing validation");
            }
        }
        if ("WORKSPACE".equals(routingType)) {
            try {
                UUID workspaceId = UUID.fromString(routingTarget.trim());
                JsonNode workspace = tusoClient.getWorkspace(workspaceId);
                if (workspace == null || workspace.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Workspace routing target was not found in TUSO");
                }
                return null;
            } catch (IllegalArgumentException ex) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Workspace routing target must be a UUID");
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Workspace routing target was not found in TUSO");
            } catch (Exception e) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "TUSO_UNAVAILABLE",
                        "TUSO workspace lookup failed for routing validation");
            }
        }
        if ("UNIT".equals(routingType)) {
            return new ValidationError(HttpStatus.NOT_IMPLEMENTED, "ROUTING_TYPE_UNAVAILABLE",
                    "UNIT routing requires canonical facility-unit directory lookup support");
        }
        if ("FACILITY_SERVICE".equals(routingType)) {
            String facilityRef = routingTarget.trim();
            String[] parts = facilityRef.split(":", 2);
            try {
                long facilityId = Long.parseLong(parts[0]);
                JsonNode facility = tusoClient.getFacility(facilityId);
                if (facility == null || facility.isNull()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "Facility routing target was not found in TUSO");
                }
                if (parts.length == 2 && parts[1].isBlank()) {
                    return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                            "facility-service routing target must include a service code after ':'");
                }
                return null;
            } catch (NumberFormatException ex) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "facility-service routing target must start with numeric facility id");
            } catch (HttpClientErrorException.NotFound notFound) {
                return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                        "Facility routing target was not found in TUSO");
            } catch (Exception e) {
                return new ValidationError(HttpStatus.BAD_GATEWAY, "TUSO_UNAVAILABLE",
                        "TUSO facility lookup failed for routing validation");
            }
        }
        return new ValidationError(HttpStatus.BAD_REQUEST, "INVALID_ROUTING_TARGET",
                "Unsupported routingType: " + routingType);
    }

    private List<String> extractAttachmentReferences(Object rawAttachments) {
        if (rawAttachments == null) {
            return List.of();
        }
        if (rawAttachments instanceof List<?> list) {
            return list.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .toList();
        }
        if (rawAttachments instanceof String single) {
            String trimmed = single.trim();
            if (trimmed.isBlank()) return List.of();
            return List.of(trimmed);
        }
        return List.of(rawAttachments.toString().trim());
    }

    private List<String> extractAttachmentReferencesFromReferral(JsonNode referral) {
        JsonNode attachmentsNode = referral.path("attachmentDocumentIds");
        if (attachmentsNode.isMissingNode() || attachmentsNode.isNull()) {
            attachmentsNode = referral.path("attachments");
        }
        if (attachmentsNode.isMissingNode() || attachmentsNode.isNull()) {
            return List.of();
        }
        if (attachmentsNode.isArray()) {
            List<String> refs = new ArrayList<>();
            attachmentsNode.forEach(node -> {
                String ref = node.asText("").trim();
                if (!ref.isBlank()) refs.add(ref);
            });
            return refs.stream().distinct().toList();
        }
        if (attachmentsNode.isTextual()) {
            String raw = attachmentsNode.asText("");
            if (raw.isBlank()) return List.of();
            try {
                JsonNode parsed = objectMapper.readTree(raw);
                if (parsed.isArray()) {
                    List<String> refs = new ArrayList<>();
                    parsed.forEach(node -> {
                        String ref = node.asText("").trim();
                        if (!ref.isBlank()) refs.add(ref);
                    });
                    return refs.stream().distinct().toList();
                }
            } catch (JsonProcessingException ignored) {
                // fallback handled below
            }
            return List.of(raw.trim());
        }
        return List.of();
    }

    private String normalizedRoutingType(String routingType) {
        return routingType == null ? null : routingType.trim().toUpperCase(Locale.ROOT);
    }

    private Object normalizeReferralPayload(Object payload) {
        if (payload instanceof JsonNode jsonNode) {
            return normalizeReferralJson(jsonNode);
        }
        return payload;
    }

    private JsonNode normalizeReferralJson(JsonNode input) {
        if (input == null || input.isNull()) return input;
        if (input.isArray()) {
            ArrayNode normalized = objectMapper.createArrayNode();
            input.forEach(node -> normalized.add(normalizeReferralJson(node)));
            return normalized;
        }
        if (!input.isObject()) return input;
        ObjectNode copy = input.deepCopy();
        List<String> attachments = extractAttachmentReferencesFromReferral(copy);
        ArrayNode attachmentRefs = objectMapper.createArrayNode();
        attachments.forEach(attachmentRefs::add);
        copy.set("attachmentReferences", attachmentRefs);

        JsonNode routingNode = parseRoutingTarget(copy.path("routingTarget"));
        if (routingNode != null && routingNode.isObject()) {
            copy.put("routingType", routingNode.path("type").asText(""));
            String targetRef = routingNode.hasNonNull("target_ref")
                    ? routingNode.path("target_ref").asText("")
                    : routingNode.path("target").asText("");
            copy.put("routingTargetRef", targetRef);
        }
        return copy;
    }

    private JsonNode parseRoutingTarget(JsonNode routingNode) {
        if (routingNode == null || routingNode.isNull() || routingNode.isMissingNode()) return null;
        if (routingNode.isObject()) return routingNode;
        if (routingNode.isTextual()) {
            String raw = routingNode.asText("");
            if (raw.isBlank()) return null;
            try {
                JsonNode parsed = objectMapper.readTree(raw);
                return parsed.isObject() ? parsed : null;
            } catch (JsonProcessingException ignored) {
                return null;
            }
        }
        return null;
    }

    private record ValidationError(HttpStatus status, String code, String message) {
        ResponseEntity<Map<String, Object>> toResponse(String requestId, String correlationId) {
            return ResponseEntity.status(status).body(Map.of(
                    "error", Map.of("code", code, "message", message),
                    "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
        }
    }
}
