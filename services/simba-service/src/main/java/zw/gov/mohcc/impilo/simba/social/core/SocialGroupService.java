package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialGroupMembershipEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialGroupMembershipRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialGroupRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialPostRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wellness-social GROUPS (supersede clubs via {@code group_kind='CLUB'}). Owns create/join/request/
 * approve/leave, the role lattice, posting/comment permission enforcement, the sensitive-category flag,
 * pinned official posts, and official announcements (emits {@code SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED}).
 *
 * <p>Role lattice (highest → lowest privilege):
 * OWNER, ADMIN, MODERATOR, VERIFIED_PROVIDER, PROGRAMME_OFFICER, MEMBER, CAREGIVER, VIEWER.
 * Officials (OWNER/ADMIN/MODERATOR/VERIFIED_PROVIDER/PROGRAMME_OFFICER) may pin + announce; VIEWER may
 * neither post nor comment; a per-membership {@code can_post}/{@code can_comment} override tightens this.
 */
@Service
public class SocialGroupService {

    /** Roles that may publish official announcements + pin posts. */
    static final Set<String> OFFICIAL_ROLES = Set.of(
            "OWNER", "ADMIN", "MODERATOR", "VERIFIED_PROVIDER", "PROGRAMME_OFFICER");
    /** Roles that may administer membership (approve/remove/role-change). */
    static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN");

    private final SocialGroupRepository groups;
    private final SocialGroupMembershipRepository memberships;
    private final SocialPostRepository posts;
    private final SimbaEventEmitter events;
    private final WellnessAuditService audit;

    public SocialGroupService(SocialGroupRepository groups,
                              SocialGroupMembershipRepository memberships,
                              SocialPostRepository posts,
                              SimbaEventEmitter events,
                              WellnessAuditService audit) {
        this.groups = groups;
        this.memberships = memberships;
        this.posts = posts;
        this.events = events;
        this.audit = audit;
    }

    public static class GroupAccessException extends RuntimeException {
        public GroupAccessException(String message) { super(message); }
    }

    public record CreateGroup(String name, String description, String groupKind, String topic,
                              boolean sensitiveFlag, UUID communityId, UUID programmeId,
                              String visibility, String joinPolicy, Integer maxMembers) { }

    // ── lifecycle ────────────────────────────────────────────────────────────────

    @Transactional
    public SocialGroupEntity create(UUID tenantId, String creatorCpid, CreateGroup in,
                                    String correlationId, String requestId) {
        SocialGroupEntity g = new SocialGroupEntity();
        g.setTenantId(tenantId);
        g.setName(in.name());
        g.setDescription(in.description());
        if (in.groupKind() != null && !in.groupKind().isBlank()) g.setGroupKind(in.groupKind());
        g.setTopic(in.topic());
        g.setSensitiveFlag(in.sensitiveFlag());
        g.setCommunityId(in.communityId());
        g.setProgrammeId(in.programmeId());
        if (in.visibility() != null && !in.visibility().isBlank()) g.setVisibility(in.visibility());
        if (in.joinPolicy() != null && !in.joinPolicy().isBlank()) g.setJoinPolicy(in.joinPolicy());
        g.setMaxMembers(in.maxMembers());
        g.setCreatedBy(creatorCpid);
        g.setMemberCount(1);
        g = groups.save(g);

        // Creator becomes OWNER.
        SocialGroupMembershipEntity owner = new SocialGroupMembershipEntity();
        owner.setTenantId(tenantId);
        owner.setGroupId(g.getGroupId());
        owner.setPersonCpid(creatorCpid);
        owner.setRole("OWNER");
        owner.setStatus("ACTIVE");
        memberships.save(owner);

        emit(tenantId, "WELLNESS_SOCIAL_GROUP", g.getGroupId(),
                "impilo.simba.wellness.social.group.created.v1", creatorCpid,
                Map.of("group_id", g.getGroupId().toString(), "name", g.getName(),
                        "sensitive_flag", g.isSensitiveFlag()));
        audit.recordSelf(tenantId, creatorCpid, "CITIZEN", creatorCpid,
                "wellness.social.group.create", correlationId, requestId);
        return g;
    }

    @Transactional(readOnly = true)
    public List<SocialGroupEntity> list(UUID tenantId) {
        return groups.findByTenantIdAndStatusOrderByNameAsc(tenantId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public SocialGroupEntity get(UUID tenantId, UUID groupId) {
        return groups.findByGroupIdAndTenantId(groupId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found"));
    }

    @Transactional(readOnly = true)
    public List<SocialGroupMembershipEntity> members(UUID groupId) {
        return memberships.findByGroupIdOrderByJoinedAtAsc(groupId);
    }

    @Transactional(readOnly = true)
    public List<SocialGroupEntity> myGroups(UUID tenantId, String personCpid) {
        return memberships.findByTenantIdAndPersonCpidAndStatus(tenantId, personCpid, "ACTIVE")
                .stream()
                .map(m -> groups.findByGroupIdAndTenantId(m.getGroupId(), tenantId).orElse(null))
                .filter(g -> g != null)
                .toList();
    }

    /**
     * Join (OPEN → ACTIVE) or request (REQUEST → REQUESTED). CLOSED groups reject direct joins.
     * Returns the membership with its resolved status.
     */
    @Transactional
    public SocialGroupMembershipEntity join(UUID tenantId, UUID groupId, String personCpid,
                                            String correlationId, String requestId) {
        SocialGroupEntity g = get(tenantId, groupId);
        var existing = memberships.findByGroupIdAndPersonCpid(groupId, personCpid);
        if (existing.isPresent() && "ACTIVE".equals(existing.get().getStatus())) {
            return existing.get();
        }
        if (g.getMaxMembers() != null
                && memberships.countByGroupIdAndStatus(groupId, "ACTIVE") >= g.getMaxMembers()) {
            throw new IllegalStateException("Group is full");
        }
        String policy = g.getJoinPolicy() == null ? "OPEN" : g.getJoinPolicy().toUpperCase();
        if ("CLOSED".equals(policy)) {
            throw new GroupAccessException("Group is closed to new members");
        }
        SocialGroupMembershipEntity m = existing.orElseGet(SocialGroupMembershipEntity::new);
        m.setTenantId(tenantId);
        m.setGroupId(groupId);
        m.setPersonCpid(personCpid);
        m.setRole("MEMBER");
        boolean approved = "OPEN".equals(policy);
        m.setStatus(approved ? "ACTIVE" : "REQUESTED");
        m = memberships.save(m);
        if (approved) {
            g.setMemberCount((int) memberships.countByGroupIdAndStatus(groupId, "ACTIVE"));
            groups.save(g);
        }
        emit(tenantId, "WELLNESS_SOCIAL_GROUP_MEMBERSHIP", groupId,
                approved ? "impilo.simba.wellness.social.group.joined.v1"
                        : "impilo.simba.wellness.social.group.requested.v1",
                personCpid, Map.of("group_id", groupId.toString(), "status", m.getStatus()));
        audit.recordSelf(tenantId, personCpid, "CITIZEN", personCpid,
                approved ? "wellness.social.group.join" : "wellness.social.group.request",
                correlationId, requestId);
        return m;
    }

    /** Approve a pending REQUESTED member. Requires an OWNER/ADMIN actor. */
    @Transactional
    public SocialGroupMembershipEntity approve(UUID tenantId, UUID groupId, String actorCpid,
                                               String memberCpid, String correlationId, String requestId) {
        requireRole(groupId, actorCpid, ADMIN_ROLES, "approve members");
        SocialGroupMembershipEntity m = memberships.findByGroupIdAndPersonCpid(groupId, memberCpid)
                .orElseThrow(() -> new IllegalArgumentException("Membership request not found"));
        if (!"REQUESTED".equals(m.getStatus())) {
            throw new IllegalStateException("Membership is not pending approval");
        }
        m.setStatus("ACTIVE");
        m = memberships.save(m);
        SocialGroupEntity g = get(tenantId, groupId);
        g.setMemberCount((int) memberships.countByGroupIdAndStatus(groupId, "ACTIVE"));
        groups.save(g);
        emit(tenantId, "WELLNESS_SOCIAL_GROUP_MEMBERSHIP", groupId,
                "impilo.simba.wellness.social.group.approved.v1", memberCpid,
                Map.of("group_id", groupId.toString(), "approved_by", actorCpid));
        audit.record(tenantId, actorCpid, "CITIZEN", null, memberCpid, "COACH",
                null, null, "wellness.social.group.approve", "ALLOW", null, correlationId, requestId);
        return m;
    }

    /** Change a member's role. Requires an OWNER/ADMIN actor; OWNER role can only be granted by OWNER. */
    @Transactional
    public SocialGroupMembershipEntity setRole(UUID tenantId, UUID groupId, String actorCpid,
                                               String memberCpid, String newRole,
                                               String correlationId, String requestId) {
        String actorRole = requireRole(groupId, actorCpid, ADMIN_ROLES, "change roles");
        String target = newRole == null ? "MEMBER" : newRole.toUpperCase();
        if ("OWNER".equals(target) && !"OWNER".equals(actorRole)) {
            throw new GroupAccessException("Only an OWNER may grant OWNER");
        }
        SocialGroupMembershipEntity m = memberships.findByGroupIdAndPersonCpid(groupId, memberCpid)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        m.setRole(target);
        // VIEWER cannot post/comment; officials get posting by default.
        if ("VIEWER".equals(target)) {
            m.setCanPost(false);
            m.setCanComment(false);
        }
        m = memberships.save(m);
        audit.record(tenantId, actorCpid, "CITIZEN", null, memberCpid, "COACH",
                null, null, "wellness.social.group.set-role", "ALLOW", target, correlationId, requestId);
        return m;
    }

    @Transactional
    public void leave(UUID tenantId, UUID groupId, String personCpid,
                      String correlationId, String requestId) {
        SocialGroupMembershipEntity m = memberships.findByGroupIdAndPersonCpid(groupId, personCpid)
                .orElseThrow(() -> new IllegalArgumentException("Not a member"));
        m.setStatus("LEFT");
        memberships.save(m);
        SocialGroupEntity g = get(tenantId, groupId);
        g.setMemberCount((int) memberships.countByGroupIdAndStatus(groupId, "ACTIVE"));
        groups.save(g);
        audit.recordSelf(tenantId, personCpid, "CITIZEN", personCpid,
                "wellness.social.group.leave", correlationId, requestId);
    }

    // ── posting / comment permission ──────────────────────────────────────────────

    /** Resolve the active membership, or throw if the person is not an active member. */
    @Transactional(readOnly = true)
    public SocialGroupMembershipEntity requireActiveMember(UUID groupId, String personCpid) {
        SocialGroupMembershipEntity m = memberships.findByGroupIdAndPersonCpid(groupId, personCpid)
                .orElseThrow(() -> new GroupAccessException("Not a member of this group"));
        if (!"ACTIVE".equals(m.getStatus())) {
            throw new GroupAccessException("Membership is not active");
        }
        return m;
    }

    /** True when the member may post in the group (VIEWER + can_post=false are blocked). */
    public boolean canPost(SocialGroupMembershipEntity m) {
        return m != null && "ACTIVE".equals(m.getStatus())
                && !"VIEWER".equals(m.getRole()) && m.isCanPost();
    }

    /** True when the member may comment in the group. */
    public boolean canComment(SocialGroupMembershipEntity m) {
        return m != null && "ACTIVE".equals(m.getStatus())
                && !"VIEWER".equals(m.getRole()) && m.isCanComment();
    }

    /** True when the member holds an official role (may pin / announce). */
    public boolean isOfficial(SocialGroupMembershipEntity m) {
        return m != null && OFFICIAL_ROLES.contains(m.getRole());
    }

    /** Enforce that the person may post; throws {@link GroupAccessException} otherwise. */
    @Transactional(readOnly = true)
    public void enforceCanPost(UUID groupId, String personCpid) {
        if (!canPost(requireActiveMember(groupId, personCpid))) {
            throw new GroupAccessException("You do not have permission to post in this group");
        }
    }

    // ── pinned + announcements ────────────────────────────────────────────────────

    /** Pin an existing post as an official group post. Requires an official role. */
    @Transactional
    public SocialPostEntity pin(UUID tenantId, UUID groupId, String actorCpid, UUID postId,
                                String correlationId, String requestId) {
        requireRole(groupId, actorCpid, OFFICIAL_ROLES, "pin posts");
        SocialPostEntity p = posts.findByPostIdAndTenantId(postId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found"));
        if (p.getGroupId() == null || !p.getGroupId().equals(groupId)) {
            throw new IllegalArgumentException("Post does not belong to this group");
        }
        p.setPinned(true);
        p.setOfficial(true);
        posts.save(p);
        audit.record(tenantId, actorCpid, "CITIZEN", null, actorCpid, "COACH",
                null, null, "wellness.social.group.pin", "ALLOW", null, correlationId, requestId);
        return p;
    }

    /**
     * Publish an official group announcement — creates a pinned official post + emits
     * {@code SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED}. Requires an official role. Body must be milestone-safe.
     */
    @Transactional
    public SocialPostEntity announce(UUID tenantId, UUID groupId, String actorCpid, String actorType,
                                     String body, String correlationId, String requestId) {
        requireRole(groupId, actorCpid, OFFICIAL_ROLES, "publish announcements");
        MilestoneSanitiser.requireSafe(body);
        SocialGroupEntity g = get(tenantId, groupId);

        SocialPostEntity p = new SocialPostEntity();
        p.setTenantId(tenantId);
        p.setActorCpid(actorCpid);
        p.setActorType(actorType == null ? "PROVIDER" : actorType);
        p.setGroupId(groupId);
        p.setCommunityId(g.getCommunityId());
        p.setProgrammeId(g.getProgrammeId());
        p.setBody(body);
        p.setVisibility("GROUP");
        p.setPinned(true);
        p.setOfficial(true);
        p = posts.save(p);

        emit(tenantId, "SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED", groupId,
                "impilo.simba.wellness.social.group.announcement.v1", actorCpid,
                announcementPayload(groupId, p.getPostId(), g.getName()));
        audit.record(tenantId, actorCpid, actorType, null, actorCpid, "COACH",
                null, null, "wellness.social.group.announce", "ALLOW", null, correlationId, requestId);
        return p;
    }

    private Map<String, Object> announcementPayload(UUID groupId, UUID postId, String groupName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("group_id", groupId.toString());
        payload.put("post_id", postId.toString());
        payload.put("group_name", groupName);
        return payload;
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    /** Resolve the actor's active role and enforce it is in {@code allowed}; returns the role. */
    private String requireRole(UUID groupId, String actorCpid, Set<String> allowed, String what) {
        SocialGroupMembershipEntity m = memberships.findByGroupIdAndPersonCpid(groupId, actorCpid)
                .orElseThrow(() -> new GroupAccessException("Not a member of this group"));
        if (!"ACTIVE".equals(m.getStatus()) || !allowed.contains(m.getRole())) {
            throw new GroupAccessException("You do not have permission to " + what);
        }
        return m.getRole();
    }

    private void emit(UUID tenantId, String aggregateType, UUID aggregateId, String eventType,
                      String subjectCpid, Map<String, Object> payload) {
        events.emit(tenantId, aggregateType, aggregateId.toString(), eventType, subjectCpid,
                "simba:" + aggregateType.toLowerCase() + ":" + aggregateId, payload);
    }
}
