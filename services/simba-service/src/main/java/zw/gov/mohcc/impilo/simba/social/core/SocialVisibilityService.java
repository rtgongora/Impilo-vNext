package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.simba.core.ConsentPreferenceService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;

import java.util.Set;
import java.util.UUID;

/**
 * Central read guard for the wellness-social layer. Every read path goes through {@link #canView}.
 * Enforces the visibility ladder (PRIVATE → PUBLIC_WITHIN_IMPILO) and sensitive-category gating that
 * calls the Part-1 {@link ConsentPreferenceService}: a post tagged with a sensitive category is only
 * visible to a non-owner if the OWNER has consented to sharing that category.
 */
@Service
public class SocialVisibilityService {

    private final ConsentPreferenceService consentService;

    public SocialVisibilityService(ConsentPreferenceService consentService) {
        this.consentService = consentService;
    }

    /** A viewer's relationship to a piece of content, resolved by the caller from membership/follow. */
    public record ViewerContext(
            UUID tenantId,
            String viewerCpid,
            boolean isOwner,
            boolean isFollower,
            boolean isGroupMember,
            boolean isCommunityMember,
            boolean isCareTeam,
            boolean isProgrammeStaff,
            boolean isModerator) {
    }

    /** True when the viewer may see the post, applying visibility level + sensitive-category consent. */
    public boolean canView(SocialPostEntity post, ViewerContext v) {
        // Hidden/removed content is only visible to owner + moderator.
        if (!"VISIBLE".equals(post.getModerationStatus())) {
            return v.isOwner() || v.isModerator();
        }
        // Owner + moderator always see their own / all content.
        if (v.isOwner() || v.isModerator()) {
            return true;
        }
        // Sensitive-category gating: consult the OWNER's consent.
        if (post.getSensitiveCategory() != null && !post.getSensitiveCategory().isBlank()) {
            Set<String> consented = consentService.consentedSensitiveCategories(
                    v.tenantId(), post.getActorCpid());
            if (!consented.contains(post.getSensitiveCategory().toUpperCase())) {
                return false;
            }
        }
        return switch (post.getVisibility() == null ? "PRIVATE" : post.getVisibility().toUpperCase()) {
            case "PRIVATE" -> false;
            case "CAREGIVER", "CARE_TEAM" -> v.isCareTeam();
            case "GROUP" -> v.isGroupMember();
            case "COMMUNITY" -> v.isCommunityMember();
            case "PROGRAMME" -> v.isProgrammeStaff() || v.isCommunityMember();
            case "FACILITY" -> v.isProgrammeStaff();
            case "FOLLOWER" -> v.isFollower();
            case "PUBLIC_WITHIN_IMPILO", "ANONYMOUS", "PUBLIC" -> true;
            default -> false;
        };
    }
}
