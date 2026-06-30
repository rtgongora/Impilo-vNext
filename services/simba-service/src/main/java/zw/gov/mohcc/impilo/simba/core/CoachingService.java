package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingRelationshipEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.CoachingTouchpointEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.CoachingRelationshipRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.CoachingTouchpointRepository;

import java.util.List;
import java.util.UUID;

/**
 * Wellness coaching: relationships between a participant and a coach (Varapi provider id), and the
 * touchpoints/notes logged within them. A coaching relationship is NOT a clinical encounter —
 * escalation to clinical care routes through {@link CareLinkageService} into PCT.
 */
@Service
public class CoachingService {

    private final CoachingRelationshipRepository relationshipRepository;
    private final CoachingTouchpointRepository touchpointRepository;

    public CoachingService(CoachingRelationshipRepository relationshipRepository,
                           CoachingTouchpointRepository touchpointRepository) {
        this.relationshipRepository = relationshipRepository;
        this.touchpointRepository = touchpointRepository;
    }

    @Transactional(readOnly = true)
    public List<CoachingRelationshipEntity> forPerson(UUID tenantId, String personCpid) {
        return relationshipRepository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }

    @Transactional(readOnly = true)
    public List<CoachingRelationshipEntity> forCoach(UUID tenantId, String coachProviderId) {
        return relationshipRepository.findByTenantIdAndCoachProviderIdOrderByCreatedAtDesc(tenantId, coachProviderId);
    }

    @Transactional(readOnly = true)
    public CoachingRelationshipEntity get(UUID tenantId, UUID relationshipId) {
        return relationshipRepository.findByRelationshipIdAndTenantId(relationshipId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Coaching relationship not found"));
    }

    @Transactional
    public CoachingRelationshipEntity assign(CoachingRelationshipEntity incoming) {
        String focus = incoming.getFocus() != null ? incoming.getFocus() : "GENERAL";
        relationshipRepository.findByTenantIdAndPersonCpidAndCoachProviderIdAndStatus(
                        incoming.getTenantId(), incoming.getPersonCpid(), incoming.getCoachProviderId(), "ACTIVE")
                .ifPresent(existing -> {
                    if (focus.equals(existing.getFocus())) {
                        throw new IllegalStateException("Active coaching relationship already exists for this focus");
                    }
                });
        incoming.setFocus(focus);
        return relationshipRepository.save(incoming);
    }

    @Transactional(readOnly = true)
    public List<CoachingTouchpointEntity> touchpoints(UUID relationshipId) {
        return touchpointRepository.findByRelationshipIdOrderByCreatedAtDesc(relationshipId);
    }

    /** True when the given coach provider id owns an active relationship with the subject. */
    @Transactional(readOnly = true)
    public boolean coachIsAssigned(UUID tenantId, String coachProviderId, UUID relationshipId) {
        return relationshipRepository.findByRelationshipIdAndTenantId(relationshipId, tenantId)
                .map(r -> "ACTIVE".equals(r.getStatus())
                        && r.getCoachProviderId() != null
                        && r.getCoachProviderId().equals(coachProviderId))
                .orElse(false);
    }

    @Transactional
    public CoachingTouchpointEntity addTouchpoint(UUID tenantId, UUID relationshipId,
                                                  CoachingTouchpointEntity incoming) {
        get(tenantId, relationshipId); // validate ownership/tenant
        incoming.setTenantId(tenantId);
        incoming.setRelationshipId(relationshipId);
        return touchpointRepository.save(incoming);
    }
}
