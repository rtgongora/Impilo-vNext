package zw.gov.mohcc.impilo.community.social.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.community.social.api.dto.SocialDtos;
import zw.gov.mohcc.impilo.community.social.persistence.entity.*;
import zw.gov.mohcc.impilo.community.social.persistence.repository.*;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application service for the social subdomain.
 *
 * Visibility is enforced in the read paths via the repository-level filters
 * (see {@code SocialPostRepository#findHomeFeedForActor}) and in this class
 * for scope-specific lookups. Write operations check authorship/role.
 *
 * Outbox events are emitted through {@link SocialEventEmitter}; the existing
 * {@code CommunityOutboxPublisher} relays them to Kafka.
 */
@Service
public class SocialService {

    private static final String TARGET_POST = "POST";
    private static final String TARGET_COMMENT = "COMMENT";

    private final SocialPostRepository postRepo;
    private final SocialCommentRepository commentRepo;
    private final SocialReactionRepository reactionRepo;
    private final SocialBookmarkRepository bookmarkRepo;
    private final SocialCommunityRepository communityRepo;
    private final SocialCommunityMembershipRepository communityMembershipRepo;
    private final SocialGroupRepository groupRepo;
    private final SocialGroupMembershipRepository groupMembershipRepo;
    private final SocialPageRepository pageRepo;
    private final SocialPageFollowerRepository pageFollowerRepo;
    private final SocialModerationCaseRepository moderationRepo;
    private final SocialEventEmitter events;
    private final ObjectMapper json;

    public SocialService(SocialPostRepository postRepo,
                         SocialCommentRepository commentRepo,
                         SocialReactionRepository reactionRepo,
                         SocialBookmarkRepository bookmarkRepo,
                         SocialCommunityRepository communityRepo,
                         SocialCommunityMembershipRepository communityMembershipRepo,
                         SocialGroupRepository groupRepo,
                         SocialGroupMembershipRepository groupMembershipRepo,
                         SocialPageRepository pageRepo,
                         SocialPageFollowerRepository pageFollowerRepo,
                         SocialModerationCaseRepository moderationRepo,
                         SocialEventEmitter events,
                         ObjectMapper json) {
        this.postRepo = postRepo;
        this.commentRepo = commentRepo;
        this.reactionRepo = reactionRepo;
        this.bookmarkRepo = bookmarkRepo;
        this.communityRepo = communityRepo;
        this.communityMembershipRepo = communityMembershipRepo;
        this.groupRepo = groupRepo;
        this.groupMembershipRepo = groupMembershipRepo;
        this.pageRepo = pageRepo;
        this.pageFollowerRepo = pageFollowerRepo;
        this.moderationRepo = moderationRepo;
        this.events = events;
        this.json = json;
    }

    // ── Feed ────────────────────────────────────────────────────────────────
    public SocialDtos.FeedPageDto feed(UUID tenantId, String actorId, String scope,
                                       String scopeId, int limit, int offset) {
        String s = scope == null ? "home" : scope.toLowerCase(Locale.ROOT);
        Pageable page = PageRequest.of(Math.max(0, offset / Math.max(1, limit)), Math.max(1, Math.min(limit, 100)));
        List<SocialPostEntity> rows;
        switch (s) {
            case "community" -> {
                if (scopeId == null) throw badRequest("scopeId required for community feed");
                rows = postRepo.findByTenantIdAndCommunityIdAndStatusOrderByPublishedAtDesc(
                        tenantId, UUID.fromString(scopeId), "published", page).getContent();
            }
            case "group" -> {
                if (scopeId == null) throw badRequest("scopeId required for group feed");
                rows = postRepo.findByTenantIdAndGroupIdAndStatusOrderByPublishedAtDesc(
                        tenantId, UUID.fromString(scopeId), "published", page).getContent();
            }
            case "page", "facility" -> {
                if (scopeId == null) throw badRequest("scopeId required for page feed");
                rows = postRepo.findByTenantIdAndPageIdAndStatusOrderByPublishedAtDesc(
                        tenantId, UUID.fromString(scopeId), "published", page).getContent();
            }
            case "my_posts" -> rows = postRepo
                    .findByTenantIdAndAuthorActorIdOrderByCreatedAtDesc(tenantId, actorId, page).getContent();
            case "drafts" -> rows = postRepo
                    .findByTenantIdAndAuthorActorIdOrderByCreatedAtDesc(tenantId, actorId, page).getContent()
                    .stream().filter(p -> "draft".equals(p.getStatus())).toList();
            case "saved", "bookmarks" -> rows = postRepo
                    .findBookmarkedByActor(tenantId, actorId == null ? "" : actorId, page).getContent();
            case "announcements" -> rows = postRepo.findAnnouncements(tenantId, page).getContent();
            case "public_health" -> rows = postRepo.findAnnouncements(tenantId, page).getContent();
            case "moderation" -> rows = postRepo
                    .findByTenantIdAndStatusOrderByPublishedAtDesc(tenantId, "removed", page).getContent();
            case "home", "following", "wellness", "learning" -> rows = actorId == null || actorId.isBlank()
                    ? postRepo.findByTenantIdAndStatusOrderByPublishedAtDesc(tenantId, "published", page).getContent()
                    : postRepo.findHomeFeedForActor(tenantId, actorId, page).getContent();
            default -> rows = postRepo
                    .findByTenantIdAndStatusOrderByPublishedAtDesc(tenantId, "published", page).getContent();
        }

        List<SocialDtos.PostDto> items = rows.stream()
                .map(p -> toPostDto(p, actorId))
                .collect(Collectors.toList());
        String nextCursor = rows.size() == limit
                ? String.valueOf(offset + limit)
                : null;
        return new SocialDtos.FeedPageDto(s, items, nextCursor);
    }

    // ── Post CRUD ──────────────────────────────────────────────────────────
    @Transactional
    public SocialDtos.PostDto createPost(UUID tenantId, String actorId, String actorDisplay,
                                         String actorRoleBadge, SocialDtos.CreatePostRequest req) {
        SocialPostEntity p = new SocialPostEntity();
        p.setTenantId(tenantId);
        p.setAuthorActorId(actorId == null ? "system:anonymous" : actorId);
        p.setAuthorDisplay(actorDisplay);
        p.setAuthorRoleBadge(actorRoleBadge);
        p.setKind(req.kind() == null ? "text" : req.kind());
        p.setTitle(req.title());
        p.setBody(req.body() == null ? "" : req.body());
        p.setVisibility(req.visibility() == null ? "public" : req.visibility());
        p.setTopicsJson(SocialJson.writeJson(json, req.topics() == null ? List.of() : req.topics()));
        p.setAttachmentsJson(SocialJson.writeJson(json, req.attachments() == null ? List.of() : req.attachments()));
        if (req.scope() != null) {
            p.setCommunityId(req.scope().communityId());
            p.setGroupId(req.scope().groupId());
            p.setPageId(req.scope().pageId());
            p.setFacilityId(req.scope().facilityId());
        }
        boolean isDraft = Boolean.TRUE.equals(req.saveAsDraft());
        boolean isScheduled = req.scheduledFor() != null && req.scheduledFor().isAfter(OffsetDateTime.now());
        if (isDraft) {
            p.setStatus("draft");
        } else if (isScheduled) {
            p.setStatus("scheduled");
            p.setScheduledFor(req.scheduledFor());
        } else {
            p.setStatus("published");
            p.setPublishedAt(OffsetDateTime.now());
        }
        p.setSource(req.source() == null ? "manual" : req.source());
        if (req.nompiloAssist() != null) {
            p.setNompiloFlagsJson(SocialJson.writeJson(json, req.nompiloAssist()));
        }
        SocialPostEntity saved = postRepo.save(p);

        if (!isDraft) {
            events.emit(tenantId,
                    isScheduled ? "post.scheduled" : "post.published",
                    "social-post", saved.getId().toString(),
                    Map.of(
                            "postId", saved.getId().toString(),
                            "authorActorId", saved.getAuthorActorId(),
                            "kind", saved.getKind(),
                            "visibility", saved.getVisibility(),
                            "communityId", String.valueOf(saved.getCommunityId()),
                            "groupId", String.valueOf(saved.getGroupId()),
                            "pageId", String.valueOf(saved.getPageId())
                    ));
        } else {
            events.emit(tenantId, "post.created", "social-post", saved.getId().toString(),
                    Map.of("postId", saved.getId().toString(), "status", "draft"));
        }
        return toPostDto(saved, actorId);
    }

    @Transactional(readOnly = true)
    public SocialDtos.PostDto getPost(UUID tenantId, String actorId, UUID id) {
        SocialPostEntity p = postRepo.findById(id)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        return toPostDto(p, actorId);
    }

    @Transactional
    public SocialDtos.PostDto updatePost(UUID tenantId, String actorId, UUID id, SocialDtos.UpdatePostRequest req) {
        SocialPostEntity p = postRepo.findById(id)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        if (!Objects.equals(actorId, p.getAuthorActorId())) {
            throw forbidden("only the author can update a post");
        }
        if (req.title() != null) p.setTitle(req.title());
        if (req.body() != null) p.setBody(req.body());
        if (req.visibility() != null) p.setVisibility(req.visibility());
        if (req.topics() != null) p.setTopicsJson(SocialJson.writeJson(json, req.topics()));
        if (req.attachments() != null) p.setAttachmentsJson(SocialJson.writeJson(json, req.attachments()));
        SocialPostEntity saved = postRepo.save(p);
        events.emit(tenantId, "post.updated", "social-post", saved.getId().toString(),
                Map.of("postId", saved.getId().toString()));
        return toPostDto(saved, actorId);
    }

    @Transactional
    public void archivePost(UUID tenantId, String actorId, UUID id) {
        SocialPostEntity p = postRepo.findById(id)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        if (!Objects.equals(actorId, p.getAuthorActorId())) {
            throw forbidden("only the author can archive a post");
        }
        p.setStatus("archived");
        postRepo.save(p);
        events.emit(tenantId, "post.archived", "social-post", id.toString(),
                Map.of("postId", id.toString()));
    }

    @Transactional
    public SocialDtos.PostDto publishDraft(UUID tenantId, String actorId, UUID id) {
        SocialPostEntity p = postRepo.findById(id)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        if (!Objects.equals(actorId, p.getAuthorActorId())) {
            throw forbidden("only the author can publish their draft");
        }
        p.setStatus("published");
        p.setPublishedAt(OffsetDateTime.now());
        postRepo.save(p);
        events.emit(tenantId, "post.published", "social-post", id.toString(),
                Map.of("postId", id.toString()));
        return toPostDto(p, actorId);
    }

    @Transactional
    public void pin(UUID tenantId, String actorId, UUID id, boolean pinned) {
        SocialPostEntity p = postRepo.findById(id)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        // TODO(role-check): integrate with TSHEPO authz to gate moderator/admin pin
        p.setPinned(pinned);
        postRepo.save(p);
        events.emit(tenantId, "post.pinned", "social-post", id.toString(),
                Map.of("postId", id.toString(), "pinned", pinned));
    }

    // ── Reactions ──────────────────────────────────────────────────────────
    @Transactional
    public SocialDtos.ReactionSummaryDto react(UUID tenantId, String actorId, UUID postId, String type) {
        SocialPostEntity p = postRepo.findById(postId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        SocialReactionEntity existing = reactionRepo
                .findFirstByTargetTypeAndTargetIdAndActorId(TARGET_POST, postId, actorId)
                .orElse(null);
        if (existing == null) {
            existing = new SocialReactionEntity();
            existing.setTenantId(tenantId);
            existing.setTargetType(TARGET_POST);
            existing.setTargetId(postId);
            existing.setActorId(actorId);
            existing.setReactionType(type == null ? "LIKE" : type);
            reactionRepo.save(existing);
        } else {
            existing.setReactionType(type == null ? "LIKE" : type);
            reactionRepo.save(existing);
        }
        SocialDtos.ReactionSummaryDto summary = recountReactions(p, actorId);
        events.emit(tenantId, "reaction.created", "social-post", postId.toString(),
                Map.of("postId", postId.toString(), "actorId", actorId, "type", existing.getReactionType()));
        return summary;
    }

    @Transactional
    public SocialDtos.ReactionSummaryDto unreact(UUID tenantId, String actorId, UUID postId) {
        SocialPostEntity p = postRepo.findById(postId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        reactionRepo.findFirstByTargetTypeAndTargetIdAndActorId(TARGET_POST, postId, actorId)
                .ifPresent(reactionRepo::delete);
        return recountReactions(p, actorId);
    }

    private SocialDtos.ReactionSummaryDto recountReactions(SocialPostEntity p, String actorId) {
        List<SocialReactionEntity> rows = reactionRepo.findByTargetTypeAndTargetId(TARGET_POST, p.getId());
        int total = rows.size();
        Map<String, Integer> byType = new LinkedHashMap<>();
        String viewer = null;
        for (SocialReactionEntity r : rows) {
            byType.merge(r.getReactionType(), 1, Integer::sum);
            if (actorId != null && actorId.equals(r.getActorId())) viewer = r.getReactionType();
        }
        Map<String, Object> summaryMap = new LinkedHashMap<>();
        summaryMap.put("total", total);
        summaryMap.put("byType", byType);
        p.setReactionCount(total);
        p.setReactionSummaryJson(SocialJson.writeJson(json, summaryMap));
        postRepo.save(p);
        return new SocialDtos.ReactionSummaryDto(total, byType, viewer);
    }

    // ── Comments ───────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SocialDtos.CommentDto> listComments(UUID tenantId, UUID postId) {
        SocialPostEntity p = postRepo.findById(postId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        return commentRepo.findByPostIdOrderByCreatedAtAsc(p.getId()).stream()
                .map(c -> new SocialDtos.CommentDto(
                        c.getId(), c.getPostId(), c.getBody(),
                        identityFor(c.getAuthorActorId(), c.getAuthorDisplay(), null, null, false, null),
                        c.getParentCommentId(), c.getCreatedAt(),
                        new SocialDtos.ReactionSummaryDto(c.getReactionCount(), Map.of(), null)
                )).toList();
    }

    @Transactional
    public SocialDtos.CommentDto addComment(UUID tenantId, String actorId, String display,
                                            UUID postId, SocialDtos.CommentRequest req) {
        SocialPostEntity p = postRepo.findById(postId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        SocialCommentEntity c = new SocialCommentEntity();
        c.setTenantId(tenantId);
        c.setPostId(p.getId());
        c.setParentCommentId(req.parentCommentId());
        c.setAuthorActorId(actorId);
        c.setAuthorDisplay(display);
        c.setBody(req.body());
        SocialCommentEntity saved = commentRepo.save(c);
        p.setCommentCount((int) commentRepo.countByPostId(p.getId()));
        postRepo.save(p);
        events.emit(tenantId, "comment.created", "social-post", p.getId().toString(),
                Map.of("postId", p.getId().toString(), "commentId", saved.getId().toString(),
                        "actorId", actorId == null ? "" : actorId));
        return new SocialDtos.CommentDto(
                saved.getId(), saved.getPostId(), saved.getBody(),
                identityFor(saved.getAuthorActorId(), saved.getAuthorDisplay(), null, null, false, null),
                saved.getParentCommentId(), saved.getCreatedAt(),
                new SocialDtos.ReactionSummaryDto(0, Map.of(), null));
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────
    @Transactional
    public boolean toggleBookmark(UUID tenantId, String actorId, UUID postId, boolean on) {
        SocialPostEntity p = postRepo.findById(postId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        Optional<SocialBookmarkEntity> existing = bookmarkRepo.findFirstByActorIdAndPostId(actorId, p.getId());
        if (on) {
            if (existing.isEmpty()) {
                SocialBookmarkEntity b = new SocialBookmarkEntity();
                b.setTenantId(tenantId);
                b.setActorId(actorId);
                b.setPostId(p.getId());
                bookmarkRepo.save(b);
            }
            return true;
        } else {
            existing.ifPresent(bookmarkRepo::delete);
            return false;
        }
    }

    // ── Reports + Moderation ──────────────────────────────────────────────
    @Transactional
    public SocialDtos.ModerationCaseDto reportPost(UUID tenantId, String reporterActorId, UUID postId,
                                                   SocialDtos.ReportRequest req) {
        SocialPostEntity p = postRepo.findById(postId)
                .filter(e -> tenantId.equals(e.getTenantId()))
                .orElseThrow(() -> notFound("post"));
        SocialModerationCaseEntity c = new SocialModerationCaseEntity();
        c.setTenantId(tenantId);
        c.setPostId(p.getId());
        c.setReporterActorId(reporterActorId == null ? "system:anonymous" : reporterActorId);
        c.setReason(req.reason() == null ? "unspecified" : req.reason());
        c.setDetails(req.details());
        SocialModerationCaseEntity saved = moderationRepo.save(c);
        events.emit(tenantId, "post.reported", "social-post", p.getId().toString(),
                Map.of("postId", p.getId().toString(), "caseId", saved.getId().toString(),
                        "reason", saved.getReason()));
        return new SocialDtos.ModerationCaseDto(saved.getId(), saved.getPostId(), saved.getReporterActorId(),
                saved.getReason(), saved.getDetails(), saved.getStatus(), saved.getDecision(),
                saved.getCreatedAt(), saved.getResolvedAt());
    }

    @Transactional(readOnly = true)
    public List<SocialDtos.ModerationCaseDto> openCases(UUID tenantId) {
        return moderationRepo.findByTenantIdAndStatusOrderByCreatedAtAsc(tenantId, "OPEN").stream()
                .map(c -> new SocialDtos.ModerationCaseDto(
                        c.getId(), c.getPostId(), c.getReporterActorId(), c.getReason(), c.getDetails(),
                        c.getStatus(), c.getDecision(), c.getCreatedAt(), c.getResolvedAt())).toList();
    }

    @Transactional
    public void resolveCase(UUID tenantId, String moderatorActorId, UUID caseId,
                            SocialDtos.ResolveModerationRequest req) {
        SocialModerationCaseEntity c = moderationRepo.findById(caseId)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("moderation case"));
        c.setStatus("RESOLVED");
        c.setDecision(req.decision());
        c.setRationale(req.rationale());
        c.setResolverActorId(moderatorActorId);
        c.setResolvedAt(OffsetDateTime.now());
        moderationRepo.save(c);
        if ("REMOVE".equals(req.decision()) || "ARCHIVE".equals(req.decision())) {
            postRepo.findById(c.getPostId()).ifPresent(p -> {
                p.setStatus("removed");
                postRepo.save(p);
            });
        }
        events.emit(tenantId, "post.moderated", "social-post", c.getPostId().toString(),
                Map.of("postId", c.getPostId().toString(), "caseId", c.getId().toString(),
                        "decision", req.decision() == null ? "" : req.decision()));
    }

    // ── Communities ───────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SocialDtos.CommunityDto> listCommunities(UUID tenantId, String actorId, String category, String query) {
        List<SocialCommunityEntity> rows = category == null || category.isBlank()
                ? communityRepo.findByTenantIdOrderByMemberCountDesc(tenantId)
                : communityRepo.findByTenantIdAndCategoryOrderByMemberCountDesc(tenantId, category);
        if (query != null && !query.isBlank()) {
            String q = query.toLowerCase(Locale.ROOT);
            rows = rows.stream().filter(c ->
                    c.getName().toLowerCase(Locale.ROOT).contains(q) ||
                    (c.getDescription() != null && c.getDescription().toLowerCase(Locale.ROOT).contains(q))
            ).toList();
        }
        return rows.stream().map(c -> toCommunityDto(c, actorId)).toList();
    }

    @Transactional(readOnly = true)
    public SocialDtos.CommunityDto getCommunity(UUID tenantId, String actorId, UUID id) {
        SocialCommunityEntity c = communityRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("community"));
        return toCommunityDto(c, actorId);
    }

    @Transactional
    public SocialDtos.CommunityDto createCommunity(UUID tenantId, String actorId,
                                                   SocialDtos.CreateCommunityRequest req) {
        SocialCommunityEntity c = new SocialCommunityEntity();
        c.setTenantId(tenantId);
        c.setName(req.name());
        c.setSlug(req.slug() == null ? slugify(req.name()) : req.slug());
        c.setDescription(req.description());
        c.setCategory(req.category() == null ? "general" : req.category());
        c.setKind(req.kind() == null ? "public" : req.kind());
        c.setCoverColor(req.coverColor());
        c.setIconUrl(req.iconUrl());
        c.setCoverImageUrl(req.coverImageUrl());
        c.setRulesJson(SocialJson.writeJson(json, req.rules() == null ? List.of() : req.rules()));
        c.setModeratorIdsJson(SocialJson.writeJson(json, actorId == null ? List.of() : List.of(actorId)));
        SocialCommunityEntity saved = communityRepo.save(c);
        events.emit(tenantId, "community.created", "social-community", saved.getId().toString(),
                Map.of("communityId", saved.getId().toString(), "name", saved.getName()));
        if (actorId != null && !actorId.isBlank()) {
            joinCommunity(tenantId, actorId, saved.getId());
        }
        return toCommunityDto(saved, actorId);
    }

    @Transactional
    public void joinCommunity(UUID tenantId, String actorId, UUID communityId) {
        SocialCommunityEntity c = communityRepo.findById(communityId)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("community"));
        if (communityMembershipRepo.findFirstByCommunityIdAndActorId(c.getId(), actorId).isPresent()) {
            return;
        }
        SocialCommunityMembershipEntity m = new SocialCommunityMembershipEntity();
        m.setTenantId(tenantId);
        m.setCommunityId(c.getId());
        m.setActorId(actorId);
        m.setStatus("restricted".equals(c.getKind()) ? "requested" : "active");
        communityMembershipRepo.save(m);
        c.setMemberCount((int) communityMembershipRepo.countByCommunityIdAndStatus(c.getId(), "active"));
        communityRepo.save(c);
        events.emit(tenantId, "community.joined", "social-community", c.getId().toString(),
                Map.of("communityId", c.getId().toString(), "actorId", actorId, "status", m.getStatus()));
    }

    @Transactional
    public void leaveCommunity(UUID tenantId, String actorId, UUID communityId) {
        SocialCommunityEntity c = communityRepo.findById(communityId)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("community"));
        communityMembershipRepo.findFirstByCommunityIdAndActorId(c.getId(), actorId)
                .ifPresent(communityMembershipRepo::delete);
        c.setMemberCount((int) communityMembershipRepo.countByCommunityIdAndStatus(c.getId(), "active"));
        communityRepo.save(c);
    }

    // ── Groups ────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SocialDtos.GroupDto> listGroups(UUID tenantId, String actorId, UUID communityId) {
        List<SocialGroupEntity> rows = communityId == null
                ? groupRepo.findByTenantIdOrderByMemberCountDesc(tenantId)
                : groupRepo.findByTenantIdAndCommunityIdOrderByMemberCountDesc(tenantId, communityId);
        return rows.stream().map(g -> toGroupDto(g, actorId)).toList();
    }

    @Transactional(readOnly = true)
    public SocialDtos.GroupDto getGroup(UUID tenantId, String actorId, UUID id) {
        SocialGroupEntity g = groupRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("group"));
        return toGroupDto(g, actorId);
    }

    @Transactional
    public SocialDtos.GroupDto createGroup(UUID tenantId, String actorId, SocialDtos.CreateGroupRequest req) {
        SocialGroupEntity g = new SocialGroupEntity();
        g.setTenantId(tenantId);
        g.setName(req.name());
        g.setDescription(req.description());
        g.setCommunityId(req.communityId());
        g.setFacilityId(req.facilityId());
        g.setOrganisationId(req.organisationId());
        g.setCategory(req.category());
        SocialGroupEntity saved = groupRepo.save(g);
        events.emit(tenantId, "group.created", "social-group", saved.getId().toString(),
                Map.of("groupId", saved.getId().toString(), "name", saved.getName()));
        if (actorId != null) joinGroup(tenantId, actorId, saved.getId());
        return toGroupDto(saved, actorId);
    }

    @Transactional
    public void joinGroup(UUID tenantId, String actorId, UUID groupId) {
        SocialGroupEntity g = groupRepo.findById(groupId)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("group"));
        if (groupMembershipRepo.findFirstByGroupIdAndActorId(g.getId(), actorId).isPresent()) return;
        SocialGroupMembershipEntity m = new SocialGroupMembershipEntity();
        m.setTenantId(tenantId);
        m.setGroupId(g.getId());
        m.setActorId(actorId);
        groupMembershipRepo.save(m);
        g.setMemberCount(g.getMemberCount() + 1);
        groupRepo.save(g);
    }

    // ── Pages ─────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public List<SocialDtos.PageDto> listPages(UUID tenantId, String actorId, String kind) {
        List<SocialPageEntity> rows = kind == null || kind.isBlank()
                ? pageRepo.findByTenantIdOrderByFollowerCountDesc(tenantId)
                : pageRepo.findByTenantIdAndKindOrderByFollowerCountDesc(tenantId, kind);
        return rows.stream().map(p -> toPageDto(p, actorId)).toList();
    }

    @Transactional(readOnly = true)
    public SocialDtos.PageDto getPage(UUID tenantId, String actorId, UUID id) {
        SocialPageEntity p = pageRepo.findById(id)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("page"));
        return toPageDto(p, actorId);
    }

    @Transactional
    public SocialDtos.PageDto createPage(UUID tenantId, String actorId, SocialDtos.CreatePageRequest req) {
        SocialPageEntity p = new SocialPageEntity();
        p.setTenantId(tenantId);
        p.setKind(req.kind() == null ? "facility" : req.kind());
        p.setName(req.name());
        p.setSlug(req.slug() == null ? slugify(req.name()) : req.slug());
        p.setBio(req.bio());
        p.setIconUrl(req.iconUrl());
        p.setCoverImageUrl(req.coverImageUrl());
        if (req.actionLinks() != null) {
            p.setActionLinksJson(SocialJson.writeJson(json, req.actionLinks()));
        }
        if (actorId != null) {
            p.setAdminIdsJson(SocialJson.writeJson(json, List.of(actorId)));
        }
        SocialPageEntity saved = pageRepo.save(p);
        events.emit(tenantId, "page.created", "social-page", saved.getId().toString(),
                Map.of("pageId", saved.getId().toString(), "name", saved.getName()));
        return toPageDto(saved, actorId);
    }

    @Transactional
    public boolean followPage(UUID tenantId, String actorId, UUID pageId) {
        SocialPageEntity p = pageRepo.findById(pageId)
                .filter(x -> tenantId.equals(x.getTenantId()))
                .orElseThrow(() -> notFound("page"));
        Optional<SocialPageFollowerEntity> existing = pageFollowerRepo.findFirstByPageIdAndActorId(p.getId(), actorId);
        if (existing.isPresent()) {
            pageFollowerRepo.delete(existing.get());
            p.setFollowerCount(Math.max(0, p.getFollowerCount() - 1));
            pageRepo.save(p);
            return false;
        }
        SocialPageFollowerEntity f = new SocialPageFollowerEntity();
        f.setTenantId(tenantId);
        f.setPageId(p.getId());
        f.setActorId(actorId);
        pageFollowerRepo.save(f);
        p.setFollowerCount(p.getFollowerCount() + 1);
        pageRepo.save(p);
        return true;
    }

    // ── Suggestions ───────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public SocialDtos.SuggestionsBundleDto suggestions(UUID tenantId, String actorId) {
        Set<UUID> joinedCommunities = actorId == null ? Set.of()
                : communityMembershipRepo.findByActorIdAndStatus(actorId, "active").stream()
                .map(SocialCommunityMembershipEntity::getCommunityId).collect(Collectors.toSet());
        Set<UUID> joinedGroups = actorId == null ? Set.of()
                : groupMembershipRepo.findByActorIdAndStatus(actorId, "active").stream()
                .map(SocialGroupMembershipEntity::getGroupId).collect(Collectors.toSet());
        Set<UUID> followedPages = actorId == null ? Set.of()
                : pageFollowerRepo.findByActorId(actorId).stream()
                .map(SocialPageFollowerEntity::getPageId).collect(Collectors.toSet());

        List<SocialDtos.CommunityDto> communities = communityRepo
                .findByTenantIdOrderByMemberCountDesc(tenantId).stream()
                .filter(c -> !joinedCommunities.contains(c.getId()))
                .limit(5).map(c -> toCommunityDto(c, actorId)).toList();
        List<SocialDtos.GroupDto> groups = groupRepo
                .findByTenantIdOrderByMemberCountDesc(tenantId).stream()
                .filter(g -> !joinedGroups.contains(g.getId()))
                .limit(5).map(g -> toGroupDto(g, actorId)).toList();
        List<SocialDtos.PageDto> pages = pageRepo
                .findByTenantIdOrderByFollowerCountDesc(tenantId).stream()
                .filter(p -> !followedPages.contains(p.getId()))
                .limit(5).map(p -> toPageDto(p, actorId)).toList();
        return new SocialDtos.SuggestionsBundleDto(communities, groups, pages);
    }

    // ── Mapping helpers ───────────────────────────────────────────────────
    private SocialDtos.PostDto toPostDto(SocialPostEntity p, String viewerActorId) {
        SocialDtos.IdentityDto author = identityFor(
                p.getAuthorActorId(), p.getAuthorDisplay(),
                null, p.getAuthorRoleBadge(),
                p.getAuthorRoleBadge() != null,
                p.getAuthorFacility());
        SocialDtos.ScopeDto scope = new SocialDtos.ScopeDto(
                p.getCommunityId(), p.getGroupId(), p.getPageId(), p.getFacilityId());
        List<String> topics = SocialJson.readStringList(json, p.getTopicsJson());
        List<SocialDtos.AttachmentDto> attachments = SocialJson.readListOfMaps(json, p.getAttachmentsJson()).stream()
                .map(m -> new SocialDtos.AttachmentDto(
                        Objects.toString(m.get("id"), null),
                        Objects.toString(m.get("mediaType"), null),
                        Objects.toString(m.get("url"), null),
                        Objects.toString(m.get("previewText"), null),
                        Objects.toString(m.get("kind"), null),
                        m.get("metadata") instanceof Map ? (Map<String, Object>) m.get("metadata") : Map.of()))
                .toList();
        Map<String, Object> summaryMap = SocialJson.readMap(json, p.getReactionSummaryJson());
        int total = summaryMap.get("total") instanceof Number ? ((Number) summaryMap.get("total")).intValue()
                : p.getReactionCount();
        Map<String, Integer> byType = new LinkedHashMap<>();
        Object byTypeObj = summaryMap.get("byType");
        if (byTypeObj instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> {
                if (v instanceof Number n) byType.put(String.valueOf(k), n.intValue());
            });
        }
        String viewerReaction = viewerActorId == null ? null
                : reactionRepo.findFirstByTargetTypeAndTargetIdAndActorId(TARGET_POST, p.getId(), viewerActorId)
                .map(SocialReactionEntity::getReactionType).orElse(null);
        boolean bookmarked = viewerActorId != null &&
                bookmarkRepo.findFirstByActorIdAndPostId(viewerActorId, p.getId()).isPresent();
        Map<String, Object> nompiloFlags = SocialJson.readMap(json, p.getNompiloFlagsJson());

        return new SocialDtos.PostDto(
                p.getId(), p.getTenantId(), p.getKind(), p.getStatus(), p.getVisibility(),
                p.getTitle(), p.getBody(), topics, author, scope, attachments,
                new SocialDtos.ReactionSummaryDto(total, byType, viewerReaction),
                p.getCommentCount(), bookmarked, p.isPinned(), p.getSource(),
                p.getCreatedAt(), p.getPublishedAt(), p.getScheduledFor(), nompiloFlags);
    }

    private SocialDtos.CommunityDto toCommunityDto(SocialCommunityEntity c, String viewerActorId) {
        List<String> rules = SocialJson.readStringList(json, c.getRulesJson());
        List<String> moderators = SocialJson.readStringList(json, c.getModeratorIdsJson());
        String viewerMembership = "none";
        if (viewerActorId != null) {
            viewerMembership = communityMembershipRepo.findFirstByCommunityIdAndActorId(c.getId(), viewerActorId)
                    .map(m -> m.getRole() == null ? "member" : m.getRole())
                    .orElse(moderators.contains(viewerActorId) ? "moderator" : "none");
        }
        return new SocialDtos.CommunityDto(
                c.getId(), c.getTenantId(), c.getSlug(), c.getName(), c.getDescription(),
                c.getCategory(), c.getKind(), c.getCoverColor(), c.getIconUrl(), c.getCoverImageUrl(),
                c.getMemberCount(), rules, moderators, viewerMembership, c.getCreatedAt(),
                c.isNompiloAssistantEnabled());
    }

    private SocialDtos.GroupDto toGroupDto(SocialGroupEntity g, String viewerActorId) {
        String viewerMembership = viewerActorId == null ? "none"
                : groupMembershipRepo.findFirstByGroupIdAndActorId(g.getId(), viewerActorId)
                .map(m -> m.getRole() == null ? "member" : m.getRole()).orElse("none");
        return new SocialDtos.GroupDto(
                g.getId(), g.getCommunityId(), g.getName(), g.getDescription(), g.getCategory(),
                g.getFacilityId(), g.getOrganisationId(), g.getMemberCount(),
                viewerMembership, g.getCreatedAt());
    }

    @SuppressWarnings("unchecked")
    private SocialDtos.PageDto toPageDto(SocialPageEntity p, String viewerActorId) {
        List<SocialDtos.ActionLinkDto> actionLinks = SocialJson.readListOfMaps(json, p.getActionLinksJson()).stream()
                .map(m -> new SocialDtos.ActionLinkDto(
                        Objects.toString(m.get("label"), null),
                        Objects.toString(m.get("href"), null),
                        Objects.toString(m.get("kind"), null))).toList();
        List<String> admins = SocialJson.readStringList(json, p.getAdminIdsJson());
        boolean follows = viewerActorId != null &&
                pageFollowerRepo.findFirstByPageIdAndActorId(p.getId(), viewerActorId).isPresent();
        return new SocialDtos.PageDto(
                p.getId(), p.getTenantId(), p.getKind(), p.getName(), p.getSlug(), p.getBio(),
                p.getIconUrl(), p.getCoverImageUrl(), p.isVerified(), p.getFollowerCount(),
                actionLinks, admins, follows, p.getCreatedAt());
    }

    private static SocialDtos.IdentityDto identityFor(String actorId, String display, String avatar,
                                                      String roleBadge, boolean verified, String facilityName) {
        String kind = "user";
        if (actorId != null) {
            if (actorId.startsWith("system:") || actorId.startsWith("page:")) kind = "page";
            else if (actorId.startsWith("community:")) kind = "community";
            else if (actorId.startsWith("group:")) kind = "group";
        }
        return new SocialDtos.IdentityDto(kind, actorId, display == null ? actorId : display, avatar,
                roleBadge, verified, facilityName);
    }

    private static String slugify(String s) {
        if (s == null) return UUID.randomUUID().toString().substring(0, 8);
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
    }

    private static ResponseStatusException notFound(String what) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, what + " not found");
    }

    private static ResponseStatusException forbidden(String why) {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, why);
    }

    private static ResponseStatusException badRequest(String why) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, why);
    }
}
