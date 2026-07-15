package zw.gov.mohcc.impilo.simba.social.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialPostRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wellness-social posts. Creation sanitises milestone text (never emits clinical values), persists the
 * post with its actor block + visibility, emits an outbox event, and audits. Reads are keyset-paginated
 * and filtered by {@link SocialVisibilityService} at the controller/feed layer.
 */
@Service
public class SocialPostService {

    private final SocialPostRepository repository;
    private final SimbaEventEmitter events;
    private final WellnessAuditService audit;
    private final ObjectMapper objectMapper;

    public SocialPostService(SocialPostRepository repository,
                             SimbaEventEmitter events,
                             WellnessAuditService audit,
                             ObjectMapper objectMapper) {
        this.repository = repository;
        this.events = events;
        this.audit = audit;
        this.objectMapper = objectMapper;
    }

    public record CreatePost(String actorCpid, String actorType, String providerId, UUID groupId,
                             UUID communityId, UUID programmeId, String body, List<String> mediaObjectIds,
                             String sensitiveCategory, String milestoneRef, String visibility) { }

    @Transactional
    public SocialPostEntity create(UUID tenantId, CreatePost in, String correlationId, String requestId) {
        // Milestone / body must not carry clinical values.
        MilestoneSanitiser.requireSafe(in.body());
        if (in.milestoneRef() != null) {
            MilestoneSanitiser.requireSafe(in.milestoneRef());
        }

        SocialPostEntity p = new SocialPostEntity();
        p.setTenantId(tenantId);
        p.setActorCpid(in.actorCpid());
        if (in.actorType() != null) p.setActorType(in.actorType());
        p.setProviderId(in.providerId());
        p.setGroupId(in.groupId());
        p.setCommunityId(in.communityId());
        p.setProgrammeId(in.programmeId());
        p.setBody(in.body());
        p.setSensitiveCategory(in.sensitiveCategory());
        p.setMilestoneRef(in.milestoneRef());
        if (in.visibility() != null && !in.visibility().isBlank()) {
            p.setVisibility(in.visibility());
        }
        if (in.mediaObjectIds() != null && !in.mediaObjectIds().isEmpty()) {
            try {
                p.setMediaObjectIds(objectMapper.writeValueAsString(in.mediaObjectIds()));
            } catch (Exception ignored) {
                p.setMediaObjectIds(null);
            }
        }
        p = repository.save(p);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("post_id", p.getPostId().toString());
        payload.put("actor_cpid", p.getActorCpid());
        payload.put("visibility", p.getVisibility());
        payload.put("group_id", p.getGroupId() == null ? null : p.getGroupId().toString());
        events.emit(tenantId, "WELLNESS_SOCIAL_POST", p.getPostId().toString(),
                "impilo.simba.wellness.social.post.created.v1", p.getActorCpid(),
                "simba:social-post:" + p.getPostId(), payload);

        audit.recordSelf(tenantId, in.actorCpid(), in.actorType(), in.actorCpid(),
                "wellness.social.post.create", correlationId, requestId);
        return p;
    }

    @Transactional(readOnly = true)
    public SocialPostEntity get(UUID tenantId, UUID postId) {
        return repository.findByPostIdAndTenantId(postId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
    }

    /** Raw feed page (visibility filtering is applied by the caller). Fetches limit+1 for cursoring. */
    @Transactional(readOnly = true)
    public List<SocialPostEntity> feed(UUID tenantId, String filter, String cpid, UUID groupId,
                                       Long cursor, int limit) {
        PageRequest req = PageRequest.of(0, Math.max(1, Math.min(limit, 100)) + 1);
        return switch (filter == null ? "ALL" : filter.toUpperCase()) {
            case "MY_ACTIVITY" -> repository.myActivity(tenantId, cpid, cursor, req);
            case "GROUP" -> repository.byGroup(tenantId, groupId, cursor, req);
            default -> repository.feed(tenantId, cursor, req);
        };
    }

    @Transactional
    public SocialPostEntity setModerationStatus(UUID tenantId, UUID postId, String status) {
        SocialPostEntity p = get(tenantId, postId);
        p.setModerationStatus(status);
        return repository.save(p);
    }

    @Transactional
    public void save(SocialPostEntity post) {
        repository.save(post);
    }
}
