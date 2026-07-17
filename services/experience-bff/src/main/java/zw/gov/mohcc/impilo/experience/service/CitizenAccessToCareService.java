package zw.gov.mohcc.impilo.experience.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.BookingServiceClient;
import zw.gov.mohcc.impilo.experience.client.NhumeServiceClient;
import zw.gov.mohcc.impilo.experience.client.ReferralServiceClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Citizen access-to-care actions behind the anonymous find-care journey — the resource-moving steps
 * that require a signed-in person (doctrine: care search is anonymous; booking/transport rise with
 * the action). Each action composes an authoritative sovereign source and NEVER fabricates:
 *
 * <ul>
 *   <li><b>Appointment request</b> → booking-service (system of record for citizen appointments). No
 *       real citizen slot-availability is published anywhere in the estate, so this is an honest
 *       governed <em>request</em>: booking-service records it {@code REQUESTED} and returns a
 *       reference. Slots and confirmations are never fabricated.</li>
 *   <li><b>Patient transport request</b> → nhume-service ({@code delivery_type=PATIENT}). Created as
 *       an honest pending request; nhume owns dispatch and the lane never claims a courier moved
 *       until nhume reports it. Emergency transport stays on the Daidzai SOS lane (not duplicated).</li>
 *   <li><b>Referral movement</b> → referral-service (+ nhume for the transport leg): a read-only,
 *       citizen-safe summary; internal clinical/ops detail stays hidden.</li>
 * </ul>
 *
 * <p>Abuse controls + PII-safety mirror {@link PublicSosIntakeService}: per-actor + global rate
 * windows in Redis, body caps, and disclosure-limited receipts. Because a citizen id keyed the
 * request, status reads verify an ownership binding to defeat IDOR on the sovereign reads (which are
 * only tenant-scoped).</p>
 */
@Service
public class CitizenAccessToCareService {

    private static final Logger log = LoggerFactory.getLogger(CitizenAccessToCareService.class);

    // ── Abuse thresholds (authed lane — lighter than the anonymous SOS write) ──
    public static final int MAX_TRANSPORT_PER_ACTOR = 10;
    public static final int ACTOR_WINDOW_SECONDS = 3600;      // 1 hour
    public static final int MAX_TRANSPORT_GLOBAL = 300;
    public static final int GLOBAL_WINDOW_SECONDS = 60;
    public static final int MAX_NOTES_CHARS = 1000;
    public static final int MAX_LABEL_CHARS = 200;
    public static final long OWNERSHIP_TTL_DAYS = 30;

    private static final String ACTOR_RATE_PREFIX = "citizen:transport:rl:actor:";
    private static final String GLOBAL_RATE_KEY = "citizen:transport:rl:global";
    private static final String OWNER_PREFIX = "citizen:transport:owner:";

    private final StringRedisTemplate redis;
    private final BookingServiceClient booking;
    private final NhumeServiceClient nhume;
    private final ReferralServiceClient referral;

    public CitizenAccessToCareService(StringRedisTemplate redis,
                                      BookingServiceClient booking,
                                      NhumeServiceClient nhume,
                                      ReferralServiceClient referral) {
        this.redis = redis;
        this.booking = booking;
        this.nhume = nhume;
        this.referral = referral;
    }

    /** Client error (missing/invalid input). Maps to HTTP 400. */
    public static class AccessToCareValidationException extends RuntimeException {
        public AccessToCareValidationException(String message) { super(message); }
    }

    /** Too many requests — carries a retry-after hint. Maps to HTTP 429. */
    public static class AccessToCareRateLimitedException extends RuntimeException {
        private final long retryAfterSeconds;
        public AccessToCareRateLimitedException(String message, long retryAfterSeconds) {
            super(message);
            this.retryAfterSeconds = retryAfterSeconds;
        }
        public long retryAfterSeconds() { return retryAfterSeconds; }
    }

    /** Not the requester's own resource (or unknown). Maps to HTTP 404 — no existence oracle. */
    public static class AccessToCareNotFoundException extends RuntimeException {
        public AccessToCareNotFoundException(String message) { super(message); }
    }

    // ── Appointment request (booking-service SoR; honest REQUESTED, never a fabricated slot) ──

    /**
     * Record a governed appointment request for the signed-in citizen. Delegates to booking-service,
     * which persists it {@code REQUESTED} and returns a reference — no slot is invented, nothing is
     * confirmed here. Returns a disclosure-limited receipt.
     */
    public Map<String, Object> requestAppointment(String actorCpid, String facilityId, String service,
                                                  String preferredDate, String reason) {
        requireText(facilityId, "facilityId");
        requireText(service, "service");
        requireText(preferredDate, "preferredDate");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("facilityId", facilityId.trim());
        body.put("appointmentType", service.trim());
        body.put("bookingType", service.trim());
        body.put("preferredDate", preferredDate.trim());
        body.put("preferredStartTime", preferredDate.trim());
        if (reason != null && !reason.isBlank()) {
            body.put("reason", cap(reason, MAX_NOTES_CHARS));
            body.put("reasonForBooking", cap(reason, MAX_NOTES_CHARS));
        }

        JsonNode created = booking.createCitizenBooking(actorCpid, body);
        String reference = firstText(created, "referenceNumber", "reference", "bookingNumber", "id");
        String status = firstText(created, "status", "bookingStatus");
        log.info("Citizen appointment requested cpid-hash={} facility={} ref={}",
                ContactOtpService.sha256Hex(actorCpid), facilityId, reference);
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("reference", reference);
        receipt.put("status", status != null ? status : "REQUESTED");
        receipt.put("message", "Your appointment request has been sent to the facility. You'll be "
                + "notified when it is confirmed — nothing is booked until the facility confirms.");
        return receipt;
    }

    // ── Patient transport request (nhume PATIENT; honest pending, never a fabricated dispatch) ──

    /**
     * Request planned (non-emergency) patient transport tied to a facility and, optionally, a
     * referral. Creates a nhume {@code PATIENT} delivery and submits it into the workflow as a
     * pending request. The receipt carries only a reference + citizen status; a courier is never
     * claimed until nhume reports one. Emergency transport is NOT handled here — that is the Daidzai
     * SOS lane.
     */
    public Map<String, Object> requestTransport(String actorCpid, Map<String, Object> input) {
        String facilityId = str(input, "facilityId");
        requireText(facilityId, "facilityId");
        String facilityLabel = cap(str(input, "facilityLabel"), MAX_LABEL_CHARS);
        String referralId = str(input, "referralId");
        String pickupLabel = cap(str(input, "pickupLabel"), MAX_LABEL_CHARS);
        String notes = cap(str(input, "notes"), MAX_NOTES_CHARS);

        enforceWindow(ACTOR_RATE_PREFIX + ContactOtpService.sha256Hex(actorCpid),
                MAX_TRANSPORT_PER_ACTOR, ACTOR_WINDOW_SECONDS,
                "You have requested transport many times recently. Please wait a little before trying again.");
        enforceWindow(GLOBAL_RATE_KEY, MAX_TRANSPORT_GLOBAL, GLOBAL_WINDOW_SECONDS,
                "The transport service is very busy right now. Please try again shortly.");

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("kind", "FACILITY");
        destination.put("ref", facilityId);
        if (facilityLabel != null && !facilityLabel.isBlank()) {
            destination.put("label", facilityLabel);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("delivery_type", "PATIENT");
        body.put("priority", "STANDARD");
        body.put("request_source", "CITIZEN_FIND_CARE");
        body.put("requesting_actor_id", actorCpid);
        body.put("requesting_actor_type", "PATIENT");
        body.put("requesting_facility_id", facilityId);
        body.put("destination", destination);
        if (pickupLabel != null && !pickupLabel.isBlank()) {
            Map<String, Object> origin = new LinkedHashMap<>();
            origin.put("kind", "ADDRESS");
            origin.put("label", pickupLabel);
            body.put("origin", origin);
        }
        if (referralId != null && !referralId.isBlank()) {
            body.put("clinical_context_ref", referralId.trim());
        }
        if (notes != null && !notes.isBlank()) {
            body.put("notes", notes);
        }
        // Enter the workflow as a pending request (SUBMITTED). Dispatch/assignment is nhume's to make;
        // we never present it as dispatched.
        body.put("submit_immediately", true);

        JsonNode created = nhume.createDelivery(body);
        String reference = firstText(created, "reference_code", "referenceCode", "reference");
        String deliveryId = firstText(created, "delivery_id", "deliveryId", "id");
        String rawStatus = firstText(created, "status");
        bindOwnership(deliveryId, actorCpid);

        log.info("Citizen transport requested cpid-hash={} facility={} ref={} nhumeStatus={}",
                ContactOtpService.sha256Hex(actorCpid), facilityId, reference, rawStatus);
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("reference", reference);
        receipt.put("transportRef", deliveryId);
        receipt.put("status", citizenTransportStatus(rawStatus));
        receipt.put("message", "Your transport request has been received. It is being reviewed — you'll "
                + "be updated as it is assigned. No vehicle is on the way until it is assigned.");
        return receipt;
    }

    /**
     * Citizen-safe transport status by the transport reference the request returned. Verifies the
     * ownership binding first (the sovereign read is only tenant-scoped, so this defeats IDOR). Returns
     * reference + a coarse citizen status only — never courier, address, or clinical detail.
     */
    public Map<String, Object> transportStatus(String actorCpid, String transportRef) {
        requireText(transportRef, "transportRef");
        if (!ownsResource(transportRef, actorCpid)) {
            throw new AccessToCareNotFoundException("No transport request found for this reference.");
        }
        JsonNode delivery;
        try {
            delivery = nhume.getDelivery(transportRef.trim());
        } catch (Exception e) {
            log.warn("Citizen transport status read failed for ref-hash={}: {}",
                    ContactOtpService.sha256Hex(transportRef), e.getMessage());
            throw new AccessToCareNotFoundException("No transport request found for this reference.");
        }
        if (delivery == null || delivery.isMissingNode() || delivery.isNull()) {
            throw new AccessToCareNotFoundException("No transport request found for this reference.");
        }
        String rawStatus = firstText(delivery, "status");
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("reference", firstText(delivery, "reference_code", "referenceCode", "reference"));
        view.put("transportRef", transportRef.trim());
        view.put("status", citizenTransportStatus(rawStatus));
        view.put("updatedAt", firstText(delivery, "updated_at", "updatedAt"));
        return view;
    }

    // ── Referral movement (referral-service + nhume; citizen-safe, read-only) ──

    /**
     * Citizen-safe referral movement summary: the referral lifecycle status (mapped to citizen
     * language), the receiving facility name when the referral holds it, and — when the citizen
     * supplies the transport reference for this referral — the transport leg's coarse status. The
     * referral read is tenant-scoped only, so this verifies the referral belongs to the requesting
     * citizen (patientId == actor) before disclosing anything. Internal clinical/ops payload is never
     * echoed.
     */
    public Map<String, Object> referralMovement(String actorCpid, String referralId, String transportRef) {
        requireText(referralId, "referralId");
        JsonNode ref;
        try {
            ref = referral.getReferral(referralId.trim());
        } catch (Exception e) {
            log.warn("Citizen referral movement read failed for referral-hash={}: {}",
                    ContactOtpService.sha256Hex(referralId), e.getMessage());
            throw new AccessToCareNotFoundException("No referral found for this reference.");
        }
        if (ref == null || ref.isMissingNode() || ref.isNull()) {
            throw new AccessToCareNotFoundException("No referral found for this reference.");
        }
        String patientId = firstText(ref, "patientId", "patient_id");
        if (patientId == null || !patientId.equals(actorCpid)) {
            // Not the requester's referral — do not confirm it exists.
            throw new AccessToCareNotFoundException("No referral found for this reference.");
        }

        Map<String, Object> movement = new LinkedHashMap<>();
        movement.put("reference", firstText(ref, "id"));
        movement.put("status", citizenReferralStatus(firstText(ref, "status")));
        movement.put("receivingFacility", safeReceivingFacility(ref.get("payload")));
        movement.put("acceptedAt", firstText(ref, "updatedAt", "updated_at"));
        movement.put("requestedAt", firstText(ref, "createdAt", "created_at"));

        // Optional transport leg — only when the citizen supplies (and owns) the transport reference.
        if (transportRef != null && !transportRef.isBlank() && ownsResource(transportRef, actorCpid)) {
            try {
                JsonNode delivery = nhume.getDelivery(transportRef.trim());
                if (delivery != null && !delivery.isMissingNode() && !delivery.isNull()) {
                    Map<String, Object> leg = new LinkedHashMap<>();
                    leg.put("transportRef", transportRef.trim());
                    leg.put("status", citizenTransportStatus(firstText(delivery, "status")));
                    leg.put("updatedAt", firstText(delivery, "updated_at", "updatedAt"));
                    movement.put("transport", leg);
                }
            } catch (Exception e) {
                log.warn("Referral transport leg read failed: {}", e.getMessage());
                // Omit the transport leg rather than fabricate it.
            }
        }
        return movement;
    }

    // ── internals ──────────────────────────────────────────────────────────

    /** Map nhume's fine-grained delivery status to the small citizen vocabulary. */
    static String citizenTransportStatus(String raw) {
        if (raw == null) return "REQUESTED";
        return switch (raw.toUpperCase()) {
            case "ASSIGNED", "ACCEPTED" -> "ASSIGNED";
            case "EN_ROUTE_TO_PICKUP", "AWAITING_PICKUP" -> "PICKUP";
            case "PICKED_UP", "IN_TRANSIT", "DELIVERY_ATTEMPTED" -> "EN_ROUTE";
            case "AT_DESTINATION", "DELIVERED", "PARTIALLY_DELIVERED", "CLOSED" -> "ARRIVED";
            case "CANCELLED", "REJECTED", "FAILED", "RETURNED", "DISPUTED" -> "CANCELLED";
            default -> "REQUESTED";
        };
    }

    /** Map referral lifecycle to citizen language. */
    static String citizenReferralStatus(String raw) {
        if (raw == null) return "SUBMITTED";
        return switch (raw.toUpperCase()) {
            case "ACCEPTED" -> "ACCEPTED";
            case "RESPONDED" -> "IN_PROGRESS";
            case "COMPLETED" -> "ARRIVED";
            case "CANCELLED", "REJECTED" -> "CANCELLED";
            default -> "SUBMITTED";
        };
    }

    /** Extract only a facility NAME from the referral payload — never the full clinical payload. */
    private static String safeReceivingFacility(JsonNode payload) {
        if (payload == null || !payload.isObject()) return null;
        for (String key : List.of("receivingFacilityName", "receivingFacility", "toFacilityName",
                "toFacility", "destinationFacility", "receiving_facility_name")) {
            JsonNode v = payload.get(key);
            if (v != null && v.isTextual() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private void bindOwnership(String resourceId, String actorCpid) {
        if (resourceId == null || resourceId.isBlank()) return;
        try {
            redis.opsForValue().set(OWNER_PREFIX + resourceId, ContactOtpService.sha256Hex(actorCpid),
                    Duration.ofDays(OWNERSHIP_TTL_DAYS));
        } catch (Exception e) {
            // Best-effort: a lost binding just means the citizen must re-request; never fabricate access.
            log.warn("Transport ownership binding store unavailable: {}", e.getMessage());
        }
    }

    private boolean ownsResource(String resourceId, String actorCpid) {
        if (resourceId == null || resourceId.isBlank()) return false;
        try {
            String owner = redis.opsForValue().get(OWNER_PREFIX + resourceId.trim());
            return owner != null && owner.equals(ContactOtpService.sha256Hex(actorCpid));
        } catch (Exception e) {
            // Fail CLOSED: without a verifiable binding we do not disclose someone else's transport.
            log.warn("Transport ownership check store unavailable, denying read: {}", e.getMessage());
            return false;
        }
    }

    private void enforceWindow(String key, int max, int windowSeconds, String message) {
        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1) {
                redis.expire(key, Duration.ofSeconds(windowSeconds));
            }
            if (count != null && count > max) {
                Long ttl = redis.getExpire(key);
                throw new AccessToCareRateLimitedException(message, ttl != null && ttl > 0 ? ttl : windowSeconds);
            }
        } catch (AccessToCareRateLimitedException e) {
            throw e;
        } catch (Exception e) {
            // Fail CLOSED for a resource-moving write when the limiter is down (not life-safety —
            // emergencies use the SOS lane, which fails open).
            log.warn("Access-to-care rate-limit store unavailable, denying request (fail-closed): {}", e.getMessage());
            throw new AccessToCareRateLimitedException(
                    "We can't process this request right now. Please try again shortly.", windowSeconds);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new AccessToCareValidationException(field + " is required.");
        }
    }

    private static String firstText(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String key : keys) {
            JsonNode v = node.get(key);
            if (v != null && !v.isNull() && v.isValueNode() && !v.asText().isBlank()) {
                return v.asText();
            }
        }
        return null;
    }

    private static String str(Map<String, Object> body, String key) {
        Object v = body == null ? null : body.get(key);
        return v == null ? "" : v.toString().trim();
    }

    private static String cap(String value, int max) {
        if (value == null) return null;
        String t = value.trim();
        return t.length() <= max ? t : t.substring(0, max);
    }
}
