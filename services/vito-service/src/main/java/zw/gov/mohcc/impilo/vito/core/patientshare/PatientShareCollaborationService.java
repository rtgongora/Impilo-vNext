package zw.gov.mohcc.impilo.vito.core.patientshare;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.crypto.Argon2IdService;
import zw.gov.mohcc.impilo.shared.crypto.HmacService;
import zw.gov.mohcc.impilo.vito.core.IdentityService;
import zw.gov.mohcc.impilo.vito.core.patientshare.policy.PatientSharePolicyClient;
import zw.gov.mohcc.impilo.vito.core.patientshare.policy.PatientSharePolicyEvaluateRequest;
import zw.gov.mohcc.impilo.vito.core.patientshare.policy.PatientSharePolicyEvaluationResponse;
import zw.gov.mohcc.impilo.vito.integration.PctCollaborationClient;
import zw.gov.mohcc.impilo.vito.integration.VarapiCollaborationClient;
import zw.gov.mohcc.impilo.vito.persistence.entity.*;
import zw.gov.mohcc.impilo.vito.persistence.repository.*;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PatientShareCollaborationService {

    private static final Logger log = LoggerFactory.getLogger(PatientShareCollaborationService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final IdentityService identityService;
    private final PatientSharePolicyClient policyClient;
    private final VarapiCollaborationClient varapiCollaborationClient;
    private final HmacService hmacService;
    private final Argon2IdService argon2IdService;
    private final PatientShareRequestRepository requestRepository;
    private final PatientShareArtifactRepository artifactRepository;
    private final PatientShareAccessGrantRepository grantRepository;
    private final PatientShareSessionRepository sessionRepository;
    private final PatientShareContributionRepository contributionRepository;
    private final PatientShareRevocationRepository revocationRepository;
    private final EventOutboxRepository outboxRepository;
    private final PctCollaborationClient pctCollaborationClient;
    private final ObjectMapper objectMapper;
    private final String stepUpExpectedCode;

    public PatientShareCollaborationService(
            IdentityService identityService,
            PatientSharePolicyClient policyClient,
            VarapiCollaborationClient varapiCollaborationClient,
            HmacService hmacService,
            Argon2IdService argon2IdService,
            PatientShareRequestRepository requestRepository,
            PatientShareArtifactRepository artifactRepository,
            PatientShareAccessGrantRepository grantRepository,
            PatientShareSessionRepository sessionRepository,
            PatientShareContributionRepository contributionRepository,
            PatientShareRevocationRepository revocationRepository,
            EventOutboxRepository outboxRepository,
            PctCollaborationClient pctCollaborationClient,
            ObjectMapper objectMapper,
            @Value("${vito.patient-share.step-up-test-code:000000}") String stepUpExpectedCode) {
        this.identityService = identityService;
        this.policyClient = policyClient;
        this.varapiCollaborationClient = varapiCollaborationClient;
        this.hmacService = hmacService;
        this.argon2IdService = argon2IdService;
        this.requestRepository = requestRepository;
        this.artifactRepository = artifactRepository;
        this.grantRepository = grantRepository;
        this.sessionRepository = sessionRepository;
        this.contributionRepository = contributionRepository;
        this.revocationRepository = revocationRepository;
        this.outboxRepository = outboxRepository;
        this.pctCollaborationClient = pctCollaborationClient;
        this.objectMapper = objectMapper;
        this.stepUpExpectedCode = stepUpExpectedCode == null ? "000000" : stepUpExpectedCode.trim();
    }

    @Transactional
    public CreateShareResult createShare(UUID healthId, CreateShareCommand cmd) {
        TrustContext ctx = TrustContextHolder.require();
        identityService.findByHealthId(ctx.tenantId(), healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        String actorType = resolveShareCreateActorType(ctx);
        PatientSharePolicyEvaluationResponse pol = policyClient.evaluate(
                ctx,
                new PatientSharePolicyEvaluateRequest(
                        "SHARE_CREATE", cmd.scopeType(), actorType, null, false, false));
        if (pol == null || "PROHIBITED".equalsIgnoreCase(pol.policyOutcome())) {
            throw new PatientSharePolicyDeniedException(
                    "Patient share issuance denied by Tshepo policy");
        }

        OffsetDateTime expires = OffsetDateTime.now().plusHours(Math.max(1, cmd.expiryHours()));
        PatientShareRequestEntity req = new PatientShareRequestEntity();
        req.setTenantId(ctx.tenantId());
        req.setHealthId(healthId);
        req.setInitiatedBy(ctx.actorId());
        req.setShareType(cmd.shareType() == null ? "COLLABORATION" : cmd.shareType());
        req.setTargetProviderHint(cmd.targetProviderHint());
        req.setPurposeOfUse(cmd.purposeOfUse());
        req.setScopeType(cmd.scopeType());
        req.setScopeSummary(cmd.scopeSummary());
        req.setExpiresAt(expires);
        req.setRevocableFlag(cmd.revocableFlag());
        req.setNotes(cmd.notes());
        req.setCorrelationId(ctx.correlationId().toString());
        req = requestRepository.save(req);

        PatientShareAccessGrantEntity grant = new PatientShareAccessGrantEntity();
        grant.setShareRequestId(req.getId());
        grant.setTenantId(ctx.tenantId());
        grant.setHealthId(healthId);
        grant.setScopeType(cmd.scopeType());
        grant.setAllowedActions(defaultAllowedActions(cmd.scopeType(), cmd.allowedActions()));
        grant.setVisibilityProfile(cmd.visibilityProfile());
        grant.setUpdatePermissions(cmd.updatePermissions());
        grant.setIssuedByPolicyRef(pol.matchedRuleId() != null ? "TSHEPO_PSR_" + pol.matchedRuleId() : null);
        grant.setStatus("PENDING");
        grant.setExpiresAt(expires);
        grant = grantRepository.save(grant);

        String shareToken = UUID.randomUUID().toString();
        String otp = generateOtp();
        PatientShareArtifactEntity art = new PatientShareArtifactEntity();
        art.setShareRequestId(req.getId());
        art.setArtifactType(cmd.artifactType() == null ? "LINK" : cmd.artifactType());
        art.setTokenHash(hmacService.computeLookupHash(shareToken));
        art.setOtpHash(argon2IdService.hash(otp));
        art.setExpiresAt(expires);
        art.setOneTimeUseFlag(cmd.oneTimeUse());
        artifactRepository.save(art);

        publish("PATIENT_SHARE", String.valueOf(req.getId()), "patient_share.requested",
                "{\"healthId\":\"" + healthId + "\",\"grantId\":" + grant.getId() + "}");
        publish("PATIENT_SHARE", String.valueOf(req.getId()), "patient_share.access_grant_created",
                "{\"grantId\":" + grant.getId() + "}");
        publish("PATIENT_SHARE", String.valueOf(art.getId()), "patient_share.artifact_issued",
                "{\"shareRequestId\":" + req.getId() + "}");

        return new CreateShareResult(req.getId(), grant.getId(), shareToken, otp, expires, grant.getAllowedActions());
    }

    @Transactional(readOnly = true)
    public List<ShareSummary> listShares(UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        return requestRepository.findByTenantIdAndHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId).stream()
                .map(r -> {
                    PatientShareAccessGrantEntity g = grantRepository
                            .findFirstByShareRequestIdOrderByIdAsc(r.getId())
                            .orElse(null);
                    List<PatientShareArtifactEntity> arts = artifactRepository.findByShareRequestId(r.getId());
                    boolean anyActiveArtifact =
                            arts.stream().anyMatch(a -> a.isActiveFlag() && a.getRevokedAt() == null);
                    return new ShareSummary(
                            r.getId(),
                            r.getScopeType(),
                            r.getShareStatus(),
                            r.getExpiresAt(),
                            g != null ? g.getStatus() : null,
                            g != null ? g.getTrustLevel() : null,
                            anyActiveArtifact);
                })
                .toList();
    }

    @Transactional
    public void revokeShare(UUID healthId, long shareRequestId, String reason) {
        TrustContext ctx = TrustContextHolder.require();
        PatientShareRequestEntity req = requestRepository
                .findByIdAndTenantIdAndHealthId(shareRequestId, ctx.tenantId(), healthId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        if (!req.isRevocableFlag()) {
            throw new IllegalStateException("Share is not revocable under policy");
        }
        req.setShareStatus("REVOKED");
        requestRepository.save(req);

        PatientShareAccessGrantEntity grant = grantRepository
                .findFirstByShareRequestIdOrderByIdAsc(req.getId())
                .orElse(null);
        if (grant != null) {
            grant.setStatus("REVOKED");
            grant.setRevokedAt(OffsetDateTime.now());
            grantRepository.save(grant);
        }
        for (PatientShareArtifactEntity a : artifactRepository.findByShareRequestId(req.getId())) {
            a.setActiveFlag(false);
            a.setRevokedAt(OffsetDateTime.now());
            artifactRepository.save(a);
        }

        PatientShareRevocationEntity rev = new PatientShareRevocationEntity();
        rev.setShareRequestId(req.getId());
        rev.setAccessGrantId(grant != null ? grant.getId() : null);
        rev.setRevokedBy(ctx.actorId());
        rev.setReason(reason);
        revocationRepository.save(rev);

        publish("PATIENT_SHARE", String.valueOf(shareRequestId), "patient_share.revoked",
                "{\"revokedBy\":\"" + jsonEscape(ctx.actorId()) + "\"}");
    }

    @Transactional(readOnly = true)
    public ValidateArtifactResult validateArtifact(String shareToken) {
        PatientShareArtifactEntity art = loadActiveArtifact(shareToken);
        PatientShareRequestEntity req = requestRepository.findById(art.getShareRequestId()).orElseThrow();
        return new ValidateArtifactResult(
                true,
                req.getExpiresAt(),
                art.getExpiresAt(),
                art.isOneTimeUseFlag(),
                req.getTenantId(),
                req.getCorrelationId());
    }

    @Transactional
    public OtpVerifyResult verifyOtp(String shareToken, String otp) {
        PatientShareArtifactEntity art = loadActiveArtifact(shareToken);
        PatientShareRequestEntity req = requestRepository.findById(art.getShareRequestId()).orElseThrow();
        if (!"ACTIVE".equalsIgnoreCase(req.getShareStatus())) {
            throw new IllegalStateException("Share is not active");
        }
        if (OffsetDateTime.now().isAfter(req.getExpiresAt())) {
            throw new IllegalStateException("Share has expired");
        }

        PatientShareAccessGrantEntity grant = grantRepository
                .findFirstByShareRequestIdOrderByIdAsc(req.getId())
                .orElseThrow();
        if ("REVOKED".equalsIgnoreCase(grant.getStatus())) {
            throw new IllegalStateException("Access grant revoked");
        }

        art.setOtpAttempts(art.getOtpAttempts() + 1);
        if (!argon2IdService.verify(otp, art.getOtpHash())) {
            artifactRepository.save(art);
            if (art.getOtpAttempts() >= art.getMaxOtpAttempts()) {
                art.setActiveFlag(false);
                art.setRevokedAt(OffsetDateTime.now());
                artifactRepository.save(art);
                throw new IllegalStateException("Maximum OTP attempts exceeded");
            }
            throw new IllegalArgumentException("Invalid OTP");
        }

        String sessionToken = UUID.randomUUID().toString();
        PatientShareSessionEntity session = new PatientShareSessionEntity();
        session.setAccessGrantId(grant.getId());
        session.setSessionTokenHash(hmacService.computeLookupHash(sessionToken));
        boolean needStepUp = scopeRequiresStepUp(req.getScopeType());
        session.setSessionStatus(needStepUp ? "AWAITING_STEP_UP" : "AWAITING_IDENTITY");
        session.setStepUpVerified(!needStepUp);
        sessionRepository.save(session);

        publish("PATIENT_SHARE", String.valueOf(grant.getId()), "patient_share.accessed",
                "{\"stage\":\"OTP_VERIFIED\",\"grantId\":" + grant.getId() + "}");

        return new OtpVerifyResult(sessionToken, grant.getId(), req.getScopeType(), session.getSessionStatus());
    }

    @Transactional
    public void verifyStepUp(String sessionToken, String stepUpCode) {
        PatientShareSessionEntity session = sessionRepository
                .findBySessionTokenHash(hmacService.computeLookupHash(sessionToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid session"));
        if (!"AWAITING_STEP_UP".equalsIgnoreCase(session.getSessionStatus())) {
            throw new IllegalStateException("Session is not awaiting step-up");
        }
        String expected = stepUpExpectedCode.isBlank() ? "000000" : stepUpExpectedCode;
        if (stepUpCode == null || !expected.equals(stepUpCode.trim())) {
            throw new IllegalArgumentException("Invalid step-up code");
        }
        session.setStepUpVerified(true);
        session.setSessionStatus("AWAITING_IDENTITY");
        session.setLastActiveAt(OffsetDateTime.now());
        sessionRepository.save(session);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listCollaborationCouncils(UUID tenantId) {
        TrustContext ctx = synthetic(tenantId, "external-patient-share", "EXTERNAL_PROVIDER", UUID.randomUUID());
        return varapiCollaborationClient.listCouncils(ctx, tenantId);
    }

    @Transactional
    public WorkspaceBootstrapResult completeIdentity(String sessionToken, CompleteIdentityCommand cmd) {
        PatientShareSessionEntity session = sessionRepository
                .findBySessionTokenHash(hmacService.computeLookupHash(sessionToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid session"));
        if (!"AWAITING_IDENTITY".equalsIgnoreCase(session.getSessionStatus())) {
            throw new IllegalStateException("Session is not awaiting identity");
        }

        PatientShareAccessGrantEntity grant = grantRepository.findById(session.getAccessGrantId()).orElseThrow();
        if (!"PENDING".equalsIgnoreCase(grant.getStatus())) {
            throw new IllegalStateException("Grant is not pending activation");
        }
        if (OffsetDateTime.now().isAfter(grant.getExpiresAt())) {
            grant.setStatus("EXPIRED");
            grantRepository.save(grant);
            throw new IllegalStateException("Grant has expired");
        }

        TrustContext synth = synthForGrant(grant);

        Map<String, Object> profile = varapiCollaborationClient.createExternalProviderProfile(
                synth,
                new VarapiCollaborationClient.CreateExternalProviderProfilePayload(
                        grant.getTenantId(),
                        String.valueOf(grant.getId()),
                        cmd.councilId(),
                        cmd.councilRegistrationNumber(),
                        cmd.givenName(),
                        cmd.familyName(),
                        cmd.professionOrCadre(),
                        cmd.email(),
                        cmd.phone(),
                        cmd.organisationHint()));

        long profileId = ((Number) profile.get("id")).longValue();
        String trust = String.valueOf(profile.get("trustLevel"));
        boolean councilMatched = Boolean.TRUE.equals(profile.get("councilRegistrationMatched"));
        String tempFromVarapi = Objects.toString(profile.get("temporaryProviderPublicId"), null);

        PatientSharePolicyEvaluationResponse activate = policyClient.evaluate(
                synth,
                new PatientSharePolicyEvaluateRequest(
                        "EXTERNAL_ACTIVATE",
                        grant.getScopeType(),
                        "EXTERNAL_PROVIDER",
                        trust,
                        true,
                        councilMatched));
        if (activate == null || "PROHIBITED".equalsIgnoreCase(activate.policyOutcome()) || !activate.permitRead()) {
            publish("PATIENT_SHARE", String.valueOf(grant.getId()), "external_provider.access_denied",
                    "{\"reason\":\"TSHEPO_EXTERNAL_ACTIVATE\"}");
            throw new PatientSharePolicyDeniedException("External collaboration activation denied by Tshepo policy");
        }

        PatientSharePolicyEvaluationResponse tempPol = policyClient.evaluate(
                synth,
                new PatientSharePolicyEvaluateRequest(
                        "TEMP_PROVIDER_ISSUE",
                        grant.getScopeType(),
                        "EXTERNAL_PROVIDER",
                        trust,
                        true,
                        councilMatched));

        String tempToStore = null;
        if (tempPol != null
                && !"PROHIBITED".equalsIgnoreCase(tempPol.policyOutcome())
                && tempPol.permitTempProviderId()) {
            tempToStore = tempFromVarapi;
            publish("EXTERNAL_PROVIDER", tempToStore != null ? tempToStore : String.valueOf(profileId),
                    "external_provider.temporary_provider_id_issued",
                    "{\"grantId\":" + grant.getId() + "}");
        }

        grant.setStatus("ACTIVE");
        grant.setTrustLevel(trust);
        grant.setExternalProfileId(profileId);
        grant.setTemporaryProviderPublicId(tempToStore);
        grant.setIssuedByPolicyRef(
                activate.matchedRuleId() != null ? "TSHEPO_PSR_" + activate.matchedRuleId() : grant.getIssuedByPolicyRef());
        grant.setStartAt(OffsetDateTime.now());
        grant.setSessionActivatedCount(grant.getSessionActivatedCount() + 1);
        grantRepository.save(grant);

        session.setExternalProfileId(profileId);
        session.setSessionStatus("ACTIVE");
        session.setLastActiveAt(OffsetDateTime.now());
        sessionRepository.save(session);

        publish("PATIENT_SHARE", String.valueOf(grant.getId()), "external_provider.access_allowed",
                "{\"profileId\":" + profileId + ",\"trustLevel\":\"" + jsonEscape(trust) + "\"}");
        publish("EXTERNAL_PROVIDER", String.valueOf(profileId), "external_provider.profile_created",
                "{\"grantId\":" + grant.getId() + "}");

        return new WorkspaceBootstrapResult(
                grant.getId(),
                grant.getHealthId(),
                grant.getScopeType(),
                grant.getAllowedActions(),
                trust,
                tempToStore,
                activate.permitWrite());
    }

    @Transactional(readOnly = true)
    public WorkspaceContextResult getWorkspace(String sessionToken) {
        PatientShareSessionEntity session = loadActiveSession(sessionToken);
        PatientShareAccessGrantEntity grant = grantRepository.findById(session.getAccessGrantId()).orElseThrow();
        if (!"ACTIVE".equalsIgnoreCase(grant.getStatus())) {
            throw new IllegalStateException("Grant is not active");
        }
        if (OffsetDateTime.now().isAfter(grant.getExpiresAt())) {
            throw new IllegalStateException("Grant has expired");
        }
        return new WorkspaceContextResult(
                grant.getId(),
                grant.getHealthId(),
                grant.getScopeType(),
                grant.getAllowedActions(),
                grant.getTrustLevel(),
                grant.getTemporaryProviderPublicId(),
                session.getSessionStatus());
    }

    @Transactional
    public ContributionRecordedResult addContribution(String sessionToken, ContributionCommand cmd) {
        PatientShareSessionEntity session = loadActiveSession(sessionToken);
        PatientShareAccessGrantEntity grant = grantRepository.findById(session.getAccessGrantId()).orElseThrow();
        if (!"ACTIVE".equalsIgnoreCase(grant.getStatus())) {
            throw new IllegalStateException("Grant is not active");
        }
        if (!actionAllowed(grant.getAllowedActions(), cmd.actionType())) {
            throw new IllegalStateException("Action not permitted under this share grant");
        }

        TrustContext synth = synthForGrant(grant);
        boolean councilMatched = councilBackedTrust(grant.getTrustLevel());

        PatientSharePolicyEvaluationResponse pol = policyClient.evaluate(
                synth,
                new PatientSharePolicyEvaluateRequest(
                        "CONTRIBUTION_WRITE",
                        grant.getScopeType(),
                        "EXTERNAL_PROVIDER",
                        grant.getTrustLevel(),
                        true,
                        councilMatched));
        if (pol == null || "PROHIBITED".equalsIgnoreCase(pol.policyOutcome()) || !pol.permitWrite()) {
            throw new PatientSharePolicyDeniedException("Contribution denied by Tshepo policy");
        }

        String payloadJson = cmd.payloadJson() == null ? "{}" : cmd.payloadJson();
        PatientShareContributionEntity row = new PatientShareContributionEntity();
        row.setAccessGrantId(grant.getId());
        row.setSessionId(session.getId());
        row.setExternalProfileId(session.getExternalProfileId());
        row.setTemporaryProviderPublicId(grant.getTemporaryProviderPublicId());
        row.setTargetRecordType(cmd.targetRecordType());
        row.setTargetRecordId(cmd.targetRecordId());
        row.setActionType(cmd.actionType());
        row.setSummary(cmd.summary());
        row.setPayload(payloadJson);
        row.setTrustLevelAtContribution(grant.getTrustLevel());
        contributionRepository.save(row);

        session.setLastActiveAt(OffsetDateTime.now());
        sessionRepository.save(session);

        publish("PATIENT_SHARE", String.valueOf(grant.getId()), "shared_record.contribution_created",
                "{\"contributionId\":" + row.getId() + ",\"action\":\"" + jsonEscape(cmd.actionType()) + "\"}");

        if ("ADD_NOTE".equalsIgnoreCase(cmd.actionType())) {
            maybeMirrorContributionToPct(grant, row, cmd, synth);
        }

        return new ContributionRecordedResult(row.getId());
    }

    @Transactional(readOnly = true)
    public List<ContributionView> listContributions(UUID healthId, long shareRequestId) {
        TrustContext ctx = TrustContextHolder.require();
        PatientShareRequestEntity req = requestRepository
                .findByIdAndTenantIdAndHealthId(shareRequestId, ctx.tenantId(), healthId)
                .orElseThrow(() -> new IllegalArgumentException("Share not found"));
        PatientShareAccessGrantEntity grant = grantRepository
                .findFirstByShareRequestIdOrderByIdAsc(req.getId())
                .orElseThrow();
        return contributionRepository.findByAccessGrantIdOrderByCreatedAtDesc(grant.getId()).stream()
                .map(c -> new ContributionView(
                        c.getId(),
                        c.getActionType(),
                        c.getSummary(),
                        c.getTrustLevelAtContribution(),
                        c.getCreatedAt()))
                .toList();
    }

    private PatientShareArtifactEntity loadActiveArtifact(String shareToken) {
        PatientShareArtifactEntity art = artifactRepository
                .findByTokenHash(hmacService.computeLookupHash(shareToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid share token"));
        if (!art.isActiveFlag() || art.getRevokedAt() != null) {
            throw new IllegalStateException("Share artifact is not active");
        }
        if (OffsetDateTime.now().isAfter(art.getExpiresAt())) {
            throw new IllegalStateException("Share artifact has expired");
        }
        return art;
    }

    private PatientShareSessionEntity loadActiveSession(String sessionToken) {
        PatientShareSessionEntity s = sessionRepository
                .findBySessionTokenHash(hmacService.computeLookupHash(sessionToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid session"));
        if ("CLOSED".equalsIgnoreCase(s.getSessionStatus())) {
            throw new IllegalStateException("Session closed");
        }
        return s;
    }

    private TrustContext synthForGrant(PatientShareAccessGrantEntity grant) {
        return requestRepository
                .findById(grant.getShareRequestId())
                .map(req -> synthetic(
                        grant.getTenantId(), "external-patient-share", "EXTERNAL_PROVIDER", correlationUuidOrRandom(req)))
                .orElseGet(() -> synthetic(
                        grant.getTenantId(), "external-patient-share", "EXTERNAL_PROVIDER", UUID.randomUUID()));
    }

    private static UUID correlationUuidOrRandom(PatientShareRequestEntity req) {
        if (req.getCorrelationId() != null && !req.getCorrelationId().isBlank()) {
            try {
                return UUID.fromString(req.getCorrelationId().trim());
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return UUID.randomUUID();
    }

    private static TrustContext synthetic(UUID tenantId, String actorId, String actorType, UUID correlationId) {
        return new TrustContext(
                tenantId,
                actorId,
                actorType,
                "CARE_COORDINATION",
                null,
                correlationId != null ? correlationId : UUID.randomUUID(),
                null,
                null,
                null,
                AccessMode.INTERNAL);
    }

    private void maybeMirrorContributionToPct(
            PatientShareAccessGrantEntity grant,
            PatientShareContributionEntity row,
            ContributionCommand cmd,
            TrustContext synth) {
        String noteText = cmd.summary() != null ? cmd.summary() : "";
        if (cmd.payloadJson() != null && !cmd.payloadJson().isBlank()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = objectMapper.readValue(cmd.payloadJson(), Map.class);
                Object n = m.get("note");
                if (n == null) {
                    n = m.get("body");
                }
                if (n != null) {
                    noteText = String.valueOf(n);
                }
            } catch (Exception ignored) {
                // keep summary / empty
            }
        }
        String shareCorr = requestRepository
                .findById(grant.getShareRequestId())
                .map(PatientShareRequestEntity::getCorrelationId)
                .orElse(null);
        try {
            pctCollaborationClient.createClinicalNote(
                    synth,
                    grant.getId(),
                    row.getId(),
                    grant.getTemporaryProviderPublicId(),
                    shareCorr,
                    grant.getTrustLevel(),
                    grant.getHealthId(),
                    noteText,
                    "External collaborator");
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 401) {
                log.warn(
                        "PCT clinical note mirror skipped (HTTP 401). Configure vito.patient-share.pct-bearer-token for service auth. contributionId={}",
                        row.getId());
                return;
            }
            throw new IllegalStateException("PCT clinical note mirror failed", ex);
        }
    }

    private static boolean scopeRequiresStepUp(String scopeType) {
        if (scopeType == null) {
            return false;
        }
        return switch (scopeType.trim().toUpperCase(Locale.ROOT)) {
            case "DOCUMENT_SET", "ENCOUNTER_CONTEXT" -> true;
            default -> false;
        };
    }

    private static String resolveShareCreateActorType(TrustContext ctx) {
        String at = ctx.actorType();
        if (at == null || at.isBlank()) {
            return "SYSTEM";
        }
        String u = at.toUpperCase(Locale.ROOT);
        if (u.contains("CITIZEN")) {
            return "CITIZEN";
        }
        return "SYSTEM";
    }

    private static String defaultAllowedActions(String scopeType, String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested.trim();
        }
        if (scopeType == null) {
            return "ADD_NOTE";
        }
        return switch (scopeType.toUpperCase(Locale.ROOT)) {
            case "ENCOUNTER_CONTEXT" -> "UPDATE_ENCOUNTER_SECTION,ADD_NOTE";
            case "REFERRAL_CONTEXT" -> "ADD_REFERRAL_SUMMARY,ADD_NOTE";
            case "DOCUMENT_SET" -> "UPLOAD_DOCUMENT,ADD_NOTE";
            case "LIMITED_CLINICAL_SUMMARY", "SPECIFIC_FORM_OR_WORKFLOW" -> "ADD_NOTE,UPDATE_LIMITED_FORM";
            default -> "ADD_NOTE";
        };
    }

    private static boolean councilBackedTrust(String trustLevel) {
        if (trustLevel == null || trustLevel.isBlank()) {
            return false;
        }
        String t = trustLevel.toUpperCase(Locale.ROOT);
        return t.contains("FOUND") || t.contains("IDENTITY") || t.contains("LINKED");
    }

    private static boolean actionAllowed(String allowedCsv, String action) {
        if (allowedCsv == null || allowedCsv.isBlank() || action == null) {
            return false;
        }
        Set<String> allowed = Arrays.stream(allowedCsv.split(","))
                .map(String::trim)
                .map(s -> s.toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return allowed.contains(action.trim().toUpperCase(Locale.ROOT));
    }

    private String generateOtp() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private void publish(String aggregateType, String aggregateId, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private static String jsonEscape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record CreateShareCommand(
            String scopeType,
            String purposeOfUse,
            String shareType,
            int expiryHours,
            String targetProviderHint,
            String allowedActions,
            String visibilityProfile,
            String updatePermissions,
            String notes,
            boolean revocableFlag,
            boolean oneTimeUse,
            String scopeSummary,
            String artifactType) {}

    public record CreateShareResult(
            long shareRequestId, long grantId, String shareToken, String otp,
            OffsetDateTime expiresAt, String allowedActions) {}

    public record ShareSummary(
            long shareRequestId,
            String scopeType,
            String shareStatus,
            OffsetDateTime requestExpiresAt,
            String grantStatus,
            String trustLevel,
            boolean hasActiveArtifact) {}

    public record ValidateArtifactResult(
            boolean valid,
            OffsetDateTime requestExpiresAt,
            OffsetDateTime artifactExpiresAt,
            boolean oneTimeUse,
            UUID tenantId,
            String shareWorkflowCorrelationId) {}

    public record OtpVerifyResult(String sessionToken, long grantId, String scopeType, String sessionStatus) {}

    public record CompleteIdentityCommand(
            long councilId,
            String councilRegistrationNumber,
            String givenName,
            String familyName,
            String professionOrCadre,
            String email,
            String phone,
            String organisationHint) {}

    public record WorkspaceBootstrapResult(
            long grantId,
            UUID healthId,
            String scopeType,
            String allowedActions,
            String trustLevel,
            String temporaryProviderPublicId,
            boolean writeCapable) {}

    public record WorkspaceContextResult(
            long grantId,
            UUID healthId,
            String scopeType,
            String allowedActions,
            String trustLevel,
            String temporaryProviderPublicId,
            String sessionStatus) {}

    public record ContributionCommand(
            String targetRecordType,
            String targetRecordId,
            String actionType,
            String summary,
            String payloadJson) {}

    public record ContributionRecordedResult(long contributionId) {}

    public record ContributionView(
            long id, String actionType, String summary, String trustLevel, OffsetDateTime createdAt) {}
}
