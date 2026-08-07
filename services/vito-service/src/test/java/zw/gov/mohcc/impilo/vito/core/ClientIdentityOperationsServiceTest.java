package zw.gov.mohcc.impilo.vito.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.api.dto.ClientRegistryDtos;
import zw.gov.mohcc.impilo.vito.api.dto.ClientIdentitySummary;
import zw.gov.mohcc.impilo.vito.config.VitoProperties;
import zw.gov.mohcc.impilo.vito.core.matching.MatchingEngine;
import zw.gov.mohcc.impilo.vito.core.ClientRelationshipType;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAliasEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientAuditEventEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientIdentifierEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientRegistrationEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientStatusHistoryEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientStewardshipActionEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientVerificationReviewEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.DedupCaseEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.vito.persistence.entity.MatchResultEntity;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientIdentityOperationsServiceTest {

    @Mock private ClientRepository clientRepository;
    @Mock private ClientRegistrationRepository registrationRepository;
    @Mock private ClientIdentifierRepository identifierRepository;
    @Mock private ClientAliasRepository aliasRepository;
    @Mock private ClientIdentityEvidenceRepository evidenceRepository;
    @Mock private ClientVerificationReviewRepository verificationReviewRepository;
    @Mock private MatchResultRepository matchResultRepository;
    @Mock private DedupCaseRepository dedupCaseRepository;
    @Mock private ClientMergeCaseRepository mergeCaseRepository;
    @Mock private ClientRelationshipRepository relationshipRepository;
    @Mock private ClientAuthorizationLinkRepository authorizationLinkRepository;
    @Mock private ClientStewardshipActionRepository stewardshipActionRepository;
    @Mock private ClientStatusHistoryRepository statusHistoryRepository;
    @Mock private ClientAuditEventRepository auditEventRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private ClientIdentityOperationsService service;
    private StubMatchingEngine matchingEngine;
    private UUID tenantId;

    private final AtomicReference<ClientEntity> savedClient = new AtomicReference<>();
    private final AtomicReference<ClientRegistrationEntity> savedRegistration = new AtomicReference<>();
    private final List<ClientIdentifierEntity> identifiers = new ArrayList<>();
    private final List<ClientVerificationReviewEntity> verificationReviews = new ArrayList<>();
    private final List<ClientStewardshipActionEntity> stewardshipActions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        matchingEngine = new StubMatchingEngine();
        service = new ClientIdentityOperationsService(
                clientRepository,
                registrationRepository,
                identifierRepository,
                aliasRepository,
                evidenceRepository,
                verificationReviewRepository,
                matchResultRepository,
                dedupCaseRepository,
                mergeCaseRepository,
                relationshipRepository,
                authorizationLinkRepository,
                stewardshipActionRepository,
                statusHistoryRepository,
                auditEventRepository,
                outboxRepository,
                matchingEngine,
                null,
                null,
                null,
                new ObjectMapper(),
                new ClientAddressDelegationPublisher(outboxRepository, new ObjectMapper())
        );

        tenantId = UUID.randomUUID();
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "actor-1",
                // Was "REGISTRY_ADMIN" — a role name no client emits, so this suite drove the
                // service with an actor type no real request can carry. OPERATOR is what a
                // registration desk actually presents. See ActorTypeGuard.
                "OPERATOR",
                "CLIENT_REGISTRATION",
                "device-1",
                UUID.randomUUID(),
                null,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL
        ));

        when(clientRepository.save(any(ClientEntity.class))).thenAnswer(invocation -> {
            ClientEntity entity = invocation.getArgument(0);
            if (entity.getHealthId() == null) {
                entity.setHealthId(UUID.randomUUID());
            }
            if (entity.getCrid() == null) {
                entity.setCrid(UUID.randomUUID());
            }
            savedClient.set(entity);
            return entity;
        });
        when(clientRepository.findByTenantIdAndHealthId(any(), any())).thenAnswer(invocation -> {
            ClientEntity entity = savedClient.get();
            return entity != null && entity.getTenantId().equals(invocation.getArgument(0))
                    && entity.getHealthId().equals(invocation.getArgument(1))
                    ? Optional.of(entity)
                    : Optional.empty();
        });
        when(registrationRepository.save(any(ClientRegistrationEntity.class))).thenAnswer(invocation -> {
            ClientRegistrationEntity entity = invocation.getArgument(0);
            if (entity.getRegistrationId() == null) {
                entity.setRegistrationId(UUID.randomUUID());
            }
            savedRegistration.set(entity);
            return entity;
        });
        when(registrationRepository.findById(any())).thenAnswer(invocation -> {
            ClientRegistrationEntity entity = savedRegistration.get();
            return entity != null && entity.getRegistrationId().equals(invocation.getArgument(0))
                    ? Optional.of(entity)
                    : Optional.empty();
        });
        when(registrationRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenAnswer(invocation -> {
            ClientRegistrationEntity entity = savedRegistration.get();
            return entity != null && entity.getClientHealthId().equals(invocation.getArgument(0))
                    ? List.of(entity)
                    : List.of();
        });
        when(registrationRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenAnswer(invocation -> {
            ClientRegistrationEntity entity = savedRegistration.get();
            return entity != null
                    && entity.getTenantId().equals(invocation.getArgument(0))
                    && entity.getClientHealthId().equals(invocation.getArgument(1))
                    ? List.of(entity)
                    : List.of();
        });
        when(identifierRepository.save(any(ClientIdentifierEntity.class))).thenAnswer(invocation -> {
            ClientIdentifierEntity entity = invocation.getArgument(0);
            if (entity.getIdentifierId() == null) {
                entity.setIdentifierId(UUID.randomUUID());
            }
            identifiers.add(entity);
            return entity;
        });
        when(identifierRepository.findByClientHealthIdOrderByIssueDateDesc(any())).thenAnswer(invocation ->
                identifiers.stream().filter(identifier -> identifier.getClientHealthId().equals(invocation.getArgument(0))).toList());
        when(identifierRepository.findByTenantIdAndClientHealthIdOrderByIssueDateDesc(any(), any())).thenAnswer(invocation ->
                identifiers.stream()
                        .filter(identifier -> identifier.getTenantId().equals(invocation.getArgument(0)))
                        .filter(identifier -> identifier.getClientHealthId().equals(invocation.getArgument(1)))
                        .toList());
        when(aliasRepository.save(any(ClientAliasEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(aliasRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(aliasRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(evidenceRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(evidenceRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(verificationReviewRepository.save(any(ClientVerificationReviewEntity.class))).thenAnswer(invocation -> {
            ClientVerificationReviewEntity entity = invocation.getArgument(0);
            if (entity.getReviewId() == null) {
                entity.setReviewId(UUID.randomUUID());
            }
            verificationReviews.removeIf(existing -> existing.getReviewId().equals(entity.getReviewId()));
            verificationReviews.add(entity);
            return entity;
        });
        when(verificationReviewRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenAnswer(invocation ->
                verificationReviews.stream().filter(review -> review.getClientHealthId().equals(invocation.getArgument(0))).toList());
        when(verificationReviewRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenAnswer(invocation ->
                verificationReviews.stream()
                        .filter(review -> review.getTenantId().equals(invocation.getArgument(0)))
                        .filter(review -> review.getClientHealthId().equals(invocation.getArgument(1)))
                        .toList());
        when(verificationReviewRepository.findTop50ByTenantIdAndStatusOrderByCreatedAtDesc(any(), anyString())).thenAnswer(invocation ->
                verificationReviews.stream().filter(review -> review.getTenantId().equals(invocation.getArgument(0)) && invocation.getArgument(1).equals(review.getStatus())).toList());
        when(stewardshipActionRepository.save(any(ClientStewardshipActionEntity.class))).thenAnswer(invocation -> {
            ClientStewardshipActionEntity entity = invocation.getArgument(0);
            if (entity.getActionId() == null) {
                entity.setActionId(UUID.randomUUID());
            }
            stewardshipActions.removeIf(existing -> existing.getActionId().equals(entity.getActionId()));
            stewardshipActions.add(entity);
            return entity;
        });
        when(stewardshipActionRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenAnswer(invocation ->
                stewardshipActions.stream().filter(action -> action.getClientHealthId().equals(invocation.getArgument(0))).toList());
        when(stewardshipActionRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenAnswer(invocation ->
                stewardshipActions.stream()
                        .filter(action -> action.getTenantId().equals(invocation.getArgument(0)))
                        .filter(action -> action.getClientHealthId().equals(invocation.getArgument(1)))
                        .toList());
        when(stewardshipActionRepository.findByTenantIdAndStatus(any(), any(), any())).thenAnswer(invocation ->
                new org.springframework.data.domain.PageImpl<>(
                        stewardshipActions.stream()
                                .filter(action -> action.getTenantId().equals(invocation.getArgument(0)) && action.getStatus().equals(invocation.getArgument(1)))
                                .toList()
                ));

        when(statusHistoryRepository.save(any(ClientStatusHistoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(auditEventRepository.save(any(ClientAuditEventEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(outboxRepository.save(any(EventOutboxEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(dedupCaseRepository.save(any(DedupCaseEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(matchResultRepository.findByTenantIdAndSourceHealthId(any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        when(matchResultRepository.findByTenantIdAndCandidateHealthId(any(), any(), any())).thenReturn(org.springframework.data.domain.Page.empty());
        when(mergeCaseRepository.findByPrimaryHealthIdOrSecondaryHealthIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(relationshipRepository.findByClientHealthIdOrRelatedClientHealthIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(authorizationLinkRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(statusHistoryRepository.findByClientHealthIdOrderByChangedAtDesc(any())).thenReturn(List.of());
        when(auditEventRepository.findByClientHealthIdOrderByCreatedAtDesc(any())).thenReturn(List.of());
        when(mergeCaseRepository.findTimelineByTenantIdAndHealthId(any(), any())).thenReturn(List.of());
        when(relationshipRepository.findTimelineByTenantIdAndHealthId(any(), any())).thenReturn(List.of());
        when(authorizationLinkRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
        when(statusHistoryRepository.findByTenantIdAndClientHealthIdOrderByChangedAtDesc(any(), any())).thenReturn(List.of());
        when(auditEventRepository.findByTenantIdAndClientHealthIdOrderByCreatedAtDesc(any(), any())).thenReturn(List.of());
    }

    @Test
    void getIdentitySummary_returnsLightweightIdentityFields() {
        UUID healthId = UUID.randomUUID();
        ClientEntity client = new ClientEntity();
        client.setTenantId(tenantId);
        client.setHealthId(healthId);
        client.setStatus(IdentityStatus.ACTIVE);
        client.setVerificationStatus(ClientVerificationState.VERIFIED);
        client.setIdentityAssuranceLevel(3);

        when(clientRepository.findByTenantIdAndHealthId(tenantId, healthId)).thenReturn(Optional.of(client));

        ClientIdentitySummary summary = service.getIdentitySummary(healthId);

        assertNotNull(summary);
        assertEquals(healthId, summary.healthId());
        assertEquals(IdentityStatus.ACTIVE, summary.identityStatus());
        assertEquals(ClientVerificationState.VERIFIED, summary.verificationStatus());
        assertEquals(3, summary.identityAssuranceLevel());
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void createRegistration_selfInitiated_createsProvisionalClientAndIdentifier() {
        ClientRegistryDtos.ClientProfileResponse response = service.createRegistration(
                new ClientRegistryDtos.CreateRegistrationRequest(
                        null,
                        ClientRegistrationType.SELF_INITIATED,
                        "SELF_SERVICE",
                        null,
                        null,
                        null,
                        "Alice",
                        null,
                        "Moyo",
                        LocalDate.of(1998, 6, 14),
                        false,
                        "FEMALE",
                        "0772000000",
                        null,
                        null,
                        null,
                        null,
                        "12 Mbuya Nehanda",
                        null,
                        "Harare",
                        "Harare",
                        "Harare",
                        "Self registration started from digital front door",
                        Map.of("source", "unit-test"),
                        List.of("Alice Ncube"),
                        null,
                        null,
                        null,
                        null,
                        true
                )
        );

        assertEquals(IdentityStatus.PROVISIONAL, savedClient.get().getStatus());
        assertEquals(ClientVerificationState.SELF_ASSERTED, savedClient.get().getVerificationStatus());
        assertNotNull(savedRegistration.get());
        assertFalse(identifiers.isEmpty());
        assertEquals("PROVISIONAL_ID", identifiers.get(0).getIdentifierType());
        assertEquals(IdentityStatus.PROVISIONAL, response.master().lifecycleStatus());
        assertEquals(1, response.registrations().size());
    }

    @Test
    void createRegistration_withProvisionalOptOut_doesNotCreateTemporaryIdentifier() {
        ClientRegistryDtos.ClientProfileResponse response = service.createRegistration(
                new ClientRegistryDtos.CreateRegistrationRequest(
                        null,
                        ClientRegistrationType.FACILITY_REGISTRATION,
                        "FACILITY_DESK",
                        null,
                        101L,
                        null,
                        "Nyasha",
                        null,
                        "Dube",
                        LocalDate.of(2000, 1, 5),
                        false,
                        "FEMALE",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "Facility registration without immediate temporary identifier",
                        Map.of(),
                        List.of(),
                        null,
                        null,
                        null,
                        null,
                        false
                )
        );

        assertEquals(IdentityStatus.REGISTERED, savedClient.get().getStatus());
        assertTrue(identifiers.isEmpty());
        assertTrue(response.identifiers().isEmpty());
    }

    @Test
    void createRegistration_persistsExtendedDemographicsAndReadBack() {
        ClientRegistryDtos.ClientProfileResponse response = service.createRegistration(
                new ClientRegistryDtos.CreateRegistrationRequest(
                        null,
                        ClientRegistrationType.FACILITY_REGISTRATION,
                        "EXPERIENCE_VITO_WIZARD",
                        null,
                        101L,
                        null,
                        "Tendai",
                        "Ruvarashe",
                        "Moyo",
                        LocalDate.of(1992, 4, 7),
                        false,
                        "MALE",
                        "+263771234567",
                        "tendai.moyo@example.zw",
                        "63-123456-A-78",
                        "P12345678",
                        "MA-2024-5567",
                        "12 Samora Machel Ave",
                        null,
                        "Harare",
                        "Harare",
                        "Harare",
                        "Extended demographics intake test",
                        Map.of("registrationMode", "HEALTH_WORKER_INITIATED"),
                        List.of(),
                        "en-ZW",
                        "MARRIED",
                        "Rudo Moyo",
                        "+263772345678",
                        true
                )
        );

        assertEquals("Ruvarashe", savedClient.get().getMiddleName());
        Map<String, Object> demographics = parseJsonMap(savedClient.get().getDemographics());
        assertEquals("en-ZW", demographics.get("preferredLanguage"));
        assertEquals("MARRIED", demographics.get("maritalStatus"));
        Map<String, Object> contacts = parseJsonMap(savedClient.get().getContacts());
        assertEquals("tendai.moyo@example.zw", contacts.get("email"));
        assertEquals("Rudo Moyo", contacts.get("emergencyContactName"));
        assertEquals("+263772345678", contacts.get("emergencyContactPhone"));
        Map<String, Object> address = parseJsonMap(savedClient.get().getAddress());
        assertEquals("12 Samora Machel Ave", address.get("addressLine1"));
        assertEquals("Harare", address.get("city"));
        assertTrue(identifiers.stream().anyMatch(i -> "PASSPORT_REFERENCE".equals(i.getIdentifierType())));
        assertEquals("Ruvarashe", response.master().middleName());
        assertEquals("en-ZW", response.master().demographics().get("preferredLanguage"));
        assertEquals("MARRIED", response.master().demographics().get("maritalStatus"));
        assertEquals("tendai.moyo@example.zw", response.master().contacts().get("email"));
        assertEquals("Rudo Moyo", response.master().contacts().get("emergencyContactName"));
        assertEquals("+263772345678", response.master().contacts().get("emergencyContactPhone"));
        assertEquals("Harare", response.master().address().get("city"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJsonMap(String json) {
        try {
            return new ObjectMapper().readValue(json != null ? json : "{}", Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void submitRegistration_createsVerificationQueueAndStewardshipAction() {
        ClientEntity client = baseClient();
        client.setStatus(IdentityStatus.PROVISIONAL);
        client.setVerificationStatus(ClientVerificationState.UNVERIFIED);
        savedClient.set(client);

        ClientRegistrationEntity registration = new ClientRegistrationEntity();
        registration.setRegistrationId(UUID.randomUUID());
        registration.setTenantId(tenantId);
        registration.setClientHealthId(client.getHealthId());
        registration.setRegistrationType(ClientRegistrationType.FACILITY_REGISTRATION);
        registration.setInitiatedBy("actor-1");
        registration.setInitiatedChannel("FACILITY_DESK");
        registration.setWorkflowState(ClientRegistrationWorkflowState.PARTIALLY_CAPTURED);
        savedRegistration.set(registration);

        ClientRegistryDtos.ClientProfileResponse response = service.submitRegistration(registration.getRegistrationId());

        assertEquals(IdentityStatus.PENDING_VERIFICATION, savedClient.get().getStatus());
        assertEquals(ClientVerificationState.SELF_ASSERTED, savedClient.get().getVerificationStatus());
        assertTrue(verificationReviews.stream().anyMatch(review -> "OPEN".equals(review.getStatus())));
        assertTrue(stewardshipActions.stream().anyMatch(action -> action.getActionType() == ClientStewardshipActionType.VERIFY_IDENTITY));
        assertEquals(IdentityStatus.PENDING_VERIFICATION, response.master().lifecycleStatus());
    }

    @Test
    void runMatching_createsDedupCaseAndReviewAction() {
        ClientEntity client = baseClient();
        client.setStatus(IdentityStatus.ACTIVE);
        client.setVerificationStatus(ClientVerificationState.VERIFIED);
        savedClient.set(client);

        MatchResultEntity match = new MatchResultEntity();
        match.setId(41L);
        match.setTenantId(tenantId);
        match.setSourceHealthId(client.getHealthId());
        match.setCandidateHealthId(UUID.randomUUID());
        match.setMatchScore(BigDecimal.valueOf(0.93));
        match.setMatchAlgorithm("VITO_V2_RULES");
        match.setMatchFields("{\"name\":0.92,\"dob\":1.0}");
        match.setDisposition(MatchDisposition.PENDING);
        matchingEngine.results = List.of(match);

        List<ClientRegistryDtos.MatchCandidateView> response = service.runMatching(client.getHealthId());

        assertEquals(IdentityStatus.PENDING_MATCH_REVIEW, savedClient.get().getStatus());
        assertEquals(1, response.size());
        assertTrue(stewardshipActions.stream().anyMatch(action -> action.getActionType() == ClientStewardshipActionType.REVIEW_DUPLICATE));
        verify(dedupCaseRepository, atLeastOnce()).save(any(DedupCaseEntity.class));
        verify(outboxRepository, atLeastOnce()).save(any(EventOutboxEntity.class));
    }

    @Test
    void recordBirthDyad_writesTheEdgeWhenBothPartiesAreKnownAndNotYetLinked() {
        UUID mother = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        when(clientRepository.findByTenantIdAndHealthId(tenantId, mother)).thenReturn(Optional.of(new ClientEntity()));
        when(clientRepository.findByTenantIdAndHealthId(tenantId, child)).thenReturn(Optional.of(new ClientEntity()));
        when(relationshipRepository
                .findFirstByTenantIdAndClientHealthIdAndRelatedClientHealthIdAndRelationshipTypeAndStatus(
                        tenantId, mother, child, ClientRelationshipType.BIRTH_MOTHER_OF, "ACTIVE"))
                .thenReturn(Optional.empty());

        assertTrue(service.recordBirthDyad(tenantId, mother, child));
        verify(relationshipRepository).save(any(zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity.class));
    }

    @Test
    void recordBirthDyad_isIdempotentWhenAnActiveEdgeAlreadyExists() {
        UUID mother = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        when(clientRepository.findByTenantIdAndHealthId(tenantId, mother)).thenReturn(Optional.of(new ClientEntity()));
        when(clientRepository.findByTenantIdAndHealthId(tenantId, child)).thenReturn(Optional.of(new ClientEntity()));
        when(relationshipRepository
                .findFirstByTenantIdAndClientHealthIdAndRelatedClientHealthIdAndRelationshipTypeAndStatus(
                        tenantId, mother, child, ClientRelationshipType.BIRTH_MOTHER_OF, "ACTIVE"))
                .thenReturn(Optional.of(new zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity()));

        assertFalse(service.recordBirthDyad(tenantId, mother, child));
        verify(relationshipRepository, org.mockito.Mockito.never())
                .save(any(zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity.class));
    }

    @Test
    void recordBirthDyad_recordsNothingWhenAPartyIsNotYetAKnownClient() {
        UUID mother = UUID.randomUUID();
        UUID child = UUID.randomUUID();
        when(clientRepository.findByTenantIdAndHealthId(tenantId, mother)).thenReturn(Optional.of(new ClientEntity()));
        when(clientRepository.findByTenantIdAndHealthId(tenantId, child)).thenReturn(Optional.empty());

        assertFalse(service.recordBirthDyad(tenantId, mother, child));
        verify(relationshipRepository, org.mockito.Mockito.never())
                .save(any(zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity.class));
    }

    @Test
    void recordBirthDyad_rejectsAPersonAsTheirOwnMother() {
        UUID self = UUID.randomUUID();
        assertFalse(service.recordBirthDyad(tenantId, self, self));
        verify(relationshipRepository, org.mockito.Mockito.never())
                .save(any(zw.gov.mohcc.impilo.vito.persistence.entity.ClientRelationshipEntity.class));
    }

    private ClientEntity baseClient() {
        ClientEntity client = new ClientEntity();
        client.setId(10L);
        client.setTenantId(tenantId);
        client.setHealthId(UUID.randomUUID());
        client.setCrid(UUID.randomUUID());
        client.setGivenName("Tariro");
        client.setFamilyName("Moyo");
        client.setGoldenRecordFlag(true);
        client.setActiveFlag(true);
        client.setIdentityAssuranceLevel(1);
        return client;
    }

    private static final class StubMatchingEngine extends MatchingEngine {
        private List<MatchResultEntity> results = List.of();

        private StubMatchingEngine() {
            super(null, null, new VitoProperties(), new ObjectMapper());
        }

        @Override
        public List<MatchResultEntity> findMatches(UUID tenantId, UUID sourceHealthId) {
            return results;
        }
    }
}
