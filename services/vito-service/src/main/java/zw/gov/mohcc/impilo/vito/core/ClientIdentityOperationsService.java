package zw.gov.mohcc.impilo.vito.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.api.dto.ClientRegistryDtos;
import zw.gov.mohcc.impilo.vito.api.dto.ClientIdentitySummary;
import zw.gov.mohcc.impilo.vito.core.id.ImpiloIdFormatV2;
import zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine;
import zw.gov.mohcc.impilo.vito.core.merge.MergeService;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAliasEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuditEventEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuthorizationLinkEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentityEvidenceEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientMergeCaseEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientRegistrationEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientStatusHistoryEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientStewardshipActionEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupCaseEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.MatchResultEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.MergeHistoryEntity;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientAliasRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientAuditEventRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientAuthorizationLinkRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentifierRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientIdentityEvidenceRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientMergeCaseRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRegistrationRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRelationshipRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientStatusHistoryRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientStewardshipActionRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.ClientVerificationReviewRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.DedupCaseRepository;
import zw.gov.mohcc.impilo.vito.events.ClientAddressDelegationPublisher;
import zw.gov.mohcc.impilo.vito.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.vito.persistence.repository.MatchResultRepository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ClientIdentityOperationsService {

    private static final Set<String> FRONTLINE_ACTOR_TYPES = Set.of(
            "REGISTRATION_CLERK", "PROVIDER_USER", "PROVIDER", "COMMUNITY_HEALTH_WORKER",
            "VIRTUAL_CARE_USER", "REGISTRY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER", "ADMIN");
    private static final Set<String> STEWARD_ACTOR_TYPES = Set.of(
            "IDENTITY_STEWARD", "REGISTRY_ADMIN", "SYSTEM_ADMIN", "DEVELOPER", "ADMIN");

    private final ClientRepository clientRepository;
    private final ClientRegistrationRepository registrationRepository;
    private final ClientIdentifierRepository identifierRepository;
    private final ClientAliasRepository aliasRepository;
    private final ClientIdentityEvidenceRepository evidenceRepository;
    private final ClientVerificationReviewRepository verificationReviewRepository;
    private final MatchResultRepository matchResultRepository;
    private final DedupCaseRepository dedupCaseRepository;
    private final ClientMergeCaseRepository mergeCaseRepository;
    private final ClientRelationshipRepository relationshipRepository;
    private final ClientAuthorizationLinkRepository authorizationLinkRepository;
    private final ClientStewardshipActionRepository stewardshipActionRepository;
    private final ClientStatusHistoryRepository statusHistoryRepository;
    private final ClientAuditEventRepository auditEventRepository;
    private final EventOutboxRepository outboxRepository;
    private final MatchingEngine matchingEngine;
    private final MergeService mergeService;
    private final ImpiloIdAliasService aliasService;
    // Format v2 (ISO 7064) is the only issuance format — Identity Contract §4.1 / D8.
    private final ImpiloIdFormatV2 impiloIdFormat;
    private final ObjectMapper objectMapper;
    private final ClientAddressDelegationPublisher addressDelegationPublisher;

    public ClientIdentityOperationsService(ClientRepository clientRepository,
                                           ClientRegistrationRepository registrationRepository,
                                           ClientIdentifierRepository identifierRepository,
                                           ClientAliasRepository aliasRepository,
                                           ClientIdentityEvidenceRepository evidenceRepository,
                                           ClientVerificationReviewRepository verificationReviewRepository,
                                           MatchResultRepository matchResultRepository,
                                           DedupCaseRepository dedupCaseRepository,
                                           ClientMergeCaseRepository mergeCaseRepository,
                                           ClientRelationshipRepository relationshipRepository,
                                           ClientAuthorizationLinkRepository authorizationLinkRepository,
                                           ClientStewardshipActionRepository stewardshipActionRepository,
                                           ClientStatusHistoryRepository statusHistoryRepository,
                                           ClientAuditEventRepository auditEventRepository,
                                           EventOutboxRepository outboxRepository,
                                           MatchingEngine matchingEngine,
                                           MergeService mergeService,
                                           ImpiloIdAliasService aliasService,
                                           ImpiloIdFormatV2 impiloIdFormat,
                                           ObjectMapper objectMapper,
                                           ClientAddressDelegationPublisher addressDelegationPublisher) {
        this.clientRepository = clientRepository;
        this.registrationRepository = registrationRepository;
        this.identifierRepository = identifierRepository;
        this.aliasRepository = aliasRepository;
        this.evidenceRepository = evidenceRepository;
        this.verificationReviewRepository = verificationReviewRepository;
        this.matchResultRepository = matchResultRepository;
        this.dedupCaseRepository = dedupCaseRepository;
        this.mergeCaseRepository = mergeCaseRepository;
        this.relationshipRepository = relationshipRepository;
        this.authorizationLinkRepository = authorizationLinkRepository;
        this.stewardshipActionRepository = stewardshipActionRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.auditEventRepository = auditEventRepository;
        this.outboxRepository = outboxRepository;
        this.matchingEngine = matchingEngine;
        this.mergeService = mergeService;
        this.aliasService = aliasService;
        this.impiloIdFormat = impiloIdFormat;
        this.objectMapper = objectMapper;
        this.addressDelegationPublisher = addressDelegationPublisher;
    }

    @Transactional(readOnly = true)
    public Page<ClientRegistryDtos.ClientRegistrySummaryResponse> searchClients(String query,
                                                                                IdentityStatus status,
                                                                                ClientVerificationState verificationState,
                                                                                int page,
                                                                                int size) {
        TrustContext ctx = TrustContextHolder.require();
        Page<ClientEntity> clients = clientRepository.searchClients(
                ctx.tenantId(),
                normalizeQuery(query),
                status,
                verificationState,
                PageRequest.of(page, size)
        );
        return clients.map(this::toSummary);
    }

    @Transactional(readOnly = true)
    public ClientRegistryDtos.DashboardSummaryResponse getDashboardSummary() {
        TrustContext ctx = TrustContextHolder.require();
        Map<String, Long> statusMap = new LinkedHashMap<>();
        for (IdentityStatus status : IdentityStatus.values()) {
            long count = clientRepository.countByTenantIdAndStatus(ctx.tenantId(), status);
            if (count > 0) {
                statusMap.put(status.name(), count);
            }
        }

        return new ClientRegistryDtos.DashboardSummaryResponse(
                clientRepository.countByTenantId(ctx.tenantId()),
                clientRepository.countByTenantIdAndStatus(ctx.tenantId(), IdentityStatus.PROVISIONAL),
                clientRepository.countByTenantIdAndStatus(ctx.tenantId(), IdentityStatus.PENDING_VERIFICATION),
                matchResultRepository.countByTenantIdAndDisposition(ctx.tenantId(), MatchDisposition.PENDING),
                stewardshipActionRepository.countByTenantIdAndStatus(ctx.tenantId(), ClientStewardshipStatus.OPEN),
                mergeCaseRepository.countByTenantIdAndStatus(ctx.tenantId(), "OPEN"),
                statusMap
        );
    }

    @Transactional
    public ClientRegistryDtos.ClientProfileResponse createRegistration(ClientRegistryDtos.CreateRegistrationRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        if (!isSelfRegistration(request.registrationType())) {
            assertFrontline(ctx);
        }

        ClientEntity client = request.clientHealthId() != null
                ? requireClient(ctx.tenantId(), request.clientHealthId())
                : new ClientEntity();

        IdentityStatus previousStatus = client.getStatus();
        if (client.getId() == null) {
            client.setTenantId(ctx.tenantId());
            client.setStatus(Boolean.TRUE.equals(request.issueProvisionalIdentifier()) || isSelfRegistration(request.registrationType())
                    ? IdentityStatus.PROVISIONAL
                    : IdentityStatus.REGISTERED);
            client.setGoldenRecordFlag(true);
        }
        mergeClientData(client, request);
        client.setVerificationStatus(initialVerificationState(request.registrationType()));
        client.setIdentityAssuranceLevel(Math.max(client.getIdentityAssuranceLevel(), initialAssuranceLevel(request.registrationType())));
        client.setActiveFlag(true);
        client = clientRepository.save(client);
        addressDelegationPublisher.emitAddressUpserted(client);

        ClientRegistrationEntity registration = new ClientRegistrationEntity();
        registration.setTenantId(ctx.tenantId());
        registration.setClientHealthId(client.getHealthId());
        registration.setRegistrationType(request.registrationType());
        registration.setInitiatedBy(ctx.actorId());
        registration.setInitiatedChannel(request.initiatedChannel());
        registration.setLinkedFacilityId(request.linkedFacilityId());
        registration.setLinkedProviderId(blankToNull(request.linkedProviderId()));
        registration.setLinkedServiceContextId(request.linkedServiceContextId());
        registration.setWorkflowState(isPartial(request) ? ClientRegistrationWorkflowState.PARTIALLY_CAPTURED : ClientRegistrationWorkflowState.INITIATED);
        registration.setCompletionState(isPartial(request) ? "PARTIAL" : "CAPTURED");
        registration.setVerificationState(client.getVerificationStatus());
        registration.setNotes(request.notes());
        registration.setProvenance(toJson(mapOf(
                "actorId", ctx.actorId(),
                "actorType", nullSafe(ctx.actorType()),
                "registrationType", request.registrationType().name(),
                "initiatedChannel", request.initiatedChannel(),
                "facilityId", request.linkedFacilityId(),
                "providerId", blankToNull(request.linkedProviderId()),
                "serviceContextId", blankToNull(request.linkedServiceContextId()),
                "workspaceId", ctx.workspaceId()
        )));
        registration = registrationRepository.save(registration);

        if (shouldIssueProvisionalIdentifier(request)) {
            ensureIdentifier(client, "PROVISIONAL_ID", provisionalIdentifierValue(client), true, "VITO_V2");
        }
        if (request.nationalIdReference() != null && !request.nationalIdReference().isBlank()) {
            ensureIdentifier(client, "NATIONAL_ID_REFERENCE", request.nationalIdReference().trim(), false, "REGISTRATION_CAPTURE");
        }
        if (request.passportReference() != null && !request.passportReference().isBlank()) {
            ensureIdentifier(client, "PASSPORT_REFERENCE", request.passportReference().trim(), false, "REGISTRATION_CAPTURE");
        }
        if (request.medicalAidNumber() != null && !request.medicalAidNumber().isBlank()) {
            // Optional: tie the medical-aid membership number to the Health ID as an identifier.
            ensureIdentifier(client, "MEDICAL_AID_NUMBER", request.medicalAidNumber().trim(), false, "REGISTRATION_CAPTURE");
        }
        if (request.previousNames() != null) {
            for (String previousName : request.previousNames()) {
                if (previousName != null && !previousName.isBlank()) {
                    ClientAliasEntity alias = new ClientAliasEntity();
                    alias.setTenantId(ctx.tenantId());
                    alias.setClientHealthId(client.getHealthId());
                    alias.setAliasType("PREVIOUS_NAME");
                    alias.setAliasValue(previousName.trim());
                    alias.setSource(request.registrationType().name());
                    alias.setConfidence(BigDecimal.valueOf(0.70));
                    aliasRepository.save(alias);
                }
            }
        }

        if (previousStatus != client.getStatus()) {
            recordStatusHistory(client, previousStatus, client.getStatus(), "Registration initiated", ctx);
        }
        audit(client.getHealthId(), "REGISTRATION_INITIATED", "CLIENT_REGISTRATION",
                registration.getRegistrationId().toString(), null,
                Map.of("registrationType", request.registrationType().name(), "channel", request.initiatedChannel()), ctx);
        publishEvent("CLIENT", client.getHealthId().toString(), "client.registration.initiated",
                Map.of("healthId", client.getHealthId(), "registrationId", registration.getRegistrationId(),
                        "registrationType", request.registrationType().name(), "channel", request.initiatedChannel()), ctx);
        return getClientProfile(client.getHealthId());
    }

    @Transactional
    public ClientRegistryDtos.ClientProfileResponse submitRegistration(UUID registrationId) {
        TrustContext ctx = TrustContextHolder.require();
        ClientRegistrationEntity registration = requireRegistration(ctx.tenantId(), registrationId);
        ClientEntity client = requireClient(ctx.tenantId(), registration.getClientHealthId());

        registration.setWorkflowState(ClientRegistrationWorkflowState.SUBMITTED);
        registration.setCompletionState("SUBMITTED");
        registration.setSubmissionDate(OffsetDateTime.now());
        if (client.getVerificationStatus() == ClientVerificationState.UNVERIFIED) {
            registration.setVerificationState(ClientVerificationState.SELF_ASSERTED);
            client.setVerificationStatus(ClientVerificationState.SELF_ASSERTED);
        }
        registrationRepository.save(registration);

        IdentityStatus nextStatus = client.getVerificationStatus() == ClientVerificationState.VERIFIED
                ? IdentityStatus.ACTIVE
                : IdentityStatus.PENDING_VERIFICATION;
        if (client.getStatus() != nextStatus) {
            IdentityStatus previous = client.getStatus();
            client.setStatus(nextStatus);
            clientRepository.save(client);
            recordStatusHistory(client, previous, nextStatus, "Registration submitted", ctx);
        }
        if (nextStatus == IdentityStatus.PENDING_VERIFICATION) {
            createOpenVerificationReview(client.getHealthId(), registration.getRegistrationId(),
                    "REGISTRATION_TRIAGE", "Registration awaits identity verification", ctx);
            createStewardshipActionInternal(client.getHealthId(), registration.getRegistrationId(), null,
                    ClientStewardshipActionType.VERIFY_IDENTITY, null, OffsetDateTime.now().plusDays(3),
                    "Registration submitted pending identity verification", ctx);
        }

        audit(client.getHealthId(), "REGISTRATION_SUBMITTED", "CLIENT_REGISTRATION",
                registrationId.toString(), null, Map.of("workflowState", registration.getWorkflowState().name()), ctx);
        publishEvent("CLIENT", client.getHealthId().toString(), "client.registration.completed",
                Map.of("healthId", client.getHealthId(), "registrationId", registrationId,
                        "workflowState", registration.getWorkflowState().name()), ctx);
        return getClientProfile(client.getHealthId());
    }

    @Transactional
    public ClientRegistryDtos.EvidenceView addEvidence(UUID healthId, ClientRegistryDtos.AddEvidenceRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity client = requireClient(ctx.tenantId(), healthId);
        requireRegistration(ctx.tenantId(), request.registrationId());

        ClientIdentityEvidenceEntity entity = new ClientIdentityEvidenceEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setClientHealthId(client.getHealthId());
        entity.setRegistrationId(request.registrationId());
        entity.setEvidenceType(request.evidenceType());
        entity.setEvidenceReference(request.evidenceReference());
        entity.setVerificationState(request.verificationState() != null ? request.verificationState() : client.getVerificationStatus());
        entity.setNotes(request.notes());
        entity = evidenceRepository.save(entity);

        if (client.getVerificationStatus() == ClientVerificationState.UNVERIFIED) {
            client.setVerificationStatus(ClientVerificationState.PARTIALLY_VERIFIED);
            clientRepository.save(client);
        }

        audit(client.getHealthId(), "EVIDENCE_CAPTURED", "CLIENT_IDENTITY_EVIDENCE",
                entity.getEvidenceId().toString(), null, Map.of("evidenceType", entity.getEvidenceType()), ctx);
        publishEvent("CLIENT", client.getHealthId().toString(), "client.verification.updated",
                Map.of("healthId", client.getHealthId(), "evidenceId", entity.getEvidenceId(),
                        "verificationState", entity.getVerificationState().name()), ctx);
        return toEvidenceView(entity);
    }

    @Transactional
    public ClientRegistryDtos.VerificationReviewView recordVerificationReview(UUID healthId,
                                                                              ClientRegistryDtos.RecordVerificationReviewRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        ClientEntity client = requireClient(ctx.tenantId(), healthId);

        ClientVerificationReviewEntity review = new ClientVerificationReviewEntity();
        review.setTenantId(ctx.tenantId());
        review.setClientHealthId(healthId);
        review.setRegistrationId(request.registrationId());
        review.setReviewType(request.reviewType());
        review.setStatus("COMPLETED");
        review.setReviewer(ctx.actorId());
        review.setDecision(request.decision());
        review.setNotes(request.notes());
        review.setReviewedAt(OffsetDateTime.now());
        review = verificationReviewRepository.save(review);
        closeOpenVerificationReviews(client.getHealthId(), request.registrationId(), ctx);

        IdentityStatus previousStatus = client.getStatus();
        if ("VERIFIED".equalsIgnoreCase(request.decision()) || "APPROVED".equalsIgnoreCase(request.decision())) {
            client.setVerificationStatus(ClientVerificationState.VERIFIED);
            client.setIdentityAssuranceLevel(request.identityAssuranceLevel() != null ? request.identityAssuranceLevel() : Math.max(2, client.getIdentityAssuranceLevel()));
            client.setStatus(IdentityStatus.ACTIVE);
            client.setActiveFlag(true);
            issueImpiloIdIfNeeded(client, request.issueImpiloId() == null || request.issueImpiloId());
            if (request.registrationId() != null) {
                ClientRegistrationEntity registration = requireRegistration(ctx.tenantId(), request.registrationId());
                registration.setWorkflowState(ClientRegistrationWorkflowState.COMPLETED);
                registration.setCompletionState("COMPLETED");
                registration.setVerificationState(ClientVerificationState.VERIFIED);
                registrationRepository.save(registration);
            }
        } else {
            client.setVerificationStatus(ClientVerificationState.REVIEW_REQUIRED);
            client.setStatus(IdentityStatus.FLAGGED_FOR_REVIEW);
            createStewardshipActionInternal(client.getHealthId(), request.registrationId(), null,
                    ClientStewardshipActionType.VERIFY_IDENTITY, null, OffsetDateTime.now().plusDays(2),
                    "Verification review requires follow-up", ctx);
        }
        clientRepository.save(client);
        if (previousStatus != client.getStatus()) {
            recordStatusHistory(client, previousStatus, client.getStatus(), "Verification review decision", ctx);
        }

        audit(client.getHealthId(), "VERIFICATION_REVIEW_RECORDED", "CLIENT_VERIFICATION_REVIEW",
                review.getReviewId().toString(), null,
                Map.of("decision", review.getDecision(), "reviewType", review.getReviewType()), ctx);
        publishEvent("CLIENT", client.getHealthId().toString(), "client.verification.updated",
                Map.of("healthId", client.getHealthId(), "reviewId", review.getReviewId(),
                        "verificationState", client.getVerificationStatus().name()), ctx);
        return toVerificationReviewView(review);
    }

    @Transactional
    public List<ClientRegistryDtos.MatchCandidateView> runMatching(UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity client = requireClient(ctx.tenantId(), healthId);
        List<MatchResultEntity> results = matchingEngine.findMatches(ctx.tenantId(), healthId);
        if (!results.isEmpty()) {
            IdentityStatus previous = client.getStatus();
            client.setStatus(IdentityStatus.PENDING_MATCH_REVIEW);
            clientRepository.save(client);
            if (previous != client.getStatus()) {
                recordStatusHistory(client, previous, client.getStatus(), "Potential duplicate candidates identified", ctx);
            }
        }

        for (MatchResultEntity result : results) {
            DedupCaseEntity dedupCase = new DedupCaseEntity();
            dedupCase.setTenantId(ctx.tenantId());
            dedupCase.setCandidateA(result.getSourceHealthId());
            dedupCase.setCandidateB(result.getCandidateHealthId());
            dedupCase.setMatchScore(result.getMatchScore());
            dedupCase.setStatus(result.getDisposition() == MatchDisposition.AUTO_LINKED ? "AUTO_LINKED" : "PENDING");
            dedupCase.setRisk(result.getMatchScore().doubleValue() >= 0.90 ? "HIGH" : "MEDIUM");
            dedupCase.setReasons(result.getMatchFields());
            dedupCaseRepository.save(dedupCase);

            createStewardshipActionInternal(client.getHealthId(), null, null,
                    ClientStewardshipActionType.REVIEW_DUPLICATE, null, OffsetDateTime.now().plusDays(1),
                    "Potential duplicate requires review for match " + result.getId(), ctx);

            publishEvent("DEDUP", String.valueOf(result.getId()), "client.match.candidate.created",
                    Map.of("matchId", result.getId(), "sourceHealthId", result.getSourceHealthId(),
                            "candidateHealthId", result.getCandidateHealthId(), "matchScore", result.getMatchScore()), ctx);
        }
        return results.stream().map(this::toMatchCandidateView).toList();
    }

    @Transactional
    public ClientRegistryDtos.MatchCandidateView reviewMatchCandidate(Long matchId,
                                                                      ClientRegistryDtos.ReviewMatchCandidateRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        MatchResultEntity match = matchResultRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("Match candidate not found"));
        if (!match.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }

        MatchDisposition disposition = switch (request.outcome()) {
            case CONFIRMED_DUPLICATE, MERGED -> MatchDisposition.MANUAL_LINKED;
            case CONFIRMED_DISTINCT -> MatchDisposition.REJECTED;
            case CANCELLED -> MatchDisposition.DEFERRED;
            default -> MatchDisposition.PENDING;
        };
        if (match.getDisposition() == MatchDisposition.PENDING && disposition != MatchDisposition.PENDING) {
            match = matchingEngine.resolve(matchId, disposition, ctx.actorId());
        }

        if (request.outcome() == ClientMatchReviewStatus.CONFIRMED_DUPLICATE) {
            createStewardshipActionInternal(match.getSourceHealthId(), null, null,
                    ClientStewardshipActionType.MERGE_REVIEW, null, OffsetDateTime.now().plusDays(2),
                    firstNonBlank(request.notes(), "Duplicate confirmed and awaiting merge review"), ctx);
            publishEvent("DEDUP", String.valueOf(match.getId()), "client.duplicate.confirmed",
                    mapOf("matchId", match.getId(), "sourceHealthId", match.getSourceHealthId(),
                            "candidateHealthId", match.getCandidateHealthId()), ctx);
        }

        audit(match.getSourceHealthId(), "MATCH_CANDIDATE_REVIEWED", "MATCH_RESULT", String.valueOf(matchId),
                null, Map.of("outcome", request.outcome().name()), ctx);
        return toMatchCandidateView(match, request.outcome());
    }

    @Transactional
    public ClientRegistryDtos.MergeCaseView createMergeCase(ClientRegistryDtos.CreateMergeCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        requireClient(ctx.tenantId(), request.primaryHealthId());
        requireClient(ctx.tenantId(), request.secondaryHealthId());

        ClientMergeCaseEntity mergeCase = new ClientMergeCaseEntity();
        mergeCase.setTenantId(ctx.tenantId());
        mergeCase.setPrimaryHealthId(request.primaryHealthId());
        mergeCase.setSecondaryHealthId(request.secondaryHealthId());
        mergeCase.setInitiatedBy(ctx.actorId());
        mergeCase.setLinkedMatchId(request.linkedMatchId());
        mergeCase.setLinkedDedupCaseId(request.linkedDedupCaseId());
        mergeCase.setSurvivorshipSummary(request.survivorshipSummary());
        mergeCase.setStatus("OPEN");
        mergeCase = mergeCaseRepository.save(mergeCase);

        createStewardshipActionInternal(request.primaryHealthId(), null, mergeCase.getMergeCaseId(),
                ClientStewardshipActionType.MERGE_REVIEW, null, OffsetDateTime.now().plusDays(1),
                "Merge case opened for stewardship review", ctx);
        publishEvent("MERGE", mergeCase.getMergeCaseId().toString(), "client.merge.case.created",
                Map.of("mergeCaseId", mergeCase.getMergeCaseId(), "primaryHealthId", request.primaryHealthId(),
                        "secondaryHealthId", request.secondaryHealthId()), ctx);
        return toMergeCaseView(mergeCase);
    }

    @Transactional
    public ClientRegistryDtos.MergeCaseView decideMergeCase(UUID mergeCaseId,
                                                            ClientRegistryDtos.DecideMergeCaseRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        ClientMergeCaseEntity mergeCase = mergeCaseRepository.findById(mergeCaseId)
                .orElseThrow(() -> new IllegalArgumentException("Merge case not found"));
        if (!mergeCase.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }

        mergeCase.setReviewedBy(ctx.actorId());
        mergeCase.setDecision(request.decision());
        mergeCase.setSurvivorshipSummary(firstNonBlank(request.survivorshipSummary(), mergeCase.getSurvivorshipSummary()));

        if ("APPROVED".equalsIgnoreCase(request.decision()) || "EXECUTED".equalsIgnoreCase(request.decision())) {
            ClientEntity primary = requireClient(ctx.tenantId(), mergeCase.getPrimaryHealthId());
            ClientEntity secondary = requireClient(ctx.tenantId(), mergeCase.getSecondaryHealthId());
            IdentityStatus previousSecondaryStatus = secondary.getStatus();
            MergeHistoryEntity history = mergeService.merge(
                    ctx.tenantId(),
                    primary.getCrid(),
                    secondary.getCrid(),
                    mergeCase.getLinkedDedupCaseId(),
                    "STEWARDSHIP_REVIEW",
                    toJson(mapOf("survivorshipSummary", blankToNull(mergeCase.getSurvivorshipSummary()))),
                    ctx.actorId(),
                    ctx.actorType()
            );
            secondary.setMergedIntoCrid(primary.getCrid());
            secondary.setGoldenRecordFlag(false);
            secondary.setActiveFlag(false);
            clientRepository.save(secondary);

            mergeCase.setStatus("EXECUTED");
            mergeCase.setMergeHistoryId(history.getId());
            mergeCase.setExecutedAt(OffsetDateTime.now());
            recordStatusHistory(secondary, previousSecondaryStatus, IdentityStatus.MERGED, "Merge executed", ctx);
            publishEvent("MERGE", mergeCase.getMergeCaseId().toString(), "client.merge.executed",
                    Map.of("mergeCaseId", mergeCase.getMergeCaseId(), "mergeHistoryId", history.getId(),
                            "primaryHealthId", mergeCase.getPrimaryHealthId(), "secondaryHealthId", mergeCase.getSecondaryHealthId()), ctx);
        } else {
            mergeCase.setStatus("REJECTED");
        }
        mergeCaseRepository.save(mergeCase);
        audit(mergeCase.getPrimaryHealthId(), "MERGE_CASE_DECIDED", "CLIENT_MERGE_CASE", mergeCaseId.toString(),
                null, Map.of("decision", mergeCase.getDecision(), "status", mergeCase.getStatus()), ctx);
        return toMergeCaseView(mergeCase);
    }

    @Transactional
    public ClientRegistryDtos.RelationshipView addRelationship(UUID healthId,
                                                               ClientRegistryDtos.AddRelationshipRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity client = requireClient(ctx.tenantId(), healthId);
        requireClient(ctx.tenantId(), request.relatedClientHealthId());
        if (ctx.mode() == AccessMode.EXTERNAL && !isSelfRelationshipType(request.relationshipType())) {
            throw new SecurityException("Self-service cannot create this relationship type");
        }

        ClientRelationshipEntity relationship = new ClientRelationshipEntity();
        relationship.setTenantId(ctx.tenantId());
        relationship.setClientHealthId(client.getHealthId());
        relationship.setRelatedClientHealthId(request.relatedClientHealthId());
        relationship.setRelationshipType(request.relationshipType());
        relationship.setStartDate(request.startDate());
        relationship.setEndDate(request.endDate());
        relationship.setNotes(request.notes());
        relationship = relationshipRepository.save(relationship);

        audit(client.getHealthId(), "CLIENT_RELATIONSHIP_CREATED", "CLIENT_RELATIONSHIP",
                relationship.getRelationshipId().toString(), null,
                mapOf("relatedClientHealthId", relationship.getRelatedClientHealthId(),
                        "relationshipType", relationship.getRelationshipType().name()), ctx);
        publishEvent("CLIENT", client.getHealthId().toString(), "client.relationship.created",
                Map.of("relationshipId", relationship.getRelationshipId(), "clientHealthId", client.getHealthId(),
                        "relatedClientHealthId", relationship.getRelatedClientHealthId(),
                        "relationshipType", relationship.getRelationshipType().name()), ctx);
        return toRelationshipView(relationship);
    }

    @Transactional
    public ClientRegistryDtos.AuthorizationLinkView addAuthorizationLink(UUID healthId,
                                                                         ClientRegistryDtos.AddAuthorizationLinkRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        requireClient(ctx.tenantId(), healthId);

        ClientAuthorizationLinkEntity entity = new ClientAuthorizationLinkEntity();
        entity.setTenantId(ctx.tenantId());
        entity.setClientHealthId(healthId);
        entity.setAuthorisationType(request.authorisationType());
        entity.setReferenceId(request.referenceId());
        entity.setStatus(firstNonBlank(request.status(), "ACTIVE"));
        entity.setStartDate(request.startDate());
        entity.setEndDate(request.endDate());
        entity = authorizationLinkRepository.save(entity);
        audit(healthId, "CLIENT_AUTHORIZATION_LINK_CREATED", "CLIENT_AUTHORIZATION_LINK",
                entity.getAuthorizationLinkId().toString(), null,
                mapOf("authorisationType", entity.getAuthorisationType(), "referenceId", entity.getReferenceId()), ctx);
        return toAuthorizationLinkView(entity);
    }

    @Transactional
    public ClientRegistryDtos.StewardshipActionView createStewardshipAction(UUID healthId,
                                                                            ClientRegistryDtos.CreateStewardshipActionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        requireClient(ctx.tenantId(), healthId);
        ClientStewardshipActionEntity action = createStewardshipActionInternal(
                healthId,
                request.relatedRegistrationId(),
                request.relatedMergeCaseId(),
                request.actionType(),
                request.owner(),
                request.dueDate(),
                request.completionNotes(),
                ctx
        );
        return toStewardshipActionView(action);
    }

    @Transactional
    public ClientRegistryDtos.StewardshipActionView updateStewardshipAction(UUID actionId,
                                                                            ClientRegistryDtos.UpdateStewardshipActionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        ClientStewardshipActionEntity action = stewardshipActionRepository.findById(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Stewardship action not found"));
        if (!action.getTenantId().equals(ctx.tenantId())) {
            throw new SecurityException("Tenant isolation violation");
        }
        action.setStatus(request.status());
        action.setCompletionNotes(request.completionNotes());
        if (request.status() == ClientStewardshipStatus.VERIFIED || request.status() == ClientStewardshipStatus.COMPLETED) {
            action.setVerifiedBy(firstNonBlank(request.verifiedBy(), ctx.actorId()));
            action.setVerifiedAt(OffsetDateTime.now());
        }
        action = stewardshipActionRepository.save(action);
        return toStewardshipActionView(action);
    }

    @Transactional
    public ClientRegistryDtos.ClientProfileResponse recordCorrection(UUID healthId,
                                                                     ClientRegistryDtos.RecordCorrectionRequest request) {
        TrustContext ctx = TrustContextHolder.require();
        assertFrontline(ctx);
        ClientEntity client = requireClient(ctx.tenantId(), healthId);
        Map<String, Object> before = clientSnapshot(client);

        if (request.requireReview()) {
            createStewardshipActionInternal(healthId, null, null,
                    ClientStewardshipActionType.CORRECT_RECORD, null, OffsetDateTime.now().plusDays(2),
                    firstNonBlank(request.notes(), "Sensitive demographic correction requires review"), ctx);
            IdentityStatus previousStatus = client.getStatus();
            client.setStatus(IdentityStatus.FLAGGED_FOR_REVIEW);
            if (previousStatus != client.getStatus()) {
                recordStatusHistory(client, previousStatus, client.getStatus(), "Sensitive correction queued for review", ctx);
            }
        } else {
            if (request.firstName() != null) client.setGivenName(request.firstName().trim());
            if (request.middleName() != null) client.setMiddleName(blankToNull(request.middleName()));
            if (request.lastName() != null) client.setFamilyName(request.lastName().trim());
            if (request.dateOfBirth() != null) client.setDateOfBirth(request.dateOfBirth());
            if (request.sex() != null) client.setSex(blankToNull(request.sex()));
            Map<String, Object> contacts = parseMap(client.getContacts());
            if (request.phone() != null) contacts.put("phone", request.phone());
            if (request.email() != null) contacts.put("email", request.email());
            client.setContacts(toJson(contacts));
        }

        clientRepository.save(client);
        audit(client.getHealthId(), "CLIENT_CORRECTION_RECORDED", "CLIENT", client.getHealthId().toString(),
                before, clientSnapshot(client), ctx);
        return getClientProfile(healthId);
    }

    @Transactional(readOnly = true)
    public ClientRegistryDtos.ClientProfileResponse getClientProfile(UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        ClientEntity client = requireClient(ctx.tenantId(), healthId);
        List<ClientRegistrationEntity> registrations = registrationRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);
        List<ClientIdentifierEntity> identifiers = identifierRepository.findByTenantIdAndClientHealthIdOrderByIssueDateDesc(ctx.tenantId(), healthId);
        List<ClientAliasEntity> aliases = aliasRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);
        List<ClientIdentityEvidenceEntity> evidence = evidenceRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);
        List<ClientVerificationReviewEntity> reviews = verificationReviewRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);
        List<MatchResultEntity> matches = gatherMatches(ctx.tenantId(), healthId);
        List<ClientMergeCaseEntity> mergeCases = mergeCaseRepository.findTimelineByTenantIdAndHealthId(ctx.tenantId(), healthId);
        List<ClientRelationshipEntity> relationships = relationshipRepository.findTimelineByTenantIdAndHealthId(ctx.tenantId(), healthId);
        List<ClientAuthorizationLinkEntity> authorizationLinks = authorizationLinkRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);
        List<ClientStewardshipActionEntity> stewardshipActions = stewardshipActionRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);
        List<ClientStatusHistoryEntity> statusHistory = statusHistoryRepository.findByTenantIdAndClientHealthIdOrderByChangedAtDesc(ctx.tenantId(), healthId);
        List<ClientAuditEventEntity> auditEvents = auditEventRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId);

        return new ClientRegistryDtos.ClientProfileResponse(
                toMaster(client),
                registrations.stream().map(this::toRegistrationView).toList(),
                identifiers.stream().map(this::toIdentifierView).toList(),
                aliases.stream().map(this::toAliasView).toList(),
                evidence.stream().map(this::toEvidenceView).toList(),
                reviews.stream().map(this::toVerificationReviewView).toList(),
                matches.stream().map(this::toMatchCandidateView).toList(),
                mergeCases.stream().map(this::toMergeCaseView).toList(),
                relationships.stream().map(this::toRelationshipView).toList(),
                authorizationLinks.stream().map(this::toAuthorizationLinkView).toList(),
                stewardshipActions.stream().map(this::toStewardshipActionView).toList(),
                statusHistory.stream().map(this::toStatusHistoryView).toList(),
                auditEvents.stream().map(this::toAuditEventView).toList()
        );
    }

    @Transactional(readOnly = true)
    public ClientIdentitySummary getIdentitySummary(UUID healthId) {
        TrustContext ctx = TrustContextHolder.require();
        var client = clientRepository.findByTenantIdAndHealthId(ctx.tenantId(), healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + healthId));
        return new ClientIdentitySummary(
                client.getHealthId(),
                client.getStatus(),
                client.getVerificationStatus(),
                client.getIdentityAssuranceLevel()
        );
    }

    @Transactional(readOnly = true)
    public ClientRegistryDtos.StewardshipWorkspaceResponse getStewardshipWorkspace() {
        TrustContext ctx = TrustContextHolder.require();
        assertSteward(ctx);
        List<ClientRegistryDtos.MatchCandidateView> duplicateQueue = matchResultRepository
                .findByTenantIdAndDisposition(ctx.tenantId(), MatchDisposition.PENDING, PageRequest.of(0, 25))
                .getContent().stream()
                .map(this::toMatchCandidateView)
                .toList();
        List<ClientRegistryDtos.VerificationReviewView> verificationQueue = verificationReviewRepository
                .findTop50ByTenantIdAndStatusOrderByCreatedAtDesc(ctx.tenantId(), "OPEN")
                .stream()
                .map(this::toVerificationReviewView)
                .toList();
        List<ClientRegistryDtos.StewardshipActionView> stewardshipQueue = stewardshipActionRepository
                .findByTenantIdAndStatus(ctx.tenantId(), ClientStewardshipStatus.OPEN, PageRequest.of(0, 25))
                .getContent().stream()
                .map(this::toStewardshipActionView)
                .toList();
        return new ClientRegistryDtos.StewardshipWorkspaceResponse(duplicateQueue, verificationQueue, stewardshipQueue);
    }

    private void mergeClientData(ClientEntity client, ClientRegistryDtos.CreateRegistrationRequest request) {
        if (request.firstName() != null && !request.firstName().isBlank()) {
            client.setGivenName(request.firstName().trim());
        }
        if (request.middleName() != null) {
            client.setMiddleName(blankToNull(request.middleName()));
        }
        if (request.lastName() != null && !request.lastName().isBlank()) {
            client.setFamilyName(request.lastName().trim());
        }
        if (request.dateOfBirth() != null) {
            client.setDateOfBirth(request.dateOfBirth());
        }
        if (request.sex() != null) {
            client.setSex(blankToNull(request.sex()));
        }

        Map<String, Object> demographics = parseMap(client.getDemographics());
        if (request.estimatedDateOfBirth() != null) demographics.put("estimatedDateOfBirth", request.estimatedDateOfBirth());
        if (request.preferredLanguage() != null && !request.preferredLanguage().isBlank()) {
            demographics.put("preferredLanguage", request.preferredLanguage().trim());
        }
        if (request.maritalStatus() != null && !request.maritalStatus().isBlank()) {
            demographics.put("maritalStatus", request.maritalStatus().trim());
        }
        if (request.metadata() != null) demographics.putAll(request.metadata());
        client.setDemographics(toJson(demographics));

        Map<String, Object> contacts = parseMap(client.getContacts());
        if (request.phone() != null && !request.phone().isBlank()) contacts.put("phone", request.phone().trim());
        if (request.email() != null && !request.email().isBlank()) contacts.put("email", request.email().trim());
        if (request.emergencyContactName() != null && !request.emergencyContactName().isBlank()) {
            contacts.put("emergencyContactName", request.emergencyContactName().trim());
        }
        if (request.emergencyContactPhone() != null && !request.emergencyContactPhone().isBlank()) {
            contacts.put("emergencyContactPhone", request.emergencyContactPhone().trim());
        }
        client.setContacts(toJson(contacts));

        Map<String, Object> address = parseMap(client.getAddress());
        if (request.addressLine1() != null) address.put("addressLine1", request.addressLine1());
        if (request.addressLine2() != null) address.put("addressLine2", request.addressLine2());
        if (request.city() != null) address.put("city", request.city());
        if (request.district() != null) address.put("district", request.district());
        if (request.province() != null) address.put("province", request.province());
        client.setAddress(toJson(address));
    }

    private ClientVerificationState initialVerificationState(ClientRegistrationType registrationType) {
        return switch (registrationType) {
            case PROVIDER_ASSISTED, FACILITY_REGISTRATION, COMMUNITY_REGISTRATION,
                    OUTREACH_REGISTRATION, VIRTUAL_REGISTRATION -> ClientVerificationState.PROVIDER_CAPTURED;
            default -> ClientVerificationState.SELF_ASSERTED;
        };
    }

    private int initialAssuranceLevel(ClientRegistrationType registrationType) {
        return isSelfRegistration(registrationType) ? 1 : 2;
    }

    private boolean isPartial(ClientRegistryDtos.CreateRegistrationRequest request) {
        return blank(request.firstName()) || blank(request.lastName()) || request.dateOfBirth() == null;
    }

    private boolean isSelfRegistration(ClientRegistrationType registrationType) {
        return registrationType == ClientRegistrationType.SELF_INITIATED
                || registrationType == ClientRegistrationType.VIRTUAL_REGISTRATION;
    }

    private boolean isSelfRelationshipType(ClientRelationshipType type) {
        return type == ClientRelationshipType.NEXT_OF_KIN || type == ClientRelationshipType.PROXY_ACCESS_FOR;
    }

    private void assertFrontline(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("This action requires an internal or governed registration context");
        }
        if (ctx.actorType() != null && FRONTLINE_ACTOR_TYPES.contains(ctx.actorType())) {
            return;
        }
        if ("SYSTEM".equalsIgnoreCase(ctx.actorType())) {
            return;
        }
        throw new SecurityException("Actor is not authorized for frontline registration operations");
    }

    private void assertSteward(TrustContext ctx) {
        if (ctx.mode() == AccessMode.EXTERNAL) {
            throw new SecurityException("This action requires an internal stewardship context");
        }
        if (ctx.actorType() != null && STEWARD_ACTOR_TYPES.contains(ctx.actorType())) {
            return;
        }
        if ("SYSTEM".equalsIgnoreCase(ctx.actorType())) {
            return;
        }
        throw new SecurityException("Actor is not authorized for identity stewardship operations");
    }

    private ClientEntity requireClient(UUID tenantId, UUID healthId) {
        return clientRepository.findByTenantIdAndHealthId(tenantId, healthId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found: " + healthId));
    }

    private ClientRegistrationEntity requireRegistration(UUID tenantId, UUID registrationId) {
        return registrationRepository.findById(registrationId)
                .filter(entity -> entity.getTenantId().equals(tenantId))
                .orElseThrow(() -> new IllegalArgumentException("Registration not found: " + registrationId));
    }

    private void ensureIdentifier(ClientEntity client, String type, String value, boolean primary, String source) {
        ClientIdentifierEntity identifier = new ClientIdentifierEntity();
        identifier.setTenantId(client.getTenantId());
        identifier.setClientHealthId(client.getHealthId());
        identifier.setIdentifierType(type);
        identifier.setIdentifierValue(value);
        identifier.setPrimaryFlag(primary);
        identifier.setSource(source);
        identifierRepository.save(identifier);
    }

    private void issueImpiloIdIfNeeded(ClientEntity client, boolean issueImpiloId) {
        if (!issueImpiloId || client.getImpiloId() != null) {
            return;
        }
        String impiloId = impiloIdFormat.generate();
        client.setImpiloId(impiloId);
        clientRepository.save(client);
        try {
            aliasService.issueAlias(client.getTenantId(), client.getHealthId(), "IMPILO_ID", impiloId);
        } catch (IllegalStateException ignored) {
            // Existing alias is acceptable during repeated verification review actions.
        }
        ensureIdentifier(client, "IMPILO_ID", impiloId, true, "VERIFICATION_REVIEW");
    }

    private void createOpenVerificationReview(UUID healthId,
                                              UUID registrationId,
                                              String reviewType,
                                              String notes,
                                              TrustContext ctx) {
        ClientVerificationReviewEntity review = new ClientVerificationReviewEntity();
        review.setTenantId(ctx.tenantId());
        review.setClientHealthId(healthId);
        review.setRegistrationId(registrationId);
        review.setReviewType(reviewType);
        review.setStatus("OPEN");
        review.setNotes(notes);
        verificationReviewRepository.save(review);
    }

    private void closeOpenVerificationReviews(UUID healthId, UUID registrationId, TrustContext ctx) {
        verificationReviewRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(ctx.tenantId(), healthId).stream()
                .filter(review -> review.getTenantId().equals(ctx.tenantId()))
                .filter(review -> "OPEN".equalsIgnoreCase(review.getStatus()))
                .filter(review -> registrationId == null || registrationId.equals(review.getRegistrationId()))
                .forEach(review -> {
                    review.setStatus("CLOSED");
                    review.setReviewedAt(OffsetDateTime.now());
                    review.setReviewer(ctx.actorId());
                    verificationReviewRepository.save(review);
                });
    }

    private ClientStewardshipActionEntity createStewardshipActionInternal(UUID healthId,
                                                                          UUID relatedRegistrationId,
                                                                          UUID relatedMergeCaseId,
                                                                          ClientStewardshipActionType actionType,
                                                                          String owner,
                                                                          OffsetDateTime dueDate,
                                                                          String completionNotes,
                                                                          TrustContext ctx) {
        ClientStewardshipActionEntity action = new ClientStewardshipActionEntity();
        action.setTenantId(ctx.tenantId());
        action.setClientHealthId(healthId);
        action.setRelatedRegistrationId(relatedRegistrationId);
        action.setRelatedMergeCaseId(relatedMergeCaseId);
        action.setActionType(actionType);
        action.setOwner(owner);
        action.setDueDate(dueDate);
        action.setCompletionNotes(completionNotes);
        action = stewardshipActionRepository.save(action);
        publishEvent("CLIENT", healthId.toString(), "client.stewardship.action.created",
                Map.of("actionId", action.getActionId(), "healthId", healthId, "actionType", actionType.name()), ctx);
        return action;
    }

    private void recordStatusHistory(ClientEntity client,
                                     IdentityStatus previousStatus,
                                     IdentityStatus newStatus,
                                     String reason,
                                     TrustContext ctx) {
        ClientStatusHistoryEntity history = new ClientStatusHistoryEntity();
        history.setTenantId(ctx.tenantId());
        history.setClientHealthId(client.getHealthId());
        history.setPreviousStatus(previousStatus != null ? previousStatus.name() : null);
        history.setNewStatus(newStatus.name());
        history.setReason(reason);
        history.setChangedBy(ctx.actorId());
        history.setProvenanceContext(toJson(mapOf(
                "actorId", ctx.actorId(),
                "actorType", nullSafe(ctx.actorType()),
                "facilityId", ctx.facilityId(),
                "workspaceId", ctx.workspaceId(),
                "mode", ctx.mode() != null ? ctx.mode().name() : null
        )));
        statusHistoryRepository.save(history);
        publishEvent("CLIENT", client.getHealthId().toString(), "client.status.changed",
                mapOf("healthId", client.getHealthId(), "previousStatus", previousStatus != null ? previousStatus.name() : null,
                        "newStatus", newStatus.name(), "reason", reason), ctx);
    }

    private void audit(UUID clientHealthId,
                       String action,
                       String targetEntity,
                       String targetId,
                       Map<String, Object> beforeState,
                       Map<String, Object> afterState,
                       TrustContext ctx) {
        ClientAuditEventEntity event = new ClientAuditEventEntity();
        event.setTenantId(ctx.tenantId());
        event.setClientHealthId(clientHealthId);
        event.setActor(ctx.actorId());
        event.setActorType(ctx.actorType());
        event.setAction(action);
        event.setTargetEntity(targetEntity);
        event.setTargetId(targetId);
        event.setBeforeState(beforeState != null ? toJson(beforeState) : null);
        event.setAfterState(afterState != null ? toJson(afterState) : null);
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setProvenanceContext(toJson(mapOf(
                "facilityId", ctx.facilityId(),
                "workspaceId", ctx.workspaceId(),
                "actorType", nullSafe(ctx.actorType()),
                "mode", ctx.mode() != null ? ctx.mode().name() : null
        )));
        auditEventRepository.save(event);
    }

    private void publishEvent(String aggregateType, String aggregateId, String eventType, Map<String, Object> payload, TrustContext ctx) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType(aggregateType);
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(toJson(payload));
        event.setTenantId(ctx.tenantId() != null ? ctx.tenantId().toString() : null);
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setSubjectType(aggregateType);
        event.setSubjectId(aggregateId);
        outboxRepository.save(event);
    }

    private List<MatchResultEntity> gatherMatches(UUID tenantId, UUID healthId) {
        Set<Long> seenIds = new LinkedHashSet<>();
        List<MatchResultEntity> results = new ArrayList<>();
        Page<MatchResultEntity> sourcePage = matchResultRepository.findByTenantIdAndSourceHealthId(tenantId, healthId, PageRequest.of(0, 100));
        Page<MatchResultEntity> candidatePage = matchResultRepository.findByTenantIdAndCandidateHealthId(tenantId, healthId, PageRequest.of(0, 100));
        for (MatchResultEntity entity : sourcePage.getContent()) {
            if (seenIds.add(entity.getId())) {
                results.add(entity);
            }
        }
        for (MatchResultEntity entity : candidatePage.getContent()) {
            if (seenIds.add(entity.getId())) {
                results.add(entity);
            }
        }
        return results;
    }

    private ClientRegistryDtos.ClientRegistrySummaryResponse toSummary(ClientEntity client) {
        List<ClientRegistrationEntity> registrations = registrationRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(
                client.getTenantId(),
                client.getHealthId()
        );
        String latestRegistrationType = registrations.isEmpty() ? null : registrations.get(0).getRegistrationType().name();
        String latestChannel = registrations.isEmpty() ? null : registrations.get(0).getInitiatedChannel();
        long openStewardship = stewardshipActionRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(
                        client.getTenantId(),
                        client.getHealthId()
                ).stream()
                .filter(action -> action.getStatus() == ClientStewardshipStatus.OPEN || action.getStatus() == ClientStewardshipStatus.IN_PROGRESS)
                .count();
        long openMatches = gatherMatches(client.getTenantId(), client.getHealthId()).stream()
                .filter(match -> match.getDisposition() == MatchDisposition.PENDING)
                .count();
        return new ClientRegistryDtos.ClientRegistrySummaryResponse(
                client.getHealthId(),
                client.getCrid(),
                client.getImpiloId(),
                buildDisplayName(client),
                client.getStatus(),
                client.getVerificationStatus(),
                client.getIdentityAssuranceLevel(),
                client.isGoldenRecordFlag(),
                client.isActiveFlag(),
                client.isDeceasedFlag(),
                latestRegistrationType,
                latestChannel,
                openStewardship,
                openMatches
        );
    }

    private ClientRegistryDtos.ClientMaster toMaster(ClientEntity client) {
        return new ClientRegistryDtos.ClientMaster(
                client.getHealthId(),
                client.getCrid(),
                client.getImpiloId(),
                client.getGivenName(),
                client.getMiddleName(),
                client.getFamilyName(),
                client.getDateOfBirth(),
                client.getSex(),
                client.getStatus(),
                client.getVerificationStatus(),
                client.getIdentityAssuranceLevel(),
                client.isActiveFlag(),
                client.isDeceasedFlag(),
                client.isGoldenRecordFlag(),
                parseMap(client.getDemographics()),
                parseMap(client.getContacts()),
                parseMap(client.getAddress()),
                client.getCreatedAt(),
                client.getUpdatedAt()
        );
    }

    private ClientRegistryDtos.RegistrationView toRegistrationView(ClientRegistrationEntity entity) {
        return new ClientRegistryDtos.RegistrationView(
                entity.getRegistrationId(),
                entity.getRegistrationType(),
                entity.getInitiatedChannel(),
                entity.getInitiatedBy(),
                entity.getLinkedFacilityId(),
                entity.getLinkedProviderId(),
                entity.getLinkedServiceContextId(),
                entity.getWorkflowState(),
                entity.getCompletionState(),
                entity.getVerificationState(),
                entity.getSubmissionDate(),
                entity.getNotes(),
                parseMap(entity.getProvenance())
        );
    }

    private ClientRegistryDtos.IdentifierView toIdentifierView(ClientIdentifierEntity entity) {
        return new ClientRegistryDtos.IdentifierView(
                entity.getIdentifierId(),
                entity.getIdentifierType(),
                entity.getIdentifierValue(),
                entity.getStatus(),
                entity.isPrimaryFlag(),
                entity.getIssueDate(),
                entity.getExpiryDate(),
                entity.getSource()
        );
    }

    private ClientRegistryDtos.AliasView toAliasView(ClientAliasEntity entity) {
        return new ClientRegistryDtos.AliasView(
                entity.getAliasId(),
                entity.getAliasType(),
                entity.getAliasValue(),
                entity.getSource(),
                entity.getConfidence(),
                entity.isActiveFlag()
        );
    }

    private ClientRegistryDtos.EvidenceView toEvidenceView(ClientIdentityEvidenceEntity entity) {
        return new ClientRegistryDtos.EvidenceView(
                entity.getEvidenceId(),
                entity.getRegistrationId(),
                entity.getEvidenceType(),
                entity.getEvidenceReference(),
                entity.getVerificationState(),
                entity.getVerifiedBy(),
                entity.getVerifiedAt(),
                entity.getNotes()
        );
    }

    private ClientRegistryDtos.VerificationReviewView toVerificationReviewView(ClientVerificationReviewEntity entity) {
        return new ClientRegistryDtos.VerificationReviewView(
                entity.getReviewId(),
                entity.getRegistrationId(),
                entity.getReviewType(),
                entity.getStatus(),
                entity.getReviewer(),
                entity.getDecision(),
                entity.getNotes(),
                entity.getReviewedAt()
        );
    }

    private ClientRegistryDtos.MatchCandidateView toMatchCandidateView(MatchResultEntity entity) {
        return toMatchCandidateView(entity, null);
    }

    private ClientRegistryDtos.MatchCandidateView toMatchCandidateView(MatchResultEntity entity, ClientMatchReviewStatus override) {
        return new ClientRegistryDtos.MatchCandidateView(
                entity.getId(),
                entity.getSourceHealthId(),
                entity.getCandidateHealthId(),
                entity.getMatchScore(),
                summarizeMatchReasons(parseMap(entity.getMatchFields())),
                override != null ? override : mapDisposition(entity.getDisposition()),
                entity.getCreatedAt(),
                entity.getResolvedBy(),
                entity.getResolvedAt()
        );
    }

    private ClientRegistryDtos.MergeCaseView toMergeCaseView(ClientMergeCaseEntity entity) {
        return new ClientRegistryDtos.MergeCaseView(
                entity.getMergeCaseId(),
                entity.getPrimaryHealthId(),
                entity.getSecondaryHealthId(),
                entity.getInitiatedBy(),
                entity.getReviewedBy(),
                entity.getDecision(),
                entity.getSurvivorshipSummary(),
                entity.getStatus(),
                entity.getExecutedAt(),
                entity.getMergeHistoryId()
        );
    }

    private ClientRegistryDtos.RelationshipView toRelationshipView(ClientRelationshipEntity entity) {
        return new ClientRegistryDtos.RelationshipView(
                entity.getRelationshipId(),
                entity.getClientHealthId(),
                entity.getRelatedClientHealthId(),
                entity.getRelationshipType(),
                entity.getStartDate(),
                entity.getEndDate(),
                entity.getStatus(),
                entity.getNotes()
        );
    }

    private ClientRegistryDtos.AuthorizationLinkView toAuthorizationLinkView(ClientAuthorizationLinkEntity entity) {
        return new ClientRegistryDtos.AuthorizationLinkView(
                entity.getAuthorizationLinkId(),
                entity.getAuthorisationType(),
                entity.getReferenceId(),
                entity.getStatus(),
                entity.getStartDate(),
                entity.getEndDate()
        );
    }

    private ClientRegistryDtos.StewardshipActionView toStewardshipActionView(ClientStewardshipActionEntity entity) {
        return new ClientRegistryDtos.StewardshipActionView(
                entity.getActionId(),
                entity.getActionType(),
                entity.getOwner(),
                entity.getDueDate(),
                entity.getStatus(),
                entity.getCompletionNotes(),
                entity.getVerifiedBy(),
                entity.getVerifiedAt(),
                entity.getRelatedRegistrationId(),
                entity.getRelatedMergeCaseId()
        );
    }

    private ClientRegistryDtos.StatusHistoryView toStatusHistoryView(ClientStatusHistoryEntity entity) {
        return new ClientRegistryDtos.StatusHistoryView(
                entity.getStatusHistoryId(),
                entity.getPreviousStatus(),
                entity.getNewStatus(),
                entity.getReason(),
                entity.getChangedBy(),
                entity.getChangedAt(),
                parseMap(entity.getProvenanceContext())
        );
    }

    private ClientRegistryDtos.AuditEventView toAuditEventView(ClientAuditEventEntity entity) {
        return new ClientRegistryDtos.AuditEventView(
                entity.getAuditEventId(),
                entity.getActor(),
                entity.getActorType(),
                entity.getAction(),
                entity.getTargetEntity(),
                entity.getTargetId(),
                parseMap(entity.getBeforeState()),
                parseMap(entity.getAfterState()),
                entity.getCorrelationId(),
                parseMap(entity.getProvenanceContext()),
                entity.getCreatedAt()
        );
    }

    private ClientMatchReviewStatus mapDisposition(MatchDisposition disposition) {
        return switch (disposition) {
            case AUTO_LINKED, MANUAL_LINKED -> ClientMatchReviewStatus.CONFIRMED_DUPLICATE;
            case REJECTED -> ClientMatchReviewStatus.CONFIRMED_DISTINCT;
            case DEFERRED -> ClientMatchReviewStatus.CANCELLED;
            default -> ClientMatchReviewStatus.NEEDS_REVIEW;
        };
    }

    private String summarizeMatchReasons(Map<String, Object> fieldScores) {
        if (fieldScores.isEmpty()) {
            return "Match generated by probabilistic pipeline";
        }
        return fieldScores.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce((left, right) -> left + ", " + right)
                .orElse("Match generated");
    }

    private Map<String, Object> clientSnapshot(ClientEntity client) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("firstName", client.getGivenName());
        snapshot.put("middleName", client.getMiddleName());
        snapshot.put("lastName", client.getFamilyName());
        snapshot.put("dateOfBirth", client.getDateOfBirth() != null ? client.getDateOfBirth().toString() : null);
        snapshot.put("sex", client.getSex());
        snapshot.put("status", client.getStatus().name());
        snapshot.put("verificationStatus", client.getVerificationStatus().name());
        snapshot.put("contacts", parseMap(client.getContacts()));
        return snapshot;
    }

    private String buildDisplayName(ClientEntity client) {
        return List.of(client.getGivenName(), client.getMiddleName(), client.getFamilyName()).stream()
                .filter(value -> value != null && !value.isBlank())
                .reduce((left, right) -> left + " " + right)
                .orElse("Unnamed client");
    }

    private String provisionalIdentifierValue(ClientEntity client) {
        String source = client.getHealthId() != null ? client.getHealthId().toString().replace("-", "") : UUID.randomUUID().toString().replace("-", "");
        return "TEMP-" + source.substring(0, Math.min(12, source.length())).toUpperCase();
    }

    private boolean shouldIssueProvisionalIdentifier(ClientRegistryDtos.CreateRegistrationRequest request) {
        return Boolean.TRUE.equals(request.issueProvisionalIdentifier());
    }

    private String normalizeQuery(String query) {
        return query != null && !query.isBlank() ? query.trim() : null;
    }

    private String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback;
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String blankToNull(String value) {
        return blank(value) ? null : value.trim();
    }

    private Map<String, Object> mapOf(Object... keyValues) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            values.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return values;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize JSON payload", e);
        }
    }

    private Map<String, Object> parseMap(String value) {
        if (value == null || value.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() {});
        } catch (Exception ignored) {
            return new LinkedHashMap<>();
        }
    }
}
