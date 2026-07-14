package zw.gov.mohcc.impilo.experience.controller;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.experience.client.DaidzaiServiceClient;
import zw.gov.mohcc.impilo.experience.client.MusheWalletServiceClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Citizen fundraisers (crowdfunding) — stateless composition over the two owners:
 * Daidzai holds the CASE (verification lifecycle, timeline, anonymised presentation,
 * signed-attestation refs) and mushe holds the MONEY (escrow, contributions, refunds).
 * Public naming: citizens see "fundraisers", never internal service names.
 *
 * <p>Creation binds the beneficiary wallet to the CALLER server-side; donations resolve
 * the donor's wallet server-side and pass it as the debit source (real money movement —
 * never a bare counter increment).</p>
 */
@RestController
@RequestMapping("/internal/v1/citizen/fundraisers")
public class CitizenCrowdfundingController {

    private static final Logger log = LoggerFactory.getLogger(CitizenCrowdfundingController.class);

    private final DaidzaiServiceClient daidzai;
    private final MusheWalletServiceClient mushe;

    public CitizenCrowdfundingController(DaidzaiServiceClient daidzai, MusheWalletServiceClient mushe) {
        this.daidzai = daidzai;
        this.mushe = mushe;
    }

    /** Create a draft fundraiser for MYSELF — beneficiary wallet bound to the caller. */
    @PostMapping
    public ResponseEntity<Object> create(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestBody Map<String, Object> body) {
        UUID walletId = resolveWallet(actorId);
        if (walletId == null) {
            return unavailable("could not resolve your wallet — a fundraiser needs a destination for funds");
        }
        Map<String, Object> upstream = new LinkedHashMap<>(body != null ? body : Map.of());
        upstream.put("beneficiaryWalletId", walletId.toString()); // server-side binding
        upstream.remove("verificationStatus"); // lifecycle is governed, never client-set
        return call(() -> daidzai.createAssistance(upstream));
    }

    /** Publicly listed fundraisers — verified-ACTIVE only (Daidzai enforces). */
    @GetMapping
    public ResponseEntity<Object> listPublic() {
        return call(daidzai::listPublicAssistance);
    }

    @GetMapping("/mine")
    public ResponseEntity<Object> listMine() {
        return call(daidzai::listMyAssistance);
    }

    /** Case + live money view + independent verification pointer, composed. */
    @GetMapping("/{id}")
    public ResponseEntity<Object> get(@PathVariable String id) {
        try {
            JsonNode assistance = daidzai.getAssistance(id);
            Map<String, Object> composed = new LinkedHashMap<>();
            composed.put("case", assistance);
            String shareToken = assistance != null ? assistance.path("shareToken").asText(null) : null;
            if (shareToken != null && !shareToken.isBlank()) {
                composed.put("shareUrl", "/share/fund/" + shareToken);
                try {
                    composed.put("money", mushe.getBillContribution(shareToken));
                } catch (Exception e) {
                    // Case view stays useful when the money view is briefly unreachable —
                    // amounts on the case are the event-driven projection, clearly labelled.
                    composed.put("moneyUnavailable", true);
                }
            }
            String verifyUrl = assistance != null
                    ? assistance.path("attestationPublicToken").asText(null) : null;
            if (verifyUrl != null && !verifyUrl.isBlank()) {
                composed.put("independentVerificationUrl", verifyUrl);
            }
            return ResponseEntity.ok(composed);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("fundraiser get failed: {}", e.getMessage());
            return unavailable("fundraiser is unavailable right now");
        }
    }

    @GetMapping("/{id}/timeline")
    public ResponseEntity<Object> timeline(@PathVariable String id) {
        return call(() -> daidzai.assistanceSubResource(id, "timeline"));
    }

    @GetMapping("/{id}/contributions")
    public ResponseEntity<Object> contributions(@PathVariable String id) {
        return call(() -> daidzai.assistanceSubResource(id, "contributions"));
    }

    /** Donate from MY wallet — donor wallet resolved server-side (real debit, idempotent). */
    @PostMapping("/{id}/donate")
    public ResponseEntity<Object> donate(
            @RequestHeader(CompanionHeaders.ACTOR_ID) String actorId,
            @RequestHeader(value = CompanionHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        try {
            JsonNode assistance = daidzai.getAssistance(id);
            String shareToken = assistance == null ? null : assistance.path("shareToken").asText(null);
            String status = assistance == null ? null : assistance.path("verificationStatus").asText(null);
            if (shareToken == null || shareToken.isBlank()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", Map.of("code", "UNKNOWN_FUNDRAISER",
                                "message", "That fundraiser was not found.")));
            }
            if (!"ACTIVE".equals(status)) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", Map.of("code", "FUNDRAISER_NOT_OPEN",
                                "message", "This fundraiser is not open for donations (" + status + ").")));
            }
            UUID donorWallet = resolveWallet(actorId);
            if (donorWallet == null) {
                return unavailable("could not resolve your wallet to donate from");
            }
            Map<String, Object> upstream = new LinkedHashMap<>(body != null ? body : Map.of());
            upstream.put("contributorWalletId", donorWallet.toString()); // server-side debit source
            upstream.put("contributorRef", actorId);
            upstream.put("idempotencyKey",
                    idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey
                            : UUID.randomUUID().toString());
            return call(() -> mushe.contributeToBill(shareToken, upstream));
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("fundraiser donate failed: {}", e.getMessage());
            return unavailable("donation could not be captured — nothing was charged");
        }
    }

    // ── Lifecycle actions (Daidzai enforces authz/state; BFF forwards) ──

    @PostMapping("/{id}/request-verification")
    public ResponseEntity<Object> requestVerification(@PathVariable String id,
                                                      @RequestBody Map<String, Object> body) {
        return call(() -> daidzai.assistanceAction(id, "request-verification", body));
    }

    /** Provider attestation — trust plane gates the PROVIDER actor; Daidzai re-checks. */
    @PostMapping("/{id}/attest")
    public ResponseEntity<Object> attest(@PathVariable String id,
                                         @RequestBody Map<String, Object> body) {
        return call(() -> daidzai.assistanceAction(id, "attest", body));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Object> cancel(@PathVariable String id) {
        return call(() -> daidzai.assistanceAction(id, "cancel", Map.of()));
    }

    @PostMapping("/{id}/release")
    public ResponseEntity<Object> release(@PathVariable String id,
                                          @RequestBody(required = false) Map<String, Object> body) {
        return call(() -> daidzai.assistanceAction(id, "release", body == null ? Map.of() : body));
    }

    // ── helpers ──

    private interface Upstream { Object run(); }

    private ResponseEntity<Object> call(Upstream upstream) {
        try {
            return ResponseEntity.ok(upstream.run());
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (Exception e) {
            log.warn("fundraiser upstream failed: {}", e.getMessage());
            return unavailable("service is unavailable right now");
        }
    }

    private UUID resolveWallet(String actorId) {
        if (actorId == null || actorId.isBlank()) {
            return null;
        }
        try {
            JsonNode wallet = mushe.getWalletByOwner("PERSON", actorId);
            if (wallet != null && wallet.hasNonNull("id") && !wallet.get("id").asText().isBlank()) {
                return UUID.fromString(wallet.get("id").asText());
            }
        } catch (Exception e) {
            log.debug("wallet resolution failed for {}: {}", actorId, e.getMessage());
        }
        return null;
    }

    private ResponseEntity<Object> unavailable(String message) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", Map.of("code", "UPSTREAM_UNAVAILABLE", "message", message)));
    }
}
