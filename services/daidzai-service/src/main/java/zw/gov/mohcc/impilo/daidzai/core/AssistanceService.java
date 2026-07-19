package zw.gov.mohcc.impilo.daidzai.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.daidzai.events.DaidzaiEventEmitter;
import zw.gov.mohcc.impilo.daidzai.integration.CredentialAttestationClient;
import zw.gov.mohcc.impilo.daidzai.integration.MusheContributionClient;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceContributionSeenEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceEventEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AssistanceRequestEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.AssistanceContributionSeenRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.AssistanceEventRepository;
import zw.gov.mohcc.impilo.daidzai.persistence.repository.AssistanceRequestRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Assistance-case spine (crowdfunding). PO doctrine: crowdfunding is a form of ASKING FOR
 * HELP, so the case anchors here — Daidzai owns the ask, the verification lifecycle and
 * the timeline, and references money (mushe), evidence (credential-verification) and
 * consent (tshepo) by id only.
 *
 * <p>Lifecycle: DRAFT → PENDING_VERIFICATION → ACTIVE | REJECTED; ACTIVE → FULFILLED |
 * CANCELLED | REVOKED. Only verified-ACTIVE cases are publicly listed; the verified state
 * is reachable ONLY through a provider attestation that issues a signed anonymised
 * artifact — there is no administrative "mark verified" toggle.</p>
 */
@Service
public class AssistanceService {

    private static final Logger log = LoggerFactory.getLogger(AssistanceService.class);

    public static final String DRAFT = "DRAFT";
    public static final String PENDING_VERIFICATION = "PENDING_VERIFICATION";
    public static final String ACTIVE = "ACTIVE";
    public static final String REJECTED = "REJECTED";
    public static final String REVOKED = "REVOKED";
    public static final String FULFILLED = "FULFILLED";
    public static final String CANCELLED = "CANCELLED";

    private static final Set<String> CATEGORIES = Set.of(
            "MEDICAL_COSTS", "MEDICATION", "TRANSPORT_TO_CARE", "ASSISTIVE_DEVICE", "REHABILITATION");

    private final AssistanceRequestRepository requestRepo;
    private final AssistanceEventRepository eventRepo;
    private final AssistanceContributionSeenRepository seenRepo;
    private final MusheContributionClient mushe;
    private final CredentialAttestationClient attestations;
    private final DaidzaiEventEmitter emitter;

    public AssistanceService(AssistanceRequestRepository requestRepo,
                             AssistanceEventRepository eventRepo,
                             AssistanceContributionSeenRepository seenRepo,
                             MusheContributionClient mushe,
                             CredentialAttestationClient attestations,
                             DaidzaiEventEmitter emitter) {
        this.requestRepo = requestRepo;
        this.eventRepo = eventRepo;
        this.seenRepo = seenRepo;
        this.mushe = mushe;
        this.attestations = attestations;
        this.emitter = emitter;
    }

    // ---- Creation (DRAFT) — mints the mushe money backbone, fail-closed ----

    @Transactional
    public AssistanceRequestEntity create(UUID tenantId, String requesterType, String requesterActorId,
                                          String subjectIdentityMode, String subjectCpid,
                                          String category, String title, String story,
                                          BigDecimal targetAmount, String currency,
                                          OffsetDateTime endsAt, UUID beneficiaryWalletId) {
        if (requesterActorId == null || requesterActorId.isBlank()) {
            throw new IllegalArgumentException("requesterActorId is required — assistance cases are never anonymous toward the platform");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title is required");
        }
        if (targetAmount == null || targetAmount.signum() <= 0) {
            throw new IllegalArgumentException("targetAmount must be positive");
        }
        if (beneficiaryWalletId == null) {
            throw new IllegalArgumentException("beneficiaryWalletId is required — funds must have a destination");
        }
        String cat = category == null || category.isBlank() ? "MEDICAL_COSTS" : category;
        if (!CATEGORIES.contains(cat)) {
            throw new IllegalArgumentException("Unknown category: " + cat);
        }

        AssistanceRequestEntity a = new AssistanceRequestEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setAssistanceReference(newReference(tenantId));
        a.setRequesterType(requesterType == null || requesterType.isBlank() ? "CITIZEN" : requesterType);
        a.setRequesterActorId(requesterActorId);
        a.setSubjectIdentityMode("KNOWN".equals(subjectIdentityMode) ? "KNOWN" : "ANONYMOUS");
        a.setSubjectCpid(subjectCpid);
        a.setCategory(cat);
        a.setTitle(title.trim());
        a.setStory(story);
        a.setTargetAmount(targetAmount);
        a.setCurrency(currency == null || currency.isBlank() ? "USD" : currency);
        a.setEndsAt(endsAt);
        a.setVerificationStatus(DRAFT);

        // Money backbone (fail-closed): mushe mints the campaign contribution request +
        // escrow wallet; we only hold the ids.
        MusheContributionClient.CampaignMoneyLink link = mushe.createCampaignRequest(
                tenantId, a.getId(), beneficiaryWalletId, a.getTitle(),
                targetAmount, a.getCurrency(), requesterActorId);
        a.setContributionRequestId(link.contributionRequestId());
        a.setShareToken(link.shareToken());

        requestRepo.save(a);
        timeline(a, "CREATED", null, DRAFT, requesterActorId, requesterType, "Case created");
        emit(a, "daidzai.assistance.created");
        return a;
    }

    // ---- Verification lifecycle ----

    @Transactional
    public AssistanceRequestEntity requestVerification(UUID tenantId, UUID id, String actorId,
                                                       String consentRef) {
        AssistanceRequestEntity a = requireOwn(tenantId, id, actorId);
        requireStatus(a, DRAFT);
        if (consentRef == null || consentRef.isBlank()) {
            throw new IllegalArgumentException(
                    "consentRef is required — verification shares a scope-bounded case summary and needs consent");
        }
        a.setConsentRef(consentRef.trim());
        transition(a, PENDING_VERIFICATION, "VERIFICATION_REQUESTED", actorId, "CITIZEN",
                "Verification requested; consent " + consentRef);
        emit(a, "daidzai.assistance.verification_requested");
        return a;
    }

    /**
     * Provider attestation — the ONLY path to verified-ACTIVE. Issues the signed anonymised
     * MEDICAL_CASE_ATTESTATION first (fail-closed) and stores the artifact refs.
     */
    @Transactional
    public AssistanceRequestEntity attest(UUID tenantId, UUID id, String attestingActorId,
                                          String actorType, String verifyingOrgRef) {
        if (!"PROVIDER".equals(actorType)) {
            throw new SecurityException("Only a PROVIDER actor can attest an assistance case");
        }
        if (verifyingOrgRef == null || verifyingOrgRef.isBlank()) {
            throw new IllegalArgumentException("verifyingOrgRef is required");
        }
        AssistanceRequestEntity a = require(tenantId, id);
        requireStatus(a, PENDING_VERIFICATION);

        CredentialAttestationClient.CaseAttestation artifact = attestations.issueCaseAttestation(
                tenantId, a.getAssistanceReference(), a.getCategory(), verifyingOrgRef, attestingActorId);
        a.setAttestationCredentialId(artifact.credentialId());
        a.setAttestationPublicToken(artifact.verificationUrl());
        a.setVerifyingOrgRef(verifyingOrgRef);
        a.setVerifiedAt(OffsetDateTime.now());
        transition(a, ACTIVE, "ATTESTED", attestingActorId, actorType,
                "Case attested by " + verifyingOrgRef);
        emit(a, "daidzai.assistance.verified");
        return a;
    }

    @Transactional
    public AssistanceRequestEntity reject(UUID tenantId, UUID id, String actorId, String actorType,
                                          String reason) {
        AssistanceRequestEntity a = require(tenantId, id);
        requireStatus(a, PENDING_VERIFICATION);
        transition(a, REJECTED, "REJECTED", actorId, actorType,
                reason == null ? "Verification rejected" : reason);
        emit(a, "daidzai.assistance.rejected");
        return a;
    }

    /** Revoke a verified case (governance) — refunds donors via mushe. */
    @Transactional
    public AssistanceRequestEntity revoke(UUID tenantId, UUID id, String actorId, String actorType,
                                          String reason) {
        AssistanceRequestEntity a = require(tenantId, id);
        requireStatus(a, ACTIVE);
        mushe.cancelAndRefund(tenantId, a.getContributionRequestId());
        transition(a, REVOKED, "REVOKED", actorId, actorType,
                reason == null ? "Attestation revoked; donations refunded" : reason);
        emit(a, "daidzai.assistance.revoked");
        return a;
    }

    /** Owner cancellation — refunds donors via mushe. */
    @Transactional
    public AssistanceRequestEntity cancel(UUID tenantId, UUID id, String actorId) {
        AssistanceRequestEntity a = requireOwn(tenantId, id, actorId);
        if (!DRAFT.equals(a.getVerificationStatus()) && !PENDING_VERIFICATION.equals(a.getVerificationStatus())
                && !ACTIVE.equals(a.getVerificationStatus())) {
            throw new IllegalStateException("Case cannot be cancelled from " + a.getVerificationStatus());
        }
        if (a.getContributionRequestId() != null) {
            mushe.cancelAndRefund(tenantId, a.getContributionRequestId());
        }
        transition(a, CANCELLED, "CANCELLED", actorId, "CITIZEN", "Cancelled by requester");
        emit(a, "daidzai.assistance.cancelled");
        return a;
    }

    /** Release escrow to the beneficiary — allowed only while verified-ACTIVE (or FULFILLED). */
    @Transactional
    public AssistanceRequestEntity release(UUID tenantId, UUID id, String actorId, BigDecimal amount) {
        AssistanceRequestEntity a = requireOwn(tenantId, id, actorId);
        if (!ACTIVE.equals(a.getVerificationStatus()) && !FULFILLED.equals(a.getVerificationStatus())) {
            throw new IllegalStateException("Funds release requires a verified case (status "
                    + a.getVerificationStatus() + ")");
        }
        mushe.release(tenantId, a.getContributionRequestId(), amount);
        timeline(a, "RELEASED", a.getVerificationStatus(), a.getVerificationStatus(), actorId, "CITIZEN",
                amount == null ? "Escrow released to beneficiary" : "Released " + amount);
        return a;
    }

    // ---- Money projection (event-driven; idempotent on contributionId) ----

    @Transactional
    public void applyContribution(UUID contributionRequestId, UUID contributionId, BigDecimal amount,
                                  boolean isAnonymous, String contributorLabel, String message) {
        if (contributionId == null || seenRepo.existsById(contributionId)) {
            return;
        }
        AssistanceRequestEntity a = requestRepo.findByContributionRequestId(contributionRequestId)
                .orElse(null);
        if (a == null) {
            // Not a campaign-backed contribution (plain bill chip-in) — nothing to project.
            return;
        }
        AssistanceContributionSeenEntity seen = new AssistanceContributionSeenEntity();
        seen.setContributionId(contributionId);
        seen.setAssistanceId(a.getId());
        seen.setAmount(amount);
        seen.setIsAnonymous(isAnonymous);
        seen.setContributorLabel(isAnonymous ? null : contributorLabel);
        seen.setMessage(message);
        seenRepo.save(seen);

        a.setRaisedAmount(a.getRaisedAmount().add(amount));
        a.setDonorCount(a.getDonorCount() + 1);
        timeline(a, "CONTRIBUTION", a.getVerificationStatus(), a.getVerificationStatus(),
                null, null, "Contribution of " + amount + " " + a.getCurrency());
        if (ACTIVE.equals(a.getVerificationStatus())
                && a.getRaisedAmount().compareTo(a.getTargetAmount()) >= 0) {
            transition(a, FULFILLED, "FULFILLED", null, null, "Target reached");
            emit(a, "daidzai.assistance.fulfilled");
        }
        requestRepo.save(a);
    }

    // ---- Reads ----

    @Transactional(readOnly = true)
    public List<AssistanceRequestEntity> listPublic(UUID tenantId) {
        return requestRepo.findByTenantIdAndVerificationStatusOrderByCreatedAtDesc(tenantId, ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<AssistanceRequestEntity> listMine(UUID tenantId, String actorId) {
        return requestRepo.findByTenantIdAndRequesterActorIdOrderByCreatedAtDesc(tenantId, actorId);
    }

    @Transactional(readOnly = true)
    public AssistanceRequestEntity require(UUID tenantId, UUID id) {
        return requestRepo.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Assistance case not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<AssistanceEventEntity> timelineOf(UUID tenantId, UUID id) {
        require(tenantId, id);
        return eventRepo.findByAssistanceIdOrderByOccurredAtAsc(id);
    }

    @Transactional(readOnly = true)
    public List<AssistanceContributionSeenEntity> contributionsOf(UUID tenantId, UUID id) {
        require(tenantId, id);
        return seenRepo.findByAssistanceIdOrderByRecordedAtDesc(id);
    }

    // ---- internals ----

    private AssistanceRequestEntity requireOwn(UUID tenantId, UUID id, String actorId) {
        AssistanceRequestEntity a = require(tenantId, id);
        if (actorId == null || !actorId.equals(a.getRequesterActorId())) {
            throw new SecurityException("Only the requester can perform this action");
        }
        return a;
    }

    private static void requireStatus(AssistanceRequestEntity a, String expected) {
        if (!expected.equals(a.getVerificationStatus())) {
            throw new IllegalStateException("Case is " + a.getVerificationStatus()
                    + " — expected " + expected);
        }
    }

    private void transition(AssistanceRequestEntity a, String to, String eventType,
                            String actorId, String actorType, String note) {
        String from = a.getVerificationStatus();
        a.setVerificationStatus(to);
        requestRepo.save(a);
        timeline(a, eventType, from, to, actorId, actorType, note);
    }

    private void timeline(AssistanceRequestEntity a, String eventType, String from, String to,
                          String actorId, String actorType, String note) {
        AssistanceEventEntity e = new AssistanceEventEntity();
        e.setTenantId(a.getTenantId());
        e.setAssistanceId(a.getId());
        e.setEventType(eventType);
        e.setFromStatus(from);
        e.setToStatus(to);
        e.setNote(note);
        e.setActorId(actorId);
        e.setActorType(actorType);
        eventRepo.save(e);
    }

    private void emit(AssistanceRequestEntity a, String eventType) {
        emitter.emit("ASSISTANCE_REQUEST", a.getId().toString(), eventType,
                "ASSISTANCE_CASE", a.getAssistanceReference(),
                Map.of(
                        "assistanceReference", a.getAssistanceReference(),
                        "verificationStatus", a.getVerificationStatus(),
                        "category", a.getCategory(),
                        "targetAmount", a.getTargetAmount().toPlainString(),
                        "raisedAmount", a.getRaisedAmount().toPlainString(),
                        "currency", a.getCurrency()),
                a.getTenantId());
    }

    private String newReference(UUID tenantId) {
        for (int attempt = 0; attempt < 25; attempt++) {
            String candidate = "HELP-" + OffsetDateTime.now().getYear() + "-"
                    + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            if (!requestRepo.existsByTenantIdAndAssistanceReference(tenantId, candidate)) {
                return candidate;
            }
        }
        return "HELP-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
    }
}
