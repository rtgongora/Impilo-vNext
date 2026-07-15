package zw.gov.mohcc.impilo.simba.social.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.core.SimbaEventEmitter;
import zw.gov.mohcc.impilo.simba.core.WellnessAuditService;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialCommunityMembershipEntity;
import zw.gov.mohcc.impilo.simba.social.entity.SocialPostEntity;
import zw.gov.mohcc.impilo.simba.social.repository.SocialCommunityMembershipRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialCommunityRepository;
import zw.gov.mohcc.impilo.simba.social.repository.SocialPostRepository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Wellness-social COMMUNITIES — larger umbrellas that groups + members belong to. Owns create/join/
 * leave, the admin role lattice, and official community announcements (a pinned official community-scoped
 * post that emits {@code SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED} with a {@code community_id}). Communities
 * are open-join by default; administration requires an OWNER/ADMIN/PROGRAMME_OFFICER role.
 */
@Service
public class SocialCommunityService {

    static final Set<String> OFFICIAL_ROLES = Set.of(
            "OWNER", "ADMIN", "MODERATOR", "VERIFIED_PROVIDER", "PROGRAMME_OFFICER");
    static final Set<String> ADMIN_ROLES = Set.of("OWNER", "ADMIN", "PROGRAMME_OFFICER");

    private final SocialCommunityRepository communities;
    private final SocialCommunityMembershipRepository memberships;
    private final SocialPostRepository posts;
    private final SimbaEventEmitter events;
    private final WellnessAuditService audit;

    public SocialCommunityService(SocialCommunityRepository communities,
                                  SocialCommunityMembershipRepository memberships,
                                  SocialPostRepository posts,
                                  SimbaEventEmitter events,
                                  WellnessAuditService audit) {
        this.communities = communities;
        this.memberships = memberships;
        this.posts = posts;
        this.events = events;
        this.audit = audit;
    }

    public static class CommunityAccessException extends RuntimeException {
        public CommunityAccessException(String message) { super(message); }
    }

    public record CreateCommunity(String name, String description, String communityType,
                                  UUID programmeId, String visibility) { }

    @Transactional
    public SocialCommunityEntity create(UUID tenantId, String creatorCpid, CreateCommunity in,
                                        String correlationId, String requestId) {
        SocialCommunityEntity c = new SocialCommunityEntity();
        c.setTenantId(tenantId);
        c.setName(in.name());
        c.setDescription(in.description());
        if (in.communityType() != null && !in.communityType().isBlank()) {
            c.setCommunityType(in.communityType());
        }
        c.setProgrammeId(in.programmeId());
        if (in.visibility() != null && !in.visibility().isBlank()) c.setVisibility(in.visibility());
        c.setCreatedBy(creatorCpid);
        c.setMemberCount(1);
        c = communities.save(c);

        SocialCommunityMembershipEntity owner = new SocialCommunityMembershipEntity();
        owner.setTenantId(tenantId);
        owner.setCommunityId(c.getCommunityId());
        owner.setPersonCpid(creatorCpid);
        owner.setRole("OWNER");
        memberships.save(owner);

        emit(tenantId, "WELLNESS_SOCIAL_COMMUNITY", c.getCommunityId(),
                "impilo.simba.wellness.social.community.created.v1", creatorCpid,
                Map.of("community_id", c.getCommunityId().toString(), "name", c.getName()));
        audit.recordSelf(tenantId, creatorCpid, "CITIZEN", creatorCpid,
                "wellness.social.community.create", correlationId, requestId);
        return c;
    }

    @Transactional(readOnly = true)
    public List<SocialCommunityEntity> list(UUID tenantId) {
        return communities.findByTenantIdAndStatusOrderByNameAsc(tenantId, "ACTIVE");
    }

    @Transactional(readOnly = true)
    public SocialCommunityEntity get(UUID tenantId, UUID communityId) {
        return communities.findByCommunityIdAndTenantId(communityId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Community not found"));
    }

    @Transactional(readOnly = true)
    public List<SocialCommunityMembershipEntity> members(UUID communityId) {
        return memberships.findByCommunityIdOrderByJoinedAtAsc(communityId);
    }

    @Transactional
    public SocialCommunityMembershipEntity join(UUID tenantId, UUID communityId, String personCpid,
                                                String correlationId, String requestId) {
        SocialCommunityEntity c = get(tenantId, communityId);
        var existing = memberships.findByCommunityIdAndPersonCpid(communityId, personCpid);
        if (existing.isPresent() && "ACTIVE".equals(existing.get().getStatus())) {
            return existing.get();
        }
        SocialCommunityMembershipEntity m = existing.orElseGet(SocialCommunityMembershipEntity::new);
        m.setTenantId(tenantId);
        m.setCommunityId(communityId);
        m.setPersonCpid(personCpid);
        m.setRole("MEMBER");
        m.setStatus("ACTIVE");
        m = memberships.save(m);
        c.setMemberCount((int) memberships.countByCommunityIdAndStatus(communityId, "ACTIVE"));
        communities.save(c);
        emit(tenantId, "WELLNESS_SOCIAL_COMMUNITY_MEMBERSHIP", communityId,
                "impilo.simba.wellness.social.community.joined.v1", personCpid,
                Map.of("community_id", communityId.toString()));
        audit.recordSelf(tenantId, personCpid, "CITIZEN", personCpid,
                "wellness.social.community.join", correlationId, requestId);
        return m;
    }

    @Transactional
    public void leave(UUID tenantId, UUID communityId, String personCpid,
                      String correlationId, String requestId) {
        SocialCommunityMembershipEntity m = memberships
                .findByCommunityIdAndPersonCpid(communityId, personCpid)
                .orElseThrow(() -> new IllegalArgumentException("Not a member"));
        m.setStatus("LEFT");
        memberships.save(m);
        SocialCommunityEntity c = get(tenantId, communityId);
        c.setMemberCount((int) memberships.countByCommunityIdAndStatus(communityId, "ACTIVE"));
        communities.save(c);
        audit.recordSelf(tenantId, personCpid, "CITIZEN", personCpid,
                "wellness.social.community.leave", correlationId, requestId);
    }

    @Transactional
    public SocialCommunityMembershipEntity setRole(UUID tenantId, UUID communityId, String actorCpid,
                                                   String memberCpid, String newRole,
                                                   String correlationId, String requestId) {
        String actorRole = requireRole(communityId, actorCpid, ADMIN_ROLES, "change roles");
        String target = newRole == null ? "MEMBER" : newRole.toUpperCase();
        if ("OWNER".equals(target) && !"OWNER".equals(actorRole)) {
            throw new CommunityAccessException("Only an OWNER may grant OWNER");
        }
        SocialCommunityMembershipEntity m = memberships
                .findByCommunityIdAndPersonCpid(communityId, memberCpid)
                .orElseThrow(() -> new IllegalArgumentException("Membership not found"));
        m.setRole(target);
        m = memberships.save(m);
        audit.record(tenantId, actorCpid, "CITIZEN", null, memberCpid, "PROGRAMME_ADMIN",
                null, null, "wellness.social.community.set-role", "ALLOW", target,
                correlationId, requestId);
        return m;
    }

    /**
     * Publish an official community announcement — creates a pinned official community-scoped post +
     * emits {@code SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED} (community variant). Requires an official role.
     */
    @Transactional
    public SocialPostEntity announce(UUID tenantId, UUID communityId, String actorCpid, String actorType,
                                     String body, String correlationId, String requestId) {
        requireRole(communityId, actorCpid, OFFICIAL_ROLES, "publish announcements");
        MilestoneSanitiser.requireSafe(body);
        SocialCommunityEntity c = get(tenantId, communityId);

        SocialPostEntity p = new SocialPostEntity();
        p.setTenantId(tenantId);
        p.setActorCpid(actorCpid);
        p.setActorType(actorType == null ? "FACILITY_PROGRAMME" : actorType);
        p.setCommunityId(communityId);
        p.setProgrammeId(c.getProgrammeId());
        p.setBody(body);
        p.setVisibility("COMMUNITY");
        p.setPinned(true);
        p.setOfficial(true);
        p = posts.save(p);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("community_id", communityId.toString());
        payload.put("post_id", p.getPostId().toString());
        payload.put("community_name", c.getName());
        emit(tenantId, "SIMBA_GROUP_ANNOUNCEMENT_PUBLISHED", communityId,
                "impilo.simba.wellness.social.community.announcement.v1", actorCpid, payload);
        audit.record(tenantId, actorCpid, actorType, null, actorCpid, "PROGRAMME_ADMIN",
                null, null, "wellness.social.community.announce", "ALLOW", null,
                correlationId, requestId);
        return p;
    }

    /** Resolve the actor's active role and enforce it is in {@code allowed}; returns the role. */
    private String requireRole(UUID communityId, String actorCpid, Set<String> allowed, String what) {
        SocialCommunityMembershipEntity m = memberships
                .findByCommunityIdAndPersonCpid(communityId, actorCpid)
                .orElseThrow(() -> new CommunityAccessException("Not a member of this community"));
        if (!"ACTIVE".equals(m.getStatus()) || !allowed.contains(m.getRole())) {
            throw new CommunityAccessException("You do not have permission to " + what);
        }
        return m.getRole();
    }

    private void emit(UUID tenantId, String aggregateType, UUID aggregateId, String eventType,
                      String subjectCpid, Map<String, Object> payload) {
        events.emit(tenantId, aggregateType, aggregateId.toString(), eventType, subjectCpid,
                "simba:" + aggregateType.toLowerCase() + ":" + aggregateId, payload);
    }
}
