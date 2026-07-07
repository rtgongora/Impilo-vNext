package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.social.entity.SocialBookmarkEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommentEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialReactionEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialBookmarkRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialCommentRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialPostRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialReactionRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Comments, reactions, bookmarks on wellness-social content. Each write updates counters + emits. */
@Service
public class SocialInteractionService {

    private static final Set<String> VALID_REACTIONS =
            Set.of("ENCOURAGE", "LIKE", "CELEBRATE", "HELPFUL", "SUPPORT", "THANK_YOU");

    private final SocialCommentRepository commentRepository;
    private final SocialReactionRepository reactionRepository;
    private final SocialBookmarkRepository bookmarkRepository;
    private final SocialPostRepository postRepository;
    private final SimbaEventEmitter events;

    public SocialInteractionService(SocialCommentRepository commentRepository,
                                    SocialReactionRepository reactionRepository,
                                    SocialBookmarkRepository bookmarkRepository,
                                    SocialPostRepository postRepository,
                                    SimbaEventEmitter events) {
        this.commentRepository = commentRepository;
        this.reactionRepository = reactionRepository;
        this.bookmarkRepository = bookmarkRepository;
        this.postRepository = postRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<SocialCommentEntity> comments(UUID postId) {
        return commentRepository.findByPostIdAndModerationStatusOrderByIdAsc(postId, "VISIBLE");
    }

    @Transactional
    public SocialCommentEntity comment(UUID tenantId, UUID postId, String actorCpid, String actorType,
                                       String body) {
        SocialPostEntity post = postRepository.findByPostIdAndTenantId(postId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        MilestoneSanitiser.requireSafe(body);

        SocialCommentEntity c = new SocialCommentEntity();
        c.setTenantId(tenantId);
        c.setPostId(postId);
        c.setActorCpid(actorCpid);
        if (actorType != null) c.setActorType(actorType);
        c.setBody(body);
        c = commentRepository.save(c);

        post.setCommentCount((int) commentRepository.countByPostIdAndModerationStatus(postId, "VISIBLE"));
        postRepository.save(post);

        emit(tenantId, "impilo.simba.wellness.social.comment.created.v1", actorCpid,
                "COMMENT", c.getCommentId(), Map.of("post_id", postId.toString()));
        return c;
    }

    /** Toggles a reaction. Same reaction again removes it; a different reaction replaces it. */
    @Transactional
    public Map<String, Object> react(UUID tenantId, String subjectType, UUID subjectId,
                                     String actorCpid, String reaction) {
        String r = reaction == null ? "" : reaction.toUpperCase();
        if (!VALID_REACTIONS.contains(r)) {
            throw new IllegalArgumentException("Invalid reaction: " + reaction);
        }
        var existing = reactionRepository.findBySubjectTypeAndSubjectIdAndActorCpid(subjectType, subjectId, actorCpid);
        boolean active;
        if (existing.isPresent() && existing.get().getReaction().equals(r)) {
            reactionRepository.delete(existing.get());
            active = false;
        } else {
            SocialReactionEntity re = existing.orElseGet(SocialReactionEntity::new);
            re.setTenantId(tenantId);
            re.setSubjectType(subjectType);
            re.setSubjectId(subjectId);
            re.setActorCpid(actorCpid);
            re.setReaction(r);
            reactionRepository.save(re);
            active = true;
        }
        long count = reactionRepository.countBySubjectTypeAndSubjectId(subjectType, subjectId);
        if ("POST".equals(subjectType)) {
            postRepository.findByPostIdAndTenantId(subjectId, tenantId).ifPresent(p -> {
                p.setReactionCount((int) count);
                postRepository.save(p);
            });
        }
        emit(tenantId, "impilo.simba.wellness.social.reaction.v1", actorCpid, subjectType, subjectId,
                Map.of("reaction", r, "active", active));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("active", active);
        out.put("reaction", r);
        out.put("count", count);
        return out;
    }

    @Transactional
    public Map<String, Object> bookmark(UUID tenantId, String subjectType, UUID subjectId, String actorCpid) {
        var existing = bookmarkRepository.findByActorCpidAndSubjectTypeAndSubjectId(actorCpid, subjectType, subjectId);
        boolean saved;
        if (existing.isPresent()) {
            bookmarkRepository.delete(existing.get());
            saved = false;
        } else {
            SocialBookmarkEntity b = new SocialBookmarkEntity();
            b.setTenantId(tenantId);
            b.setActorCpid(actorCpid);
            b.setSubjectType(subjectType);
            b.setSubjectId(subjectId);
            bookmarkRepository.save(b);
            saved = true;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", saved);
        return out;
    }

    @Transactional(readOnly = true)
    public List<SocialBookmarkEntity> savedFor(UUID tenantId, String actorCpid) {
        return bookmarkRepository.findByTenantIdAndActorCpidOrderByIdDesc(tenantId, actorCpid);
    }

    private void emit(UUID tenantId, String eventType, String actorCpid, String subjectType,
                      UUID subjectId, Map<String, Object> extra) {
        Map<String, Object> payload = new LinkedHashMap<>(extra);
        payload.put("subject_type", subjectType);
        payload.put("subject_id", subjectId.toString());
        payload.put("actor_cpid", actorCpid);
        events.emit(tenantId, "WELLNESS_SOCIAL_INTERACTION", subjectId.toString(),
                eventType, actorCpid, "simba:social-int:" + subjectType + ":" + subjectId + ":"
                        + eventType + ":" + actorCpid + ":" + System.nanoTime(), payload);
    }
}
