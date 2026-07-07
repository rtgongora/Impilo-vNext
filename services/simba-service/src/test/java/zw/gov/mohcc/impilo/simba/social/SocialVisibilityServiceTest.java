package zw.gov.mohcc.impilo.simba.social;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.simba.core.ConsentPreferenceService;
import zw.gov.mohcc.impilo.simba.social.core.SocialVisibilityService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Visibility ladder + sensitive-category deny-without-consent. */
class SocialVisibilityServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final String OWNER = "owner-cpid";
    private static final String VIEWER = "viewer-cpid";

    private SocialPostEntity post(String visibility, String sensitive, String moderation) {
        SocialPostEntity p = new SocialPostEntity();
        p.setTenantId(TENANT);
        p.setActorCpid(OWNER);
        p.setVisibility(visibility);
        p.setSensitiveCategory(sensitive);
        p.setModerationStatus(moderation == null ? "VISIBLE" : moderation);
        return p;
    }

    private SocialVisibilityService.ViewerContext viewer(String cpid, boolean owner, boolean follower,
                                                         boolean group, boolean moderator) {
        return new SocialVisibilityService.ViewerContext(TENANT, cpid, owner, follower, group,
                false, false, false, moderator);
    }

    @Test
    void publicPost_visibleToAnyone() {
        var svc = new SocialVisibilityService(mock(ConsentPreferenceService.class));
        assertThat(svc.canView(post("PUBLIC_WITHIN_IMPILO", null, null),
                viewer(VIEWER, false, false, false, false))).isTrue();
    }

    @Test
    void privatePost_onlyOwner() {
        var svc = new SocialVisibilityService(mock(ConsentPreferenceService.class));
        assertThat(svc.canView(post("PRIVATE", null, null),
                viewer(VIEWER, false, false, false, false))).isFalse();
        assertThat(svc.canView(post("PRIVATE", null, null),
                viewer(OWNER, true, false, false, false))).isTrue();
    }

    @Test
    void followerPost_visibleToFollowerOnly() {
        var svc = new SocialVisibilityService(mock(ConsentPreferenceService.class));
        assertThat(svc.canView(post("FOLLOWER", null, null),
                viewer(VIEWER, false, true, false, false))).isTrue();
        assertThat(svc.canView(post("FOLLOWER", null, null),
                viewer(VIEWER, false, false, false, false))).isFalse();
    }

    @Test
    void sensitivePost_deniedWithoutOwnerConsent() {
        var consent = mock(ConsentPreferenceService.class);
        when(consent.consentedSensitiveCategories(eq(TENANT), eq(OWNER))).thenReturn(Set.of());
        var svc = new SocialVisibilityService(consent);
        // Public visibility but MENTAL category, owner has NOT consented → denied for non-owner.
        assertThat(svc.canView(post("PUBLIC_WITHIN_IMPILO", "MENTAL", null),
                viewer(VIEWER, false, true, false, false))).isFalse();
    }

    @Test
    void sensitivePost_allowedWhenOwnerConsented() {
        var consent = mock(ConsentPreferenceService.class);
        when(consent.consentedSensitiveCategories(eq(TENANT), eq(OWNER))).thenReturn(Set.of("MENTAL"));
        var svc = new SocialVisibilityService(consent);
        assertThat(svc.canView(post("PUBLIC_WITHIN_IMPILO", "MENTAL", null),
                viewer(VIEWER, false, false, false, false))).isTrue();
    }

    @Test
    void sensitivePost_alwaysVisibleToOwner() {
        var consent = mock(ConsentPreferenceService.class);
        var svc = new SocialVisibilityService(consent);
        assertThat(svc.canView(post("PRIVATE", "SRH", null),
                viewer(OWNER, true, false, false, false))).isTrue();
    }

    @Test
    void hiddenContent_onlyOwnerOrModerator() {
        var svc = new SocialVisibilityService(mock(ConsentPreferenceService.class));
        assertThat(svc.canView(post("PUBLIC_WITHIN_IMPILO", null, "HIDDEN"),
                viewer(VIEWER, false, false, false, false))).isFalse();
        assertThat(svc.canView(post("PUBLIC_WITHIN_IMPILO", null, "HIDDEN"),
                viewer(VIEWER, false, false, false, true))).isTrue();
    }

    @Test
    void groupPost_visibleToGroupMember() {
        var svc = new SocialVisibilityService(mock(ConsentPreferenceService.class));
        assertThat(svc.canView(post("GROUP", null, null),
                viewer(VIEWER, false, false, true, false))).isTrue();
        assertThat(svc.canView(post("GROUP", null, null),
                viewer(VIEWER, false, false, false, false))).isFalse();
    }
}
