package zw.gov.mohcc.impilo.daidzai.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.daidzai.core.EmergencyService;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyRequestEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Public (R0, anonymous) emergency status lane — gateway public-lane ADR {@code Public*Controller}
 * rule. An anonymous requester who received a {@code requestReference} on the 202 SOS receipt can
 * check where their request stands, WITHOUT signing in.
 *
 * <p><b>Disclosure-limited by construction.</b> The response is an allow-listed, PII-free view:
 * only the reference, the lifecycle status, a coarse citizen-language stage label, the created
 * timestamp, and whether a callback is still pending. It NEVER returns the callback number, the
 * description, the location, or any subject identity — those live on the entity but are not mapped
 * here. A miss is a uniform 404 with no existence detail.</p>
 *
 * <p>Consumed only by the experience-bff public gateway SOS lane
 * ({@code /internal/v1/public/gateway/sos/{reference}}), which supplies the anonymous-safe trust
 * headers; never exposed to the UI directly.</p>
 */
@RestController
@RequestMapping("/v1/public/emergency")
public class PublicEmergencyStatusController {

    private final EmergencyService service;

    public PublicEmergencyStatusController(EmergencyService service) {
        this.service = service;
    }

    @GetMapping("/requests/{reference}/status")
    public ResponseEntity<PublicEmergencyStatus> status(
            @RequestHeader(CompanionHeaders.TENANT_ID) UUID tenantId,
            @PathVariable String reference) {
        return service.findRequestByReference(tenantId, reference)
                .map(r -> ResponseEntity.ok(toPublicStatus(r)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Allow-listed, PII-free status view. Adding a field here is a deliberate disclosure decision —
     * do not map subject, callback number, description, or location onto it.
     *
     * @param requestReference the human reference from the 202 receipt
     * @param status           the raw lifecycle status (AWAITING_CALLBACK / RECEIVED / LINKED / …)
     * @param stage            coarse citizen-language label for {@code status}
     * @param callbackPending  true while a dispatcher still needs to call back before dispatch
     * @param createdAt        when the request was captured
     */
    public record PublicEmergencyStatus(
            String requestReference,
            String status,
            String stage,
            boolean callbackPending,
            OffsetDateTime createdAt) {}

    private static PublicEmergencyStatus toPublicStatus(EmergencyRequestEntity r) {
        boolean callbackPending = EmergencyService.STATUS_AWAITING_CALLBACK.equals(r.getStatus())
                && !Boolean.TRUE.equals(r.getCallbackVerified());
        return new PublicEmergencyStatus(
                r.getRequestReference(),
                r.getStatus(),
                stageLabel(r.getStatus()),
                callbackPending,
                r.getCreatedAt());
    }

    /** Coarse, reassuring stage label — never operational detail. */
    private static String stageLabel(String status) {
        if (status == null) {
            return "In progress";
        }
        return switch (status) {
            case "AWAITING_CALLBACK" -> "Awaiting callback confirmation";
            case "RECEIVED" -> "Received — being reviewed";
            case "LINKED" -> "Help is being arranged";
            case "CLOSED", "RESOLVED" -> "Closed";
            default -> "In progress";
        };
    }
}
