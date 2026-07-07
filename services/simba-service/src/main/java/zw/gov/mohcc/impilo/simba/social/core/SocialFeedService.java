package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialGroupMembershipRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Composes the wellness-social feed: fetches a raw keyset page, then applies
 * {@link SocialVisibilityService} per row (so sensitive-category + visibility-level gating is enforced
 * at read time) and computes the next cursor over the RAW page so pagination stays stable even when
 * rows are filtered out.
 */
@Service
public class SocialFeedService {

    private final SocialPostService postService;
    private final SocialVisibilityService visibility;
    private final SocialGroupMembershipRepository groupMembershipRepository;

    public SocialFeedService(SocialPostService postService,
                             SocialVisibilityService visibility,
                             SocialGroupMembershipRepository groupMembershipRepository) {
        this.postService = postService;
        this.visibility = visibility;
        this.groupMembershipRepository = groupMembershipRepository;
    }

    public record FeedPage(List<SocialPostEntity> posts, Long nextCursor) { }

    @Transactional(readOnly = true)
    public FeedPage feed(UUID tenantId, String viewerCpid, String filter, UUID groupId,
                         Long cursor, int limit) {
        int capped = Math.max(1, Math.min(limit, 100));
        List<SocialPostEntity> raw = postService.feed(tenantId, filter, viewerCpid, groupId, cursor, limit);

        Long nextCursor = null;
        if (raw.size() > capped) {
            nextCursor = raw.get(capped - 1).getId();
            raw = raw.subList(0, capped);
        }

        Set<UUID> memberGroups = viewerCpid == null ? Set.of()
                : groupMembershipRepository
                    .findByTenantIdAndPersonCpidAndStatus(tenantId, viewerCpid, "ACTIVE")
                    .stream().map(m -> m.getGroupId()).collect(Collectors.toSet());

        List<SocialPostEntity> visible = new ArrayList<>();
        for (SocialPostEntity p : raw) {
            boolean isOwner = viewerCpid != null && viewerCpid.equals(p.getActorCpid());
            boolean isGroupMember = p.getGroupId() != null && memberGroups.contains(p.getGroupId());
            SocialVisibilityService.ViewerContext v = new SocialVisibilityService.ViewerContext(
                    tenantId, viewerCpid, isOwner, false, isGroupMember, false, false, false, false);
            if (visibility.canView(p, v)) {
                visible.add(p);
            }
        }
        return new FeedPage(visible, nextCursor);
    }
}
