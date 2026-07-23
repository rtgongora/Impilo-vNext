package zw.gov.mohcc.impilo.oros.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.oros.domain.PrescriptionStatus;
import zw.gov.mohcc.impilo.oros.domain.RequestSource;
import zw.gov.mohcc.impilo.oros.integration.TshepoKeysClient;
import zw.gov.mohcc.impilo.oros.persistence.entity.*;
import zw.gov.mohcc.impilo.oros.persistence.repository.*;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * The prescription aggregate (OF-B2, Vol II §8.2/§9.2/§13.2).
 *
 * <p>Doctrine encoded here:</p>
 * <ul>
 *   <li>Signed content is immutable by construction — amendment = new version.</li>
 *   <li>Activation is atomic with signature persistence (no signed-but-inactive state).</li>
 *   <li>The token proves the right to retrieve and claim; NO clinical content in the token.</li>
 *   <li>Single-active token per prescription; amendment rotates it atomically.</li>
 *   <li>The server-side repeats counter is the ONLY decrement path; claims are atomic
 *       under a pessimistic row lock; release is an audited compensation.</li>
 * </ul>
 */
@Service
public class PrescriptionService {

    private static final Logger log = LoggerFactory.getLogger(PrescriptionService.class);

    private final PrescriptionRepository prescriptionRepository;
    private final PrescriptionVersionRepository versionRepository;
    private final PrescriptionItemRepository itemRepository;
    private final PrescriptionTokenRepository tokenRepository;
    private final PrescriptionClaimRepository claimRepository;
    private final EventOutboxRepository outboxRepository;
    private final TshepoKeysClient tshepoKeysClient;
    private final ObjectMapper objectMapper;
    private final int tokenTtlHours;

    public PrescriptionService(PrescriptionRepository prescriptionRepository,
                               PrescriptionVersionRepository versionRepository,
                               PrescriptionItemRepository itemRepository,
                               PrescriptionTokenRepository tokenRepository,
                               PrescriptionClaimRepository claimRepository,
                               EventOutboxRepository outboxRepository,
                               TshepoKeysClient tshepoKeysClient,
                               ObjectMapper objectMapper,
                               @Value("${oros.prescriptions.token-ttl-hours:72}") int tokenTtlHours) {
        this.prescriptionRepository = prescriptionRepository;
        this.versionRepository = versionRepository;
        this.itemRepository = itemRepository;
        this.tokenRepository = tokenRepository;
        this.claimRepository = claimRepository;
        this.outboxRepository = outboxRepository;
        this.tshepoKeysClient = tshepoKeysClient;
        this.objectMapper = objectMapper;
        this.tokenTtlHours = tokenTtlHours;
    }

    // ── Data records ─────────────────────────────────────────────────────

    public record ItemData(String medicationCode, String codingSystem, String displayName,
                           String dose, String route, String frequency, Integer durationDays,
                           BigDecimal quantity, String quantityUnit, String instructions,
                           boolean controlled, Boolean substitutionPermitted) {}

    public record CreateData(String patientCpid, String prescriberId, String prescriberName,
                             String encounterRef, RequestSource requestSource, String sourceRef,
                             int repeatsCeiling, LocalDate validityStart, LocalDate validityEnd,
                             boolean substitutionPermitted, String indication, List<ItemData> items) {}

    public record ClaimResult(PrescriptionClaimEntity claim, PrescriptionEntity prescription,
                              List<PrescriptionItemEntity> lines, boolean duplicateReplay) {}

    // ── Authoring ────────────────────────────────────────────────────────

    /** Author a DRAFT prescription: parent + unsigned version 1 + coded items. */
    @Transactional
    public PrescriptionEntity create(CreateData data) {
        TrustContext ctx = TrustContextHolder.require();
        if (data.items() == null || data.items().isEmpty()) {
            throw new OrosDomainException("PRESCRIPTION_ITEMS_REQUIRED", 400,
                    "A prescription requires at least one coded medication line.");
        }

        PrescriptionEntity rx = new PrescriptionEntity();
        rx.setPrescriptionId(OrderStateMachine.generateUlid());
        rx.setTenantId(ctx.tenantId());
        rx.setFacilityId(ctx.facilityId());
        rx.setPatientCpid(data.patientCpid());
        rx.setPrescriberId(data.prescriberId() != null ? data.prescriberId() : ctx.actorId());
        rx.setPrescriberName(data.prescriberName());
        rx.setEncounterRef(data.encounterRef());
        rx.setRequestSource(data.requestSource() != null ? data.requestSource() : RequestSource.INTERNAL);
        rx.setSourceRef(data.sourceRef());
        rx.setStatus(PrescriptionStatus.DRAFT);
        int ceiling = Math.max(1, data.repeatsCeiling());
        rx.setRepeatsCeiling(ceiling);
        rx.setRepeatsRemaining(ceiling);
        rx.setValidityStart(data.validityStart());
        rx.setValidityEnd(data.validityEnd());
        rx.setControlled(data.items().stream().anyMatch(ItemData::controlled));
        rx.setSubstitutionPermitted(data.substitutionPermitted());
        rx.setIndication(data.indication());
        rx.setCreatedBy(ctx.actorId());
        rx = prescriptionRepository.save(rx);

        createVersion(rx, data.items(), 1, null, "Prescription authored", ctx.actorId());

        publishEvent(rx.getPrescriptionId(), "PRESCRIPTION_CREATED", rx, ctx.tenantId());
        log.info("Prescription created: id={}, patient={}, controlled={}",
                rx.getPrescriptionId(), rx.getPatientCpid(), rx.isControlled());
        return rx;
    }

    /** Replace the unsigned head version's content while still in DRAFT. */
    @Transactional
    public PrescriptionVersionEntity updateDraft(String prescriptionId, List<ItemData> items) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity rx = get(prescriptionId);
        if (rx.getStatus() != PrescriptionStatus.DRAFT) {
            throw new OrosDomainException("PRESCRIPTION_NOT_DRAFT", 409,
                    "Prescription " + prescriptionId + " is not a draft (status=" + rx.getStatus() + ")");
        }
        PrescriptionVersionEntity head = head(prescriptionId);
        // Unsigned draft head: content may be rewritten (nothing is immutable until signed).
        itemRepository.deleteAll(itemRepository.findByPrescriptionVersionId(head.getPrescriptionVersionId()));
        persistItems(head.getPrescriptionVersionId(), items);
        String canonical = canonicalContent(rx, items);
        head.setContentJson(canonical);
        head.setContentHash(OrderVersionWriter.sha256Hex(canonical));
        rx.setControlled(items.stream().anyMatch(ItemData::controlled));
        prescriptionRepository.save(rx);
        return versionRepository.save(head);
    }

    /**
     * Amend a SIGNED_ACTIVE prescription: creates the next UNSIGNED version. The
     * amendment only becomes claimable when signed ({@link #signAndActivate}), which
     * atomically supersedes the predecessor and rotates the token (§13.2.1).
     */
    @Transactional
    public PrescriptionVersionEntity amend(String prescriptionId, List<ItemData> items, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity rx = get(prescriptionId);
        requireReason(reason);
        if (rx.getStatus() != PrescriptionStatus.SIGNED_ACTIVE) {
            throw new OrosDomainException("PRESCRIPTION_NOT_AMENDABLE", 409,
                    "Prescription " + prescriptionId + " cannot be amended in status " + rx.getStatus());
        }
        PrescriptionVersionEntity head = head(prescriptionId);
        if (!head.isSigned()) {
            throw new OrosDomainException("AMENDMENT_PENDING_SIGNATURE", 409,
                    "An unsigned amendment already exists — sign or discard it first.");
        }
        PrescriptionVersionEntity next = createVersion(
                rx, items, head.getVersion() + 1, head.getPrescriptionVersionId(), reason, ctx.actorId());
        rx.setLifecycleReason(reason);
        prescriptionRepository.save(rx);
        publishEvent(prescriptionId, "PRESCRIPTION_AMENDED", versionPayload(rx, next), ctx.tenantId());
        return next;
    }

    // ── Signing + activation (§8.2: atomic) ──────────────────────────────

    /**
     * Sign the unsigned head version via tshepo-keys (detached JWS over the canonical
     * content hash) and activate it atomically: prescription → SIGNED_ACTIVE, current
     * version pinned, predecessor token revoked, successor token minted. There is no
     * signed-but-not-activated state (§8.2.3).
     */
    @Transactional
    public PrescriptionEntity signAndActivate(String prescriptionId) {
        TrustContext ctx = TrustContextHolder.require();
        PrescriptionEntity rx = get(prescriptionId);
        if (rx.getStatus() != PrescriptionStatus.DRAFT && rx.getStatus() != PrescriptionStatus.SIGNED_ACTIVE) {
            throw new OrosDomainException("PRESCRIPTION_NOT_SIGNABLE", 409,
                    "Prescription " + prescriptionId + " cannot be signed in status " + rx.getStatus());
        }
        PrescriptionVersionEntity head = head(prescriptionId);
        if (head.isSigned()) {
            throw new OrosDomainException("VERSION_ALREADY_SIGNED", 409,
                    "Version " + head.getVersion() + " is already signed — amend to change content.");
        }

        // Detached JWS: the signed payload carries the version id + content hash, never
        // the clinical content; verification recomputes the canonical hash and checks it.
        ObjectNode signingPayload = objectMapper.createObjectNode();
        signingPayload.put("prescription_version_id", head.getPrescriptionVersionId());
        signingPayload.put("content_hash", head.getContentHash());
        signingPayload.put("prescriber_id", rx.getPrescriberId());
        signingPayload.put("tenant_id", ctx.tenantId().toString());
        TshepoKeysClient.SignedJws signed = tshepoKeysClient.signJws(ctx.tenantId(), signingPayload.toString());

        head.setSignatureJws(signed.jws());
        head.setSignatureKeyId(signed.keyId());
        head.setSignedBy(ctx.actorId());
        head.setSignedAt(OffsetDateTime.now());
        versionRepository.save(head);

        boolean amendment = head.getSupersedesVersionId() != null;
        rx.setStatus(PrescriptionStatus.SIGNED_ACTIVE);
        rx.setCurrentVersionId(head.getPrescriptionVersionId());
        prescriptionRepository.save(rx);

        // Single-active token invariant: revoke the predecessor's token in the same
        // transaction that mints the successor's (§13.2.1 — defeats cross-version reuse).
        // The flush between them is load-bearing: Hibernate orders INSERTs before
        // UPDATEs at flush time, so without it the successor's INSERT hits the
        // single-active partial unique index while the predecessor is still ACTIVE
        // (caught live by rig J-OO-5 — invisible to mocked unit tests).
        tokenRepository.findByPrescriptionIdAndStatus(prescriptionId, PrescriptionTokenEntity.STATUS_ACTIVE)
                .ifPresent(t -> revokeToken(t, amendment ? "SUPERSEDED" : "REISSUED"));
        tokenRepository.flush();
        PrescriptionTokenEntity token = mintToken(rx, head);

        publishEvent(prescriptionId, "PRESCRIPTION_SIGNED", versionPayload(rx, head), ctx.tenantId());
        publishEvent(prescriptionId, "PRESCRIPTION_ACTIVATED", activationPayload(rx, head, token), ctx.tenantId());
        log.info("Prescription signed+activated: id={}, version={}, keyId={}",
                prescriptionId, head.getVersion(), signed.keyId());
        return rx;
    }

    // ── Claim (§13.2.3: atomic, server-side counter) ─────────────────────

    /**
     * Claim a prescription by token: verify → decrement the server-side repeats
     * counter → record the claim — atomically under a pessimistic row lock. A claim
     * against anything but the current SIGNED_ACTIVE version fails (RC-7).
     */
    @Transactional
    public ClaimResult claim(String tokenJws, String idempotencyKey, String vendorRef) {
        TrustContext ctx = TrustContextHolder.require();

        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Optional<PrescriptionClaimEntity> replay =
                    claimRepository.findByTenantIdAndIdempotencyKey(ctx.tenantId(), idempotencyKey);
            if (replay.isPresent()) {
                PrescriptionClaimEntity existing = replay.get();
                PrescriptionEntity rx = get(existing.getPrescriptionId());
                return new ClaimResult(existing, rx,
                        itemRepository.findByPrescriptionVersionId(existing.getPrescriptionVersionId()), true);
            }
        }

        PrescriptionTokenEntity token = resolveToken(tokenJws);

        if (!PrescriptionTokenEntity.STATUS_ACTIVE.equals(token.getStatus())) {
            throw new OrosDomainException("TOKEN_REVOKED", 409,
                    "This prescription token is no longer claimable (" + token.getStatus() + ").");
        }
        if (token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now())) {
            revokeTokenAsExpired(token);
            throw new OrosDomainException("TOKEN_EXPIRED", 409,
                    "This prescription token has expired — a new token can be issued within prescription validity.");
        }

        // Lock the parent row: the counter decrement and claim insert serialise here.
        PrescriptionEntity rx = prescriptionRepository.findForClaim(token.getPrescriptionId())
                .orElseThrow(() -> new OrosDomainException("PRESCRIPTION_NOT_FOUND", 404, "Prescription not found"));
        if (!rx.getTenantId().equals(ctx.tenantId())) {
            throw new OrosDomainException("PRESCRIPTION_NOT_FOUND", 404, "Prescription not found");
        }

        expireIfLapsed(rx);
        if (rx.getStatus() != PrescriptionStatus.SIGNED_ACTIVE) {
            throw new OrosDomainException("PRESCRIPTION_NOT_CLAIMABLE", 409,
                    "Prescription is not claimable (status=" + rx.getStatus() + ").");
        }
        if (!token.getPrescriptionVersionId().equals(rx.getCurrentVersionId())) {
            // RC-7: token bound to a superseded version — amendment rotated the token.
            throw new OrosDomainException("VERSION_SUPERSEDED", 409,
                    "This token refers to a superseded prescription version — request the current token.");
        }
        if (rx.getRepeatsRemaining() <= 0) {
            throw new OrosDomainException("REPEATS_EXHAUSTED", 409, "All repeats have been dispensed.");
        }

        rx.setRepeatsRemaining(rx.getRepeatsRemaining() - 1);
        boolean exhausted = rx.getRepeatsRemaining() == 0;
        if (exhausted) {
            rx.setStatus(PrescriptionStatus.EXHAUSTED);
            revokeToken(token, "EXHAUSTED");
        }
        prescriptionRepository.save(rx);

        PrescriptionClaimEntity claim = new PrescriptionClaimEntity();
        claim.setTenantId(ctx.tenantId());
        claim.setTokenId(token.getTokenId());
        claim.setPrescriptionId(rx.getPrescriptionId());
        claim.setPrescriptionVersionId(token.getPrescriptionVersionId());
        claim.setClaimedByActor(ctx.actorId());
        claim.setClaimedByFacility(ctx.facilityId());
        claim.setVendorRef(vendorRef);
        claim.setIdempotencyKey(idempotencyKey != null && !idempotencyKey.isBlank() ? idempotencyKey : null);
        try {
            claim = claimRepository.save(claim);
            claimRepository.flush();
        } catch (DataIntegrityViolationException e) {
            // Idempotency-key race: the concurrent twin committed first — return its result.
            PrescriptionClaimEntity existing = claimRepository
                    .findByTenantIdAndIdempotencyKey(ctx.tenantId(), idempotencyKey)
                    .orElseThrow(() -> e);
            return new ClaimResult(existing, rx,
                    itemRepository.findByPrescriptionVersionId(existing.getPrescriptionVersionId()), true);
        }

        publishEvent(rx.getPrescriptionId(), "PRESCRIPTION_CLAIMED",
                claimPayload(rx, claim, exhausted), ctx.tenantId());
        log.info("Prescription claimed: id={}, claim={}, remaining={}, exhausted={}",
                rx.getPrescriptionId(), claim.getClaimId(), rx.getRepeatsRemaining(), exhausted);
        return new ClaimResult(claim, rx,
                itemRepository.findByPrescriptionVersionId(token.getPrescriptionVersionId()), false);
    }

    /**
     * Compensation (§9.2): a cancelled/failed episode before dispensing restores the
     * counter with an audited event. Re-activates an EXHAUSTED prescription and
     * re-mints its token when the restored repeat makes it claimable again.
     */
    @Transactional
    public PrescriptionClaimEntity releaseClaim(UUID claimId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        requireReason(reason);
        PrescriptionClaimEntity claim = claimRepository.findByTenantIdAndClaimId(ctx.tenantId(), claimId)
                .orElseThrow(() -> new OrosDomainException("CLAIM_NOT_FOUND", 404, "Claim not found"));
        if (!PrescriptionClaimEntity.STATUS_CLAIMED.equals(claim.getStatus())) {
            throw new OrosDomainException("CLAIM_NOT_RELEASABLE", 409,
                    "Claim is not in CLAIMED state (" + claim.getStatus() + ").");
        }

        PrescriptionEntity rx = prescriptionRepository.findForClaim(claim.getPrescriptionId())
                .orElseThrow(() -> new OrosDomainException("PRESCRIPTION_NOT_FOUND", 404, "Prescription not found"));

        claim.setStatus(PrescriptionClaimEntity.STATUS_RELEASED);
        claim.setReleasedAt(OffsetDateTime.now());
        claim.setReleaseReason(reason);
        claimRepository.save(claim);

        rx.setRepeatsRemaining(Math.min(rx.getRepeatsCeiling(), rx.getRepeatsRemaining() + 1));
        if (rx.getStatus() == PrescriptionStatus.EXHAUSTED) {
            rx.setStatus(PrescriptionStatus.SIGNED_ACTIVE);
            PrescriptionVersionEntity current = versionRepository.findById(rx.getCurrentVersionId())
                    .orElseThrow(() -> new OrosDomainException("VERSION_NOT_FOUND", 500, "Current version missing"));
            if (tokenRepository.findByPrescriptionIdAndStatus(
                    rx.getPrescriptionId(), PrescriptionTokenEntity.STATUS_ACTIVE).isEmpty()) {
                mintToken(rx, current);
            }
        }
        prescriptionRepository.save(rx);

        publishEvent(rx.getPrescriptionId(), "PRESCRIPTION_CLAIM_RELEASED",
                claimPayload(rx, claim, false), ctx.tenantId());
        log.info("Prescription claim released (compensation): claim={}, remaining restored to {}",
                claimId, rx.getRepeatsRemaining());
        return claim;
    }

    // ── Cancel / verify / queries ────────────────────────────────────────

    /** Prescriber cancel — unclaimed remainder only; dispensed episodes stand (§9.2). */
    @Transactional
    public PrescriptionEntity cancel(String prescriptionId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        requireReason(reason);
        PrescriptionEntity rx = get(prescriptionId);
        if (rx.getStatus() != PrescriptionStatus.DRAFT && rx.getStatus() != PrescriptionStatus.SIGNED_ACTIVE) {
            throw new OrosDomainException("PRESCRIPTION_NOT_CANCELLABLE", 409,
                    "Prescription cannot be cancelled in status " + rx.getStatus());
        }
        rx.setStatus(PrescriptionStatus.CANCELLED);
        rx.setLifecycleReason(reason);
        prescriptionRepository.save(rx);
        tokenRepository.findByPrescriptionIdAndStatus(prescriptionId, PrescriptionTokenEntity.STATUS_ACTIVE)
                .ifPresent(t -> revokeToken(t, "CANCELLED"));
        publishEvent(prescriptionId, "PRESCRIPTION_CANCELLED", rx, ctx.tenantId());
        return rx;
    }

    /** Dispenser pre-check: token standing WITHOUT claiming (no counter effect). */
    @Transactional(readOnly = true)
    public Map<String, Object> verifyToken(String tokenJws) {
        try {
            PrescriptionTokenEntity token = resolveToken(tokenJws);
            PrescriptionEntity rx = get(token.getPrescriptionId());
            boolean expired = token.getExpiresAt() != null && token.getExpiresAt().isBefore(OffsetDateTime.now());
            boolean current = token.getPrescriptionVersionId().equals(rx.getCurrentVersionId());
            boolean active = PrescriptionTokenEntity.STATUS_ACTIVE.equals(token.getStatus());
            boolean claimable = active && !expired && current
                    && rx.getStatus() == PrescriptionStatus.SIGNED_ACTIVE && rx.getRepeatsRemaining() > 0;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("valid", claimable);
            result.put("tokenStatus", expired && active ? "EXPIRED" : token.getStatus());
            result.put("prescriptionStatus", rx.getStatus().name());
            result.put("currentVersion", current);
            result.put("repeatsRemaining", rx.getRepeatsRemaining());
            result.put("controlled", rx.isControlled());
            return result;
        } catch (OrosDomainException e) {
            return Map.of("valid", false, "tokenStatus", "UNRECOGNISED", "reason", e.getCode());
        }
    }

    public PrescriptionEntity get(String prescriptionId) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return prescriptionRepository.findByTenantIdAndPrescriptionId(tenantId, prescriptionId)
                .orElseThrow(() -> new OrosDomainException("PRESCRIPTION_NOT_FOUND", 404,
                        "Prescription not found: " + prescriptionId));
    }

    public List<PrescriptionEntity> forPatient(String patientCpid) {
        UUID tenantId = TrustContextHolder.require().tenantId();
        return prescriptionRepository.findByTenantIdAndPatientCpidOrderByCreatedAtDesc(tenantId, patientCpid);
    }

    public List<PrescriptionVersionEntity> versions(String prescriptionId) {
        get(prescriptionId); // tenant guard
        return versionRepository.findByPrescriptionIdOrderByVersionDesc(prescriptionId);
    }

    public List<PrescriptionItemEntity> itemsOfVersion(String versionId) {
        return itemRepository.findByPrescriptionVersionId(versionId);
    }

    public Optional<PrescriptionTokenEntity> activeToken(String prescriptionId) {
        get(prescriptionId); // tenant guard
        return tokenRepository.findByPrescriptionIdAndStatus(prescriptionId, PrescriptionTokenEntity.STATUS_ACTIVE);
    }

    // ── Expiry sweep (§9.2 EXPIRED) ──────────────────────────────────────

    /** Lazy expiry on touch + daily sweep; no claim may ride a lapsed validity window. */
    @Scheduled(cron = "${oros.prescriptions.expiry-sweep-cron:0 15 2 * * *}")
    @Transactional
    public void expirySweep() {
        List<PrescriptionEntity> all = prescriptionRepository.findAll();
        int expired = 0;
        for (PrescriptionEntity rx : all) {
            if (rx.getStatus() == PrescriptionStatus.SIGNED_ACTIVE && lapsed(rx)) {
                rx.setStatus(PrescriptionStatus.EXPIRED);
                prescriptionRepository.save(rx);
                tokenRepository.findByPrescriptionIdAndStatus(
                                rx.getPrescriptionId(), PrescriptionTokenEntity.STATUS_ACTIVE)
                        .ifPresent(t -> revokeToken(t, "PRESCRIPTION_EXPIRED"));
                publishEvent(rx.getPrescriptionId(), "PRESCRIPTION_EXPIRED", rx, rx.getTenantId());
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Prescription expiry sweep: {} prescriptions expired", expired);
        }
    }

    // ── Internals ────────────────────────────────────────────────────────

    private void expireIfLapsed(PrescriptionEntity rx) {
        if (rx.getStatus() == PrescriptionStatus.SIGNED_ACTIVE && lapsed(rx)) {
            rx.setStatus(PrescriptionStatus.EXPIRED);
            prescriptionRepository.save(rx);
            tokenRepository.findByPrescriptionIdAndStatus(
                            rx.getPrescriptionId(), PrescriptionTokenEntity.STATUS_ACTIVE)
                    .ifPresent(t -> revokeToken(t, "PRESCRIPTION_EXPIRED"));
            publishEvent(rx.getPrescriptionId(), "PRESCRIPTION_EXPIRED", rx, rx.getTenantId());
        }
    }

    private static boolean lapsed(PrescriptionEntity rx) {
        return rx.getValidityEnd() != null && rx.getValidityEnd().isBefore(LocalDate.now());
    }

    private PrescriptionVersionEntity head(String prescriptionId) {
        return versionRepository.findFirstByPrescriptionIdOrderByVersionDesc(prescriptionId)
                .orElseThrow(() -> new OrosDomainException("VERSION_NOT_FOUND", 500,
                        "Prescription " + prescriptionId + " has no versions"));
    }

    private PrescriptionVersionEntity createVersion(PrescriptionEntity rx, List<ItemData> items,
                                                    int versionNumber, String supersedes,
                                                    String reason, String actorId) {
        PrescriptionVersionEntity version = new PrescriptionVersionEntity();
        version.setPrescriptionVersionId(OrderStateMachine.generateUlid());
        version.setPrescriptionId(rx.getPrescriptionId());
        version.setTenantId(rx.getTenantId());
        version.setVersion(versionNumber);
        version.setSupersedesVersionId(supersedes);
        String canonical = canonicalContent(rx, items);
        version.setContentJson(canonical);
        version.setContentHash(OrderVersionWriter.sha256Hex(canonical));
        version.setReason(reason);
        version.setCreatedBy(actorId);
        try {
            version = versionRepository.save(version);
            versionRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new OrosDomainException("AMEND_CONFLICT", 409,
                    "Prescription " + rx.getPrescriptionId() + " was amended concurrently — reload and retry.");
        }
        persistItems(version.getPrescriptionVersionId(), items);
        return version;
    }

    private void persistItems(String versionId, List<ItemData> items) {
        for (ItemData d : items) {
            if (d.medicationCode() == null || d.medicationCode().isBlank()) {
                throw new OrosDomainException("MEDICATION_CODE_REQUIRED", 400,
                        "Every prescription line requires a coded medication reference (no free-text products).");
            }
            PrescriptionItemEntity item = new PrescriptionItemEntity();
            item.setPrescriptionVersionId(versionId);
            item.setMedicationCode(d.medicationCode());
            if (d.codingSystem() != null && !d.codingSystem().isBlank()) {
                item.setCodingSystem(d.codingSystem());
            }
            item.setDisplayName(d.displayName());
            item.setDose(d.dose());
            item.setRoute(d.route());
            item.setFrequency(d.frequency());
            item.setDurationDays(d.durationDays());
            item.setQuantity(d.quantity());
            item.setQuantityUnit(d.quantityUnit());
            item.setInstructions(d.instructions());
            item.setControlled(d.controlled());
            item.setSubstitutionPermitted(d.substitutionPermitted() == null || d.substitutionPermitted());
            itemRepository.save(item);
        }
    }

    /** Mint the single-active token: compact JWS over an OPAQUE payload — no clinical content (§13.2.2). */
    private PrescriptionTokenEntity mintToken(PrescriptionEntity rx, PrescriptionVersionEntity version) {
        String tokenId = OrderStateMachine.generateUlid();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(tokenTtlHours);

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("token_id", tokenId);
        payload.put("prescription_version_id", version.getPrescriptionVersionId());
        payload.put("issued_at", now.toString());
        payload.put("expires_at", expiresAt.toString());
        TshepoKeysClient.SignedJws signed = tshepoKeysClient.signJws(rx.getTenantId(), payload.toString());

        PrescriptionTokenEntity token = new PrescriptionTokenEntity();
        token.setTokenId(tokenId);
        token.setPrescriptionId(rx.getPrescriptionId());
        token.setPrescriptionVersionId(version.getPrescriptionVersionId());
        token.setTenantId(rx.getTenantId());
        token.setStatus(PrescriptionTokenEntity.STATUS_ACTIVE);
        token.setTokenJws(signed.jws());
        token.setKeyId(signed.keyId());
        token.setIssuedAt(now);
        token.setExpiresAt(expiresAt);
        return tokenRepository.save(token);
    }

    private void revokeToken(PrescriptionTokenEntity token, String reason) {
        token.setStatus(PrescriptionTokenEntity.STATUS_REVOKED);
        token.setRevokedAt(OffsetDateTime.now());
        token.setRevokeReason(reason);
        tokenRepository.save(token);
    }

    private void revokeTokenAsExpired(PrescriptionTokenEntity token) {
        token.setStatus(PrescriptionTokenEntity.STATUS_EXPIRED);
        token.setRevokedAt(OffsetDateTime.now());
        token.setRevokeReason("TTL_LAPSED");
        tokenRepository.save(token);
    }

    /**
     * Resolve a presented token JWS to its stored row: parse the JWS payload for the
     * opaque {@code token_id}, then require exact match with the minted JWS string.
     * (The server issued the token, so string equality is the strongest check; offline
     * verifiers use the published JWKS instead.)
     */
    private PrescriptionTokenEntity resolveToken(String tokenJws) {
        if (tokenJws == null || tokenJws.isBlank()) {
            throw new OrosDomainException("TOKEN_REQUIRED", 400, "A prescription token is required.");
        }
        String tokenId;
        try {
            String[] parts = tokenJws.trim().split("\\.");
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            JsonNode payload = objectMapper.readTree(payloadJson);
            tokenId = payload.path("token_id").asText("");
        } catch (Exception e) {
            throw new OrosDomainException("TOKEN_MALFORMED", 400, "The presented token is not a valid JWS.");
        }
        if (tokenId.isBlank()) {
            throw new OrosDomainException("TOKEN_MALFORMED", 400, "The presented token carries no token id.");
        }
        PrescriptionTokenEntity token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new OrosDomainException("TOKEN_NOT_FOUND", 404, "Unknown prescription token."));
        if (!token.getTokenJws().trim().equals(tokenJws.trim())) {
            // Same token id but different bytes — a forgery or tamper attempt; audit-relevant.
            log.warn("Prescription token mismatch for tokenId={} — possible tampering", tokenId);
            throw new OrosDomainException("TOKEN_INVALID", 403, "The presented token failed verification.");
        }
        return token;
    }

    private String canonicalContent(PrescriptionEntity rx, List<ItemData> items) {
        Map<String, Object> content = new TreeMap<>();
        content.put("prescriptionId", rx.getPrescriptionId());
        content.put("patientCpid", rx.getPatientCpid());
        content.put("prescriberId", rx.getPrescriberId());
        content.put("encounterRef", rx.getEncounterRef());
        content.put("requestSource", rx.getRequestSource() != null ? rx.getRequestSource().name() : null);
        content.put("sourceRef", rx.getSourceRef());
        content.put("repeatsCeiling", rx.getRepeatsCeiling());
        content.put("validityStart", rx.getValidityStart() != null ? rx.getValidityStart().toString() : null);
        content.put("validityEnd", rx.getValidityEnd() != null ? rx.getValidityEnd().toString() : null);
        content.put("substitutionPermitted", rx.isSubstitutionPermitted());
        content.put("indication", rx.getIndication());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (ItemData d : items) {
            Map<String, Object> m = new TreeMap<>();
            m.put("medicationCode", d.medicationCode());
            m.put("codingSystem", d.codingSystem());
            m.put("displayName", d.displayName());
            m.put("dose", d.dose());
            m.put("route", d.route());
            m.put("frequency", d.frequency());
            m.put("durationDays", d.durationDays());
            m.put("quantity", d.quantity() != null ? d.quantity().toPlainString() : null);
            m.put("quantityUnit", d.quantityUnit());
            m.put("instructions", d.instructions());
            m.put("controlled", d.controlled());
            lines.add(m);
        }
        lines.sort(Comparator.comparing(a -> String.valueOf(a.get("medicationCode"))));
        content.put("items", lines);
        try {
            return objectMapper.writeValueAsString(content);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to canonicalise prescription content", e);
        }
    }

    private static void requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new OrosDomainException("REASON_REQUIRED", 400,
                    "A reason is required for this lifecycle action.");
        }
    }

    private Object versionPayload(PrescriptionEntity rx, PrescriptionVersionEntity version) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("prescriptionId", rx.getPrescriptionId());
        node.put("prescriptionVersionId", version.getPrescriptionVersionId());
        node.put("version", version.getVersion());
        node.put("supersedesVersionId", version.getSupersedesVersionId());
        node.put("contentHash", version.getContentHash());
        node.put("status", rx.getStatus().name());
        node.put("controlled", rx.isControlled());
        return node;
    }

    private Object activationPayload(PrescriptionEntity rx, PrescriptionVersionEntity version,
                                     PrescriptionTokenEntity token) {
        ObjectNode node = (ObjectNode) versionPayload(rx, version);
        node.put("tokenId", token.getTokenId());
        node.put("tokenExpiresAt", token.getExpiresAt().toString());
        node.put("repeatsRemaining", rx.getRepeatsRemaining());
        return node;
    }

    private Object claimPayload(PrescriptionEntity rx, PrescriptionClaimEntity claim, boolean exhausted) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("prescriptionId", rx.getPrescriptionId());
        node.put("prescriptionVersionId", claim.getPrescriptionVersionId());
        node.put("claimId", claim.getClaimId() != null ? claim.getClaimId().toString() : null);
        node.put("claimStatus", claim.getStatus());
        node.put("repeatsRemaining", rx.getRepeatsRemaining());
        node.put("exhausted", exhausted);
        node.put("controlled", rx.isControlled());
        return node;
    }

    private void publishEvent(String aggregateId, String eventType, Object payload, UUID tenantId) {
        try {
            EventOutboxEntity event = new EventOutboxEntity();
            event.setAggregateType("PRESCRIPTION");
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setPayload(objectMapper.writeValueAsString(payload));
            event.setTenantId(tenantId);
            outboxRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to write prescription outbox event: {}", eventType, e);
        }
    }
}
