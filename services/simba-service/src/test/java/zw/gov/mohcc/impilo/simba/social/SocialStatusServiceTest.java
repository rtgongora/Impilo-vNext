package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import zw.gov.mohcc.impilo.simba.core.ConsentPreferenceService;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.core.SocialStatusService;
import zw.gov.mohcc.impilo.simba.social.core.SocialVisibilityService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialStatusEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialStatusRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Create → emit/audit, active-status visibility filtering, owner-only delete, and the expiry sweep. */
class SocialStatusServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000009");
    private static final String OWNER = "owner-cpid";
    private static final String VIEWER = "viewer-cpid";

    private SocialStatusRepository repo;
    private SimbaEventEmitter events;
    private WellnessAuditService audit;
    private SocialStatusService service;

    @BeforeEach
    void setUp() {
        repo = mock(SocialStatusRepository.class);
        events = mock(SimbaEventEmitter.class);
        audit = mock(WellnessAuditService.class);
        // Use a real visibility service (only its ladder matters; statuses carry no sensitive category).
        SocialVisibilityService visibility =
                new SocialVisibilityService(mock(ConsentPreferenceService.class));
        service = new SocialStatusService(repo, visibility, events, audit);
        when(repo.save(any())).thenAnswer(inv -> {
            SocialStatusEntity e = inv.getArgument(0);
            if (e.getStatusId() == null) {
                e.setStatusId(UUID.randomUUID());
            }
            if (e.getCreatedAt() == null) {
                e.setCreatedAt(OffsetDateTime.now());
            }
            return e;
        });
    }

    private SocialStatusEntity status(String cpid, String visibility) {
        SocialStatusEntity s = new SocialStatusEntity();
        s.setStatusId(UUID.randomUUID());
        s.setTenantId(TENANT);
        s.setActorCpid(cpid);
        s.setText("Feeling good today");
        s.setVisibility(visibility);
        s.setModerationStatus("VISIBLE");
        s.setExpiresAt(OffsetDateTime.now().plusHours(6));
        return s;
    }

    @Test
    void create_persistsWithExpiry_emitsAndAudits() {
        var in = new SocialStatusService.CreateStatus(OWNER, "CLIENT", "Went for a walk", "HAPPY",
                "PUBLIC_WITHIN_IMPILO", 12);
        SocialStatusEntity s = service.create(TENANT, in, "corr-1", "req-1");

        assertThat(s.getExpiresAt()).isAfter(OffsetDateTime.now().plusHours(11));
        assertThat(s.getVisibility()).isEqualTo("PUBLIC_WITHIN_IMPILO");
        verify(events).emit(eq(TENANT), eq("WELLNESS_SOCIAL_STATUS"), anyString(),
                eq("impilo.simba.wellness.social.status.created.v1"), eq(OWNER), anyString(), any());
        verify(audit).recordSelf(eq(TENANT), eq(OWNER), eq("CLIENT"), eq(OWNER),
                eq("wellness.social.status.create"), eq("corr-1"), eq("req-1"));
    }

    @Test
    void create_rejectsBlankText() {
        var in = new SocialStatusService.CreateStatus(OWNER, "CLIENT", "  ", null, "PRIVATE", null);
        assertThatThrownBy(() -> service.create(TENANT, in, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listActive_hidesPrivateStatusesFromNonOwner() {
        when(repo.findActive(eq(TENANT), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(
                        status("other-1", "PUBLIC_WITHIN_IMPILO"),
                        status("other-2", "PRIVATE"),
                        status(VIEWER, "PRIVATE")));

        List<SocialStatusEntity> visible = service.listActive(TENANT, VIEWER, 50);

        // The viewer sees the public one + their own private, but NOT the stranger's private.
        assertThat(visible).extracting(SocialStatusEntity::getActorCpid)
                .containsExactlyInAnyOrder("other-1", VIEWER);
    }

    @Test
    void delete_onlyAuthorMayRemove() {
        SocialStatusEntity s = status(OWNER, "PUBLIC_WITHIN_IMPILO");
        when(repo.findByStatusIdAndTenantId(s.getStatusId(), TENANT))
                .thenReturn(java.util.Optional.of(s));

        assertThatThrownBy(() -> service.delete(TENANT, s.getStatusId(), "someone-else", null, null))
                .isInstanceOf(SecurityException.class);

        SocialStatusEntity hidden = service.delete(TENANT, s.getStatusId(), OWNER, "c", "r");
        assertThat(hidden.getModerationStatus()).isEqualTo("HIDDEN");
    }

    @Test
    void expireOverdue_flipsExpiredStatusesToHidden() {
        SocialStatusEntity a = status(OWNER, "PUBLIC_WITHIN_IMPILO");
        SocialStatusEntity b = status("other", "PUBLIC_WITHIN_IMPILO");
        when(repo.findExpiredVisible(any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(a, b));

        int hidden = service.expireOverdue(200);

        assertThat(hidden).isEqualTo(2);
        assertThat(a.getModerationStatus()).isEqualTo("HIDDEN");
        assertThat(b.getModerationStatus()).isEqualTo("HIDDEN");
        verify(repo).saveAll(any());
    }

    @Test
    void listActive_emptyWhenNoActiveStatuses() {
        when(repo.findActive(eq(TENANT), any(OffsetDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());
        assertThat(service.listActive(TENANT, VIEWER, 50)).isEmpty();
        verify(repo, never()).save(any());
    }

    @Test
    void create_defaultsTo24hWhenNoTtl() {
        var in = new SocialStatusService.CreateStatus(OWNER, null, "Resting", null, null, null);
        SocialStatusEntity s = service.create(TENANT, in, null, null);
        assertThat(s.getExpiresAt()).isAfter(OffsetDateTime.now().plusHours(23));
        assertThat(s.getVisibility()).isEqualTo("PRIVATE");
        verify(repo, times(1)).save(any());
    }
}
