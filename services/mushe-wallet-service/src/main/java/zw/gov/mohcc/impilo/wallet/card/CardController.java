package zw.gov.mohcc.impilo.wallet.card;

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
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardHealthDataEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.CardUpdateQueueEntity;
import zw.gov.mohcc.impilo.wallet.persistence.entity.WalletEntity;
import zw.gov.mohcc.impilo.wallet.persistence.repository.CardRepository;
import zw.gov.mohcc.impilo.wallet.persistence.repository.WalletRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for smart card lifecycle management.
 *
 * <p>Provides endpoints for card issuance, activation, blocking, replacement,
 * PIN verification, and health data synchronization. Card numbers are always
 * masked in responses (only last 4 digits visible).</p>
 */
@RestController
@RequestMapping("/internal/v1/cards")
public class CardController {

    private static final Logger log = LoggerFactory.getLogger(CardController.class);

    private final CardService cardService;
    private final CardHealthDataService healthDataService;
    private final CardRepository cardRepository;
    private final WalletRepository walletRepository;

    public CardController(CardService cardService,
                          CardHealthDataService healthDataService,
                          CardRepository cardRepository,
                          WalletRepository walletRepository) {
        this.cardService = cardService;
        this.healthDataService = healthDataService;
        this.cardRepository = cardRepository;
        this.walletRepository = walletRepository;
    }

    // ── Issue Card ──────────────────────────────────────────────────────

    @PostMapping
    public ResponseEntity<Map<String, Object>> issueCard(
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        UUID walletId = UUID.fromString(body.get("walletId"));
        String cardType = body.getOrDefault("cardType", "DUAL");
        String cardNetwork = body.getOrDefault("cardNetwork", "ZIMSWITCH");

        CardEntity card = cardService.issueCard(walletId, cardType, cardNetwork, UUID.fromString(tenantId));
        return ResponseEntity.status(HttpStatus.CREATED).body(toCardResponse(card));
    }

    // ── Activate Card ───────────────────────────────────────────────────

    @PostMapping("/{cardId}/activate")
    public ResponseEntity<Map<String, Object>> activateCard(
            @PathVariable UUID cardId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        String pin = body.get("pin");
        if (pin == null || pin.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "PIN is required"));
        }

        CardEntity card = cardService.activateCard(cardId, pin, UUID.fromString(tenantId));
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── Block Card ──────────────────────────────────────────────────────

    @PostMapping("/{cardId}/block")
    public ResponseEntity<Map<String, Object>> blockCard(
            @PathVariable UUID cardId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        String reason = body.getOrDefault("reason", "USER_REQUEST");
        CardEntity card = cardService.blockCard(cardId, reason, UUID.fromString(tenantId));
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── Unblock Card ────────────────────────────────────────────────────

    @PostMapping("/{cardId}/unblock")
    public ResponseEntity<Map<String, Object>> unblockCard(
            @PathVariable UUID cardId,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        CardEntity card = cardService.unblockCard(cardId, UUID.fromString(tenantId));
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── Replace Card ────────────────────────────────────────────────────

    @PostMapping("/{cardId}/replace")
    public ResponseEntity<Map<String, Object>> replaceCard(
            @PathVariable UUID cardId,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        CardEntity newCard = cardService.replaceCard(cardId, UUID.fromString(tenantId));
        return ResponseEntity.status(HttpStatus.CREATED).body(toCardResponse(newCard));
    }

    // ── Verify PIN ──────────────────────────────────────────────────────

    @PostMapping("/{cardId}/verify-pin")
    public ResponseEntity<Map<String, Object>> verifyPin(
            @PathVariable UUID cardId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        String pin = body.get("pin");
        if (pin == null || pin.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "PIN is required"));
        }

        boolean valid = cardService.verifyPin(cardId, pin, UUID.fromString(tenantId));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cardId", cardId.toString());
        response.put("pinValid", valid);
        return ResponseEntity.ok(response);
    }

    // ── Change PIN ──────────────────────────────────────────────────────

    @PutMapping("/{cardId}/change-pin")
    public ResponseEntity<Map<String, Object>> changePin(
            @PathVariable UUID cardId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        String currentPin = body.get("currentPin");
        String newPin = body.get("newPin");
        if (currentPin == null || currentPin.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "currentPin is required"));
        }
        if (newPin == null || newPin.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPin is required"));
        }

        CardEntity card = cardService.changePin(cardId, currentPin, newPin, UUID.fromString(tenantId));
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── Reset PIN (Admin/Supervisor) ───────────────────────────────────

    @PostMapping("/{cardId}/reset-pin")
    public ResponseEntity<Map<String, Object>> resetPin(
            @PathVariable UUID cardId,
            @RequestBody Map<String, String> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        String newPin = body.get("newPin");
        if (newPin == null || newPin.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "newPin is required"));
        }

        CardEntity card = cardService.resetPin(cardId, newPin, UUID.fromString(tenantId));
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── Get Card Details ────────────────────────────────────────────────

    @GetMapping("/{cardId}")
    public ResponseEntity<Map<String, Object>> getCard(@PathVariable UUID cardId) {
        CardEntity card = cardService.findCardOrThrow(cardId);
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── List Cards by Wallet ────────────────────────────────────────────

    @GetMapping("/by-wallet/{walletId}")
    public ResponseEntity<Map<String, Object>> getCardsByWallet(@PathVariable UUID walletId) {
        List<CardEntity> cards = cardRepository.findByWalletId(walletId);
        List<Map<String, Object>> cardList = cards.stream()
                .map(this::toCardResponse)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("walletId", walletId.toString());
        response.put("cards", cardList);
        response.put("count", cardList.size());
        return ResponseEntity.ok(response);
    }

    // ── PHR-Carry Function Opt-In (Unified SMART card, Phase 4) ─────────

    /**
     * Set the cardholder's opt-in to the card's PHR-carry function. Fail-closed: until enabled,
     * no clinical summary is ever cached on the card. Body: {@code {"enabled": true|false}}.
     */
    @PutMapping("/{cardId}/phr-carry")
    public ResponseEntity<Map<String, Object>> setPhrCarry(
            @PathVariable UUID cardId,
            @RequestBody Map<String, Object> body,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        Object enabledRaw = body.get("enabled");
        if (enabledRaw == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "enabled (boolean) is required"));
        }
        boolean enabled = Boolean.parseBoolean(enabledRaw.toString());
        CardEntity card = cardService.setPhrCarry(cardId, enabled, UUID.fromString(tenantId));
        return ResponseEntity.ok(toCardResponse(card));
    }

    // ── Health Data Status ──────────────────────────────────────────────

    @GetMapping("/{cardId}/health-data")
    public ResponseEntity<Map<String, Object>> getHealthDataStatus(@PathVariable UUID cardId) {
        // Verify card exists
        cardService.findCardOrThrow(cardId);

        List<CardHealthDataEntity> healthData = healthDataService.getAllCardHealthData(cardId);
        List<Map<String, Object>> items = healthData.stream()
                .map(this::toHealthDataMetadata)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cardId", cardId.toString());
        response.put("healthData", items);
        return ResponseEntity.ok(response);
    }

    // ── Trigger Health Data Sync ────────────────────────────────────────

    @PostMapping("/{cardId}/health-data/sync")
    public ResponseEntity<Map<String, Object>> triggerHealthDataSync(
            @PathVariable UUID cardId,
            @RequestHeader("X-Tenant-ID") String tenantId) {

        CardEntity card = cardService.findCardOrThrow(cardId);

        // Resolve the real patient CPID from the card's wallet owner_ref. Health-data
        // sync only applies to an individual (patient) wallet — a merchant/provider
        // wallet has no critical health summary, so reject rather than queue a bogus job.
        WalletEntity wallet = walletRepository.findByWalletId(card.getWalletId())
                .orElseThrow(() -> new IllegalStateException(
                        "Wallet not found for card " + cardId + " (walletId=" + card.getWalletId() + ")"));
        if (!"INDIVIDUAL".equalsIgnoreCase(wallet.getOwnerType())) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "error", "Health-data sync is only supported for individual (patient) wallets",
                    "ownerType", String.valueOf(wallet.getOwnerType())));
        }
        String patientCpid = wallet.getOwnerRef();
        if (patientCpid == null || patientCpid.isBlank()) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "error", "Wallet has no owner_ref (patient CPID) to sync health data for"));
        }
        // Fail-closed consent gate: a manual sync must not bypass the PHR-carry opt-in either.
        if (!card.isPhrEnabled()) {
            return ResponseEntity.unprocessableEntity().body(Map.of(
                    "error", "Card has not opted into the PHR-carry function; enable it before syncing health data",
                    "code", "PHR_CARRY_NOT_ENABLED"));
        }
        String encryptionKeyRef = "tshepo-keys:" + card.getTenantId() + ":patient:" + patientCpid;

        // Queue a CRITICAL_SUMMARY sync. The real summary is fetched from BUTANO at
        // card-sync time; mark it PENDING_SYNC (not "{}", which a reader could mistake
        // for "no critical health data" — no allergies/conditions — on a health card).
        healthDataService.updateCriticalSummary(
                cardId, patientCpid, "{\"status\":\"PENDING_SYNC\"}", encryptionKeyRef,
                UUID.fromString(tenantId));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cardId", cardId.toString());
        response.put("patientCpid", patientCpid);
        response.put("status", "QUEUED");
        response.put("message", "Health data sync queued for processing");
        return ResponseEntity.accepted().body(response);
    }

    // ── Pending Updates ─────────────────────────────────────────────────

    @GetMapping("/{cardId}/pending-updates")
    public ResponseEntity<Map<String, Object>> getPendingUpdates(@PathVariable UUID cardId) {
        // Verify card exists
        cardService.findCardOrThrow(cardId);

        List<CardUpdateQueueEntity> pending = healthDataService.getPendingUpdates(cardId);
        List<Map<String, Object>> items = pending.stream()
                .map(this::toQueueEntryResponse)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cardId", cardId.toString());
        response.put("pendingUpdates", items);
        response.put("count", items.size());
        return ResponseEntity.ok(response);
    }

    // ── Response Mappers ────────────────────────────────────────────────

    private Map<String, Object> toCardResponse(CardEntity card) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("cardId", card.getCardId().toString());
        map.put("walletId", card.getWalletId().toString());
        map.put("maskedCardNumber", CardService.maskCardNumber(card.getCardNumber()));
        map.put("cardType", card.getCardType());
        map.put("cardNetwork", card.getCardNetwork());
        map.put("expiryMonth", card.getExpiryMonth());
        map.put("expiryYear", card.getExpiryYear());
        map.put("status", card.getStatus());
        map.put("phrEnabled", card.isPhrEnabled());
        map.put("phrConsentAt", card.getPhrConsentAt());
        map.put("pinTriesRemaining", card.getPinTriesRemaining());
        map.put("healthAppletVersion", card.getHealthAppletVersion());
        map.put("healthDataUpdatedAt", card.getHealthDataUpdatedAt());
        map.put("ipsVersion", card.getIpsVersion());
        map.put("ipsUpdatedAt", card.getIpsUpdatedAt());
        map.put("activatedAt", card.getActivatedAt());
        map.put("blockedAt", card.getBlockedAt());
        map.put("blockedReason", card.getBlockedReason());
        map.put("replacedBy", card.getReplacedBy());
        map.put("issuedAt", card.getIssuedAt());
        return map;
    }

    private Map<String, Object> toHealthDataMetadata(CardHealthDataEntity hd) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("dataType", hd.getDataType());
        map.put("version", hd.getVersion());
        map.put("sizeBytes", hd.getSizeBytes());
        map.put("generatedAt", hd.getGeneratedAt());
        map.put("syncedToCardAt", hd.getSyncedToCardAt());
        map.put("contentHash", hd.getContentHash());
        map.put("compression", hd.getCompression());
        return map;
    }

    private Map<String, Object> toQueueEntryResponse(CardUpdateQueueEntity q) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("queueId", q.getQueueId().toString());
        map.put("updateType", q.getUpdateType());
        map.put("status", q.getStatus());
        map.put("priority", q.getPriority());
        map.put("attempts", q.getAttempts());
        map.put("lastAttemptAt", q.getLastAttemptAt());
        map.put("createdAt", q.getCreatedAt());
        return map;
    }
}
