package zw.gov.mohcc.impilo.experience.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Teleconsultation lifecycle endpoints — 7-stage workflow.
 *
 * Stage 1: Case identified → create session
 * Stage 2: Build referral package → store draft
 * Stage 3: Routing & worklists → list/filter
 * Stage 4: Review & accept → accept/decline/reassign
 * Stage 5: Session workspace → chat, notes, attachments
 * Stage 6: Submit response → structured response package
 * Stage 7: Completion note → loop closure
 */
@RestController
@RequestMapping("/internal/v1/teleconsult")
public class TeleconsultController {

    private static final Logger log = LoggerFactory.getLogger(TeleconsultController.class);
    private static final List<Map<String, Object>> SESSIONS = new CopyOnWriteArrayList<>();
    private static final List<Map<String, Object>> MESSAGES = new CopyOnWriteArrayList<>();

    // ── Stage 1: Create session ───────────────────────────────────────

    @PostMapping("/sessions")
    public ResponseEntity<Map<String, Object>> createSession(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        String id = "tc-" + UUID.randomUUID().toString().substring(0, 8);
        String now = OffsetDateTime.now().toString();

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("id", id);
        session.put("patientId", body.get("patientId"));
        session.put("encounterId", body.get("encounterId"));
        session.put("referrerId", actorId);
        session.put("referrerFacilityId", body.get("facilityId"));
        session.put("urgency", body.getOrDefault("urgency", "ROUTINE"));
        session.put("specialty", body.get("specialty"));
        session.put("status", "DRAFT");
        session.put("stage", 1);
        session.put("createdAt", now);
        session.put("updatedAt", now);

        // Referral package (Stage 2)
        session.put("referralLetter", null);
        session.put("presentingProblems", List.of());
        session.put("clinicalQuestion", null);
        session.put("attachments", List.of());

        // Routing (Stage 2.4)
        session.put("routingType", null);
        session.put("routingTarget", null);
        session.put("consentToken", null);

        // Response (Stage 6)
        session.put("responseNote", null);
        session.put("responseOrders", List.of());
        session.put("responseDiagnosis", null);
        session.put("responseFollowUp", null);

        // Completion (Stage 7)
        session.put("completionNote", null);
        session.put("patientOutcome", null);

        SESSIONS.add(session);

        return ResponseEntity.status(HttpStatus.CREATED).body(wrap(session, requestId, correlationId));
    }

    // ── Stage 2: Update referral package ──────────────────────────────

    @PutMapping("/sessions/{id}/referral")
    public ResponseEntity<Map<String, Object>> updateReferral(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);

        if (body.containsKey("referralLetter")) session.put("referralLetter", body.get("referralLetter"));
        if (body.containsKey("presentingProblems")) session.put("presentingProblems", body.get("presentingProblems"));
        if (body.containsKey("clinicalQuestion")) session.put("clinicalQuestion", body.get("clinicalQuestion"));
        if (body.containsKey("attachments")) session.put("attachments", body.get("attachments"));
        if (body.containsKey("routingType")) session.put("routingType", body.get("routingType"));
        if (body.containsKey("routingTarget")) session.put("routingTarget", body.get("routingTarget"));
        if (body.containsKey("urgency")) session.put("urgency", body.get("urgency"));
        if (body.containsKey("specialty")) session.put("specialty", body.get("specialty"));
        session.put("stage", 2);
        session.put("updatedAt", OffsetDateTime.now().toString());

        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    // ── Stage 2.3: Record consent ────────────────────────────────────

    @PostMapping("/sessions/{id}/consent")
    public ResponseEntity<Map<String, Object>> recordConsent(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);

        String token = "consent-" + UUID.randomUUID().toString().substring(0, 8);
        session.put("consentToken", token);
        session.put("consentType", body.getOrDefault("type", "DIGITAL"));
        session.put("consentRecordedAt", OffsetDateTime.now().toString());
        session.put("updatedAt", OffsetDateTime.now().toString());

        return ResponseEntity.ok(wrap(Map.of("consentToken", token, "type", body.getOrDefault("type", "DIGITAL")), requestId, correlationId));
    }

    // ── Stage 2→3: Submit referral ───────────────────────────────────

    @PostMapping("/sessions/{id}/submit")
    public ResponseEntity<Map<String, Object>> submitReferral(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);

        if (session.get("consentToken") == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", Map.of("code", "CONSENT_REQUIRED", "message", "Consent token is required before submitting")));
        }
        session.put("status", "PENDING");
        session.put("stage", 3);
        session.put("submittedAt", OffsetDateTime.now().toString());
        session.put("updatedAt", OffsetDateTime.now().toString());

        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    // ── Stage 3: List sessions / worklist ─────────────────────────────

    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> listSessions(
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String patientId,
            @RequestParam(required = false) String referrerId) {

        List<Map<String, Object>> filtered = SESSIONS;
        if (status != null) filtered = filtered.stream().filter(s -> status.equals(s.get("status"))).collect(Collectors.toList());
        if (patientId != null) filtered = filtered.stream().filter(s -> patientId.equals(s.get("patientId"))).collect(Collectors.toList());
        if (referrerId != null) filtered = filtered.stream().filter(s -> referrerId.equals(s.get("referrerId"))).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("data", filtered, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    @GetMapping("/sessions/{id}")
    public ResponseEntity<Map<String, Object>> getSession(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);
        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    // ── Stage 4: Accept / Decline / Reassign ─────────────────────────

    @PostMapping("/sessions/{id}/accept")
    public ResponseEntity<Map<String, Object>> accept(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);
        session.put("status", "ACCEPTED");
        session.put("stage", 4);
        session.put("receiverId", actorId);
        session.put("acceptedAt", OffsetDateTime.now().toString());
        session.put("updatedAt", OffsetDateTime.now().toString());
        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    @PostMapping("/sessions/{id}/decline")
    public ResponseEntity<Map<String, Object>> decline(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);
        session.put("status", "DECLINED");
        session.put("declineReason", body.get("reason"));
        session.put("updatedAt", OffsetDateTime.now().toString());
        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    // ── Stage 5: Chat messages ───────────────────────────────────────

    @PostMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestHeader(value = CompanionHeaders.ACTOR_ID, required = false) String actorId,
            @RequestBody Map<String, Object> body) {

        String msgId = "msg-" + UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("id", msgId);
        msg.put("sessionId", id);
        msg.put("senderId", actorId);
        msg.put("senderName", body.getOrDefault("senderName", "Unknown"));
        msg.put("content", body.get("content"));
        msg.put("type", body.getOrDefault("type", "TEXT"));
        msg.put("timestamp", OffsetDateTime.now().toString());
        MESSAGES.add(msg);

        return ResponseEntity.status(HttpStatus.CREATED).body(wrap(msg, requestId, correlationId));
    }

    @GetMapping("/sessions/{id}/messages")
    public ResponseEntity<Map<String, Object>> getMessages(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId) {

        List<Map<String, Object>> msgs = MESSAGES.stream()
                .filter(m -> id.equals(m.get("sessionId"))).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("data", msgs, "meta", Map.of("request_id", requestId, "correlation_id", correlationId)));
    }

    // ── Stage 6: Submit response ─────────────────────────────────────

    @PostMapping("/sessions/{id}/response")
    public ResponseEntity<Map<String, Object>> submitResponse(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);

        session.put("responseNote", body.get("responseNote"));
        session.put("responseDiagnosis", body.get("diagnosis"));
        session.put("responseOrders", body.getOrDefault("orders", List.of()));
        session.put("responseFollowUp", body.get("followUp"));
        session.put("responseRedFlags", body.get("redFlags"));
        session.put("responseActionPlan", body.get("actionPlan"));
        session.put("status", "RESPONDED");
        session.put("stage", 6);
        session.put("respondedAt", OffsetDateTime.now().toString());
        session.put("updatedAt", OffsetDateTime.now().toString());

        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    // ── Stage 7: Completion note ─────────────────────────────────────

    @PostMapping("/sessions/{id}/complete")
    public ResponseEntity<Map<String, Object>> complete(
            @PathVariable String id,
            @RequestHeader(CompanionHeaders.REQUEST_ID) String requestId,
            @RequestHeader(CompanionHeaders.CORRELATION_ID) String correlationId,
            @RequestBody Map<String, Object> body) {

        Map<String, Object> session = findSession(id);
        if (session == null) return notFound(requestId, correlationId);

        session.put("completionNote", body.get("completionNote"));
        session.put("actionsTaken", body.get("actionsTaken"));
        session.put("patientOutcome", body.get("patientOutcome"));
        session.put("followUpExecution", body.get("followUpExecution"));
        session.put("outstandingIssues", body.get("outstandingIssues"));
        session.put("closureNarrative", body.get("closureNarrative"));
        session.put("status", "CLOSED");
        session.put("stage", 7);
        session.put("closedAt", OffsetDateTime.now().toString());
        session.put("updatedAt", OffsetDateTime.now().toString());

        return ResponseEntity.ok(wrap(session, requestId, correlationId));
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private Map<String, Object> findSession(String id) {
        return SESSIONS.stream().filter(s -> id.equals(s.get("id"))).findFirst().orElse(null);
    }

    private Map<String, Object> wrap(Object data, String requestId, String correlationId) {
        return Map.of("data", data, "meta", Map.of("request_id", requestId, "correlation_id", correlationId));
    }

    private ResponseEntity<Map<String, Object>> notFound(String requestId, String correlationId) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", Map.of("code", "NOT_FOUND", "message", "Teleconsult session not found")));
    }
}
