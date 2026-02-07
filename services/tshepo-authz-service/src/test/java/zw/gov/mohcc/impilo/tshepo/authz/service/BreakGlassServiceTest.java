package zw.gov.mohcc.impilo.tshepo.authz.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.authz.config.AuthzProperties;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassCreateRequest;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassResponse;
import zw.gov.mohcc.impilo.tshepo.authz.dto.BreakGlassReviewRequest;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.BreakGlassRequestEntity;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.repository.BreakGlassRequestRepository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the BreakGlassService.
 *
 * <p>Covers creation, approval/rejection, active-check, and expiry handling
 * for break-glass emergency access override requests.</p>
 */
@ExtendWith(MockitoExtension.class)
class BreakGlassServiceTest {

    @Mock private BreakGlassRequestRepository breakGlassRepository;

    private AuthzProperties properties;
    private BreakGlassService breakGlassService;

    private static final UUID TENANT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final String ACTOR_ID = "actor-123";
    private static final String REVIEWER_ID = "supervisor-456";

    @BeforeEach
    void setUp() {
        properties = new AuthzProperties();
        properties.setBreakGlassTtlMinutes(60);
        properties.setBreakGlassReviewSlaHours(24);
        breakGlassService = new BreakGlassService(breakGlassRepository, properties);
    }

    // ════════════════════════════════════════════════════════════════════
    // createRequest
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("createRequest: persists entity with correct fields and PENDING_REVIEW status")
    void createRequest_persistsEntityWithCorrectFields() {
        BreakGlassCreateRequest request = new BreakGlassCreateRequest(
                TENANT_ID, ACTOR_ID, "Patient critically ill, need immediate access",
                "Patient", "cpid-12345"
        );

        when(breakGlassRepository.save(any(BreakGlassRequestEntity.class)))
                .thenAnswer(invocation -> {
                    BreakGlassRequestEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) {
                        entity.setId(UUID.randomUUID());
                    }
                    return entity;
                });

        BreakGlassResponse response = breakGlassService.createRequest(request);

        // Verify the entity was persisted
        ArgumentCaptor<BreakGlassRequestEntity> captor =
                ArgumentCaptor.forClass(BreakGlassRequestEntity.class);
        verify(breakGlassRepository).save(captor.capture());
        BreakGlassRequestEntity saved = captor.getValue();

        assertEquals(TENANT_ID, saved.getTenantId(),
                "Must save with correct tenant");
        assertEquals(ACTOR_ID, saved.getActorId(),
                "Must save with correct actor");
        assertEquals("Patient critically ill, need immediate access", saved.getReason(),
                "Must save with correct reason");
        assertEquals("Patient", saved.getResourceType(),
                "Must save with correct resource type");
        assertEquals("cpid-12345", saved.getResourceId(),
                "Must save with correct resource ID");
        assertEquals("PENDING_REVIEW", saved.getReviewStatus(),
                "Initial status must be PENDING_REVIEW");
        assertNotNull(saved.getGrantedAt(),
                "grantedAt must be set");
        assertNotNull(saved.getExpiresAt(),
                "expiresAt must be set");

        // Verify response mapping
        assertNotNull(response, "Response must not be null");
        assertEquals(ACTOR_ID, response.actorId(),
                "Response must reflect the actor");
        assertEquals("PENDING_REVIEW", response.reviewStatus(),
                "Response must have PENDING_REVIEW status");
    }

    @Test
    @DisplayName("createRequest: sets expiry based on configured TTL (60 minutes)")
    void createRequest_setsExpiryFromTtl() {
        BreakGlassCreateRequest request = new BreakGlassCreateRequest(
                TENANT_ID, ACTOR_ID, "Emergency access needed",
                null, null
        );

        Instant beforeCreate = Instant.now();

        when(breakGlassRepository.save(any(BreakGlassRequestEntity.class)))
                .thenAnswer(invocation -> {
                    BreakGlassRequestEntity entity = invocation.getArgument(0);
                    if (entity.getId() == null) entity.setId(UUID.randomUUID());
                    return entity;
                });

        breakGlassService.createRequest(request);

        ArgumentCaptor<BreakGlassRequestEntity> captor =
                ArgumentCaptor.forClass(BreakGlassRequestEntity.class);
        verify(breakGlassRepository).save(captor.capture());
        BreakGlassRequestEntity saved = captor.getValue();

        // expiresAt should be grantedAt + 60 minutes
        Instant expectedExpiry = saved.getGrantedAt().plusSeconds(60 * 60);
        assertEquals(expectedExpiry, saved.getExpiresAt(),
                "expiresAt must be grantedAt + breakGlassTtlMinutes");
    }

    // ════════════════════════════════════════════════════════════════════
    // hasActiveBreakGlass
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("hasActiveBreakGlass: returns true when active request exists")
    void hasActiveBreakGlass_withActiveRequest_returnsTrue() {
        BreakGlassRequestEntity entity = new BreakGlassRequestEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setExpiresAt(Instant.now().plusSeconds(3600));

        when(breakGlassRepository.findActiveByTenantIdAndActorId(
                eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(List.of(entity));

        assertTrue(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID),
                "Must return true when active break-glass request exists");
    }

    @Test
    @DisplayName("hasActiveBreakGlass: returns false when no active request exists")
    void hasActiveBreakGlass_withNoActiveRequest_returnsFalse() {
        when(breakGlassRepository.findActiveByTenantIdAndActorId(
                eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        assertFalse(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID),
                "Must return false when no active request exists");
    }

    @Test
    @DisplayName("hasActiveBreakGlass: expired requests are not returned (handled by query)")
    void hasActiveBreakGlass_expiredRequest_returnsFalse() {
        // The repository query filters on expiresAt > now, so expired entries
        // are excluded at the database level. We simulate this by returning empty.
        when(breakGlassRepository.findActiveByTenantIdAndActorId(
                eq(TENANT_ID), eq(ACTOR_ID), any(Instant.class)))
                .thenReturn(Collections.emptyList());

        assertFalse(breakGlassService.hasActiveBreakGlass(TENANT_ID, ACTOR_ID),
                "Expired break-glass requests must not count as active");
    }

    // ════════════════════════════════════════════════════════════════════
    // reviewRequest
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("reviewRequest: approve sets APPROVED status and reviewer info")
    void reviewRequest_approve_setsApprovedStatus() {
        UUID requestId = UUID.randomUUID();
        BreakGlassRequestEntity entity = new BreakGlassRequestEntity();
        entity.setId(requestId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setReason("Emergency");
        entity.setReviewStatus("PENDING_REVIEW");
        entity.setGrantedAt(Instant.now().minusSeconds(600));
        entity.setExpiresAt(Instant.now().plusSeconds(3000));

        when(breakGlassRepository.findByIdAndTenantId(requestId, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(breakGlassRepository.save(any(BreakGlassRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BreakGlassReviewRequest reviewRequest = new BreakGlassReviewRequest(
                TENANT_ID, REVIEWER_ID, true
        );

        BreakGlassResponse response = breakGlassService.reviewRequest(requestId, reviewRequest);

        assertEquals("APPROVED", response.reviewStatus(),
                "Approved request must have APPROVED status");
        assertTrue(response.approved(),
                "Approved response must have approved=true");
        assertEquals(REVIEWER_ID, response.reviewedBy(),
                "Response must record the reviewer");
        assertNotNull(response.reviewedAt(),
                "Response must have reviewedAt set");
    }

    @Test
    @DisplayName("reviewRequest: reject sets REJECTED status")
    void reviewRequest_reject_setsRejectedStatus() {
        UUID requestId = UUID.randomUUID();
        BreakGlassRequestEntity entity = new BreakGlassRequestEntity();
        entity.setId(requestId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setReason("Emergency");
        entity.setReviewStatus("PENDING_REVIEW");
        entity.setGrantedAt(Instant.now().minusSeconds(600));
        entity.setExpiresAt(Instant.now().plusSeconds(3000));

        when(breakGlassRepository.findByIdAndTenantId(requestId, TENANT_ID))
                .thenReturn(Optional.of(entity));
        when(breakGlassRepository.save(any(BreakGlassRequestEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BreakGlassReviewRequest reviewRequest = new BreakGlassReviewRequest(
                TENANT_ID, REVIEWER_ID, false
        );

        BreakGlassResponse response = breakGlassService.reviewRequest(requestId, reviewRequest);

        assertEquals("REJECTED", response.reviewStatus(),
                "Rejected request must have REJECTED status");
        assertFalse(response.approved(),
                "Rejected response must have approved=false");
    }

    @Test
    @DisplayName("reviewRequest: not found -> throws IllegalArgumentException")
    void reviewRequest_notFound_throwsException() {
        UUID requestId = UUID.randomUUID();
        when(breakGlassRepository.findByIdAndTenantId(requestId, TENANT_ID))
                .thenReturn(Optional.empty());

        BreakGlassReviewRequest reviewRequest = new BreakGlassReviewRequest(
                TENANT_ID, REVIEWER_ID, true
        );

        assertThrows(IllegalArgumentException.class,
                () -> breakGlassService.reviewRequest(requestId, reviewRequest),
                "Missing request must throw IllegalArgumentException");
    }

    @Test
    @DisplayName("reviewRequest: already reviewed -> throws IllegalStateException")
    void reviewRequest_alreadyReviewed_throwsException() {
        UUID requestId = UUID.randomUUID();
        BreakGlassRequestEntity entity = new BreakGlassRequestEntity();
        entity.setId(requestId);
        entity.setTenantId(TENANT_ID);
        entity.setActorId(ACTOR_ID);
        entity.setReason("Emergency");
        entity.setReviewStatus("APPROVED"); // Already reviewed
        entity.setGrantedAt(Instant.now().minusSeconds(600));
        entity.setExpiresAt(Instant.now().plusSeconds(3000));

        when(breakGlassRepository.findByIdAndTenantId(requestId, TENANT_ID))
                .thenReturn(Optional.of(entity));

        BreakGlassReviewRequest reviewRequest = new BreakGlassReviewRequest(
                TENANT_ID, REVIEWER_ID, true
        );

        assertThrows(IllegalStateException.class,
                () -> breakGlassService.reviewRequest(requestId, reviewRequest),
                "Already-reviewed request must throw IllegalStateException");
    }

    // ════════════════════════════════════════════════════════════════════
    // getPendingReviews
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("getPendingReviews: returns mapped responses for pending requests")
    void getPendingReviews_returnsMappedResponses() {
        BreakGlassRequestEntity e1 = new BreakGlassRequestEntity();
        e1.setId(UUID.randomUUID());
        e1.setTenantId(TENANT_ID);
        e1.setActorId("actor-1");
        e1.setReason("Reason 1");
        e1.setReviewStatus("PENDING_REVIEW");
        e1.setGrantedAt(Instant.now().minusSeconds(300));
        e1.setExpiresAt(Instant.now().plusSeconds(3300));

        BreakGlassRequestEntity e2 = new BreakGlassRequestEntity();
        e2.setId(UUID.randomUUID());
        e2.setTenantId(TENANT_ID);
        e2.setActorId("actor-2");
        e2.setReason("Reason 2");
        e2.setReviewStatus("PENDING_REVIEW");
        e2.setGrantedAt(Instant.now().minusSeconds(600));
        e2.setExpiresAt(Instant.now().plusSeconds(3000));

        when(breakGlassRepository.findByTenantIdAndReviewStatusOrderByGrantedAtDesc(
                TENANT_ID, "PENDING_REVIEW"))
                .thenReturn(List.of(e1, e2));

        List<BreakGlassResponse> results = breakGlassService.getPendingReviews(TENANT_ID);

        assertEquals(2, results.size(),
                "Must return all pending reviews");
        assertEquals("actor-1", results.get(0).actorId(),
                "First result must be actor-1");
        assertEquals("actor-2", results.get(1).actorId(),
                "Second result must be actor-2");
    }

    @Test
    @DisplayName("getPendingReviews: returns empty list when no pending reviews")
    void getPendingReviews_noPending_returnsEmptyList() {
        when(breakGlassRepository.findByTenantIdAndReviewStatusOrderByGrantedAtDesc(
                TENANT_ID, "PENDING_REVIEW"))
                .thenReturn(Collections.emptyList());

        List<BreakGlassResponse> results = breakGlassService.getPendingReviews(TENANT_ID);

        assertTrue(results.isEmpty(),
                "Must return empty list when no pending reviews");
    }
}
