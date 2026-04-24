package zw.gov.mohcc.impilo.governance.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.governance.domain.GovernanceEnums;
import zw.gov.mohcc.impilo.governance.persistence.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ScopeEvaluationService {

    private final AssignmentRepository assignmentRepository;
    private final FacilityOrganisationLinkRepository facilityOrganisationLinkRepository;
    private final JurisdictionLinkRepository jurisdictionLinkRepository;
    private final MultiSiteGroupMemberRepository multiSiteGroupMemberRepository;

    public ScopeEvaluationService(AssignmentRepository assignmentRepository,
                                  FacilityOrganisationLinkRepository facilityOrganisationLinkRepository,
                                  JurisdictionLinkRepository jurisdictionLinkRepository,
                                  MultiSiteGroupMemberRepository multiSiteGroupMemberRepository) {
        this.assignmentRepository = assignmentRepository;
        this.facilityOrganisationLinkRepository = facilityOrganisationLinkRepository;
        this.jurisdictionLinkRepository = jurisdictionLinkRepository;
        this.multiSiteGroupMemberRepository = multiSiteGroupMemberRepository;
    }

    /**
     * Determines whether the subject has at least one active assignment that covers the TUSO facility.
     */
    public ScopeFacilityResult evaluateFacilityScope(UUID tenantId,
                                                     String actorHealthId,
                                                     String providerPublicId,
                                                     Long tusoFacilityId) {
        if (tusoFacilityId == null) {
            return new ScopeFacilityResult(true, "NO_FACILITY_CONTEXT", List.of());
        }
        if (actorHealthId == null || actorHealthId.isBlank()) {
            return new ScopeFacilityResult(false, "MISSING_ACTOR", List.of());
        }

        LocalDate today = GovernanceDates.todayUtc();
        String facilityIdStr = String.valueOf(tusoFacilityId);

        List<AssignmentEntity> candidates = new ArrayList<>();
        candidates.addAll(assignmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                tenantId, GovernanceEnums.AssignmentSubjectType.USER.name(), actorHealthId));
        if (providerPublicId != null && !providerPublicId.isBlank()) {
            candidates.addAll(assignmentRepository.findByTenantIdAndSubjectTypeAndSubjectIdOrderByCreatedAtDesc(
                    tenantId, GovernanceEnums.AssignmentSubjectType.PROVIDER.name(), providerPublicId));
        }

        List<String> evidence = new ArrayList<>();
        for (AssignmentEntity a : candidates) {
            if (!GovernanceEnums.AssignmentStatus.ACTIVE.name().equals(a.getStatus())) {
                continue;
            }
            if (!GovernanceDates.isActiveForDate(a.getStartDate(), a.getEndDate(), today)) {
                continue;
            }
            if (matchesFacility(tenantId, a, facilityIdStr, today, evidence)) {
                return new ScopeFacilityResult(true, "ASSIGNMENT_SCOPE", evidence);
            }
        }
        return new ScopeFacilityResult(false, "NO_ACTIVE_ASSIGNMENT_FOR_FACILITY", evidence);
    }

    private boolean matchesFacility(UUID tenantId,
                                    AssignmentEntity a,
                                    String facilityIdStr,
                                    LocalDate today,
                                    List<String> evidence) {
        String tt = a.getTargetType();
        try {
            if (GovernanceEnums.AssignmentTargetType.FACILITY.name().equals(tt)) {
                if (facilityIdStr.equals(a.getTargetId())) {
                    evidence.add("FACILITY_DIRECT:" + a.getId());
                    return true;
                }
            }
            if (GovernanceEnums.AssignmentTargetType.ORGANISATION.name().equals(tt)) {
                UUID orgId = UUID.fromString(a.getTargetId());
                List<FacilityOrganisationLinkEntity> links = facilityOrganisationLinkRepository
                        .findByTenantIdAndFacilityIdAndStatus(tenantId, Long.parseLong(facilityIdStr),
                                GovernanceEnums.LinkStatus.ACTIVE.name());
                boolean ok = links.stream().anyMatch(l ->
                        l.getOrganisationId().equals(orgId)
                                && GovernanceDates.isActiveForDate(l.getStartDate(), l.getEndDate(), today));
                if (ok) {
                    evidence.add("ORGANISATION_NETWORK:" + a.getId());
                    return true;
                }
            }
            if (GovernanceEnums.AssignmentTargetType.ORGANISATION_UNIT.name().equals(tt)) {
                UUID unitId = UUID.fromString(a.getTargetId());
                List<FacilityOrganisationLinkEntity> links = facilityOrganisationLinkRepository
                        .findByTenantIdAndFacilityIdAndStatus(tenantId, Long.parseLong(facilityIdStr),
                                GovernanceEnums.LinkStatus.ACTIVE.name());
                boolean ok = links.stream().anyMatch(l ->
                        l.getOrganisationUnitId() != null
                                && l.getOrganisationUnitId().equals(unitId)
                                && GovernanceDates.isActiveForDate(l.getStartDate(), l.getEndDate(), today));
                if (ok) {
                    evidence.add("ORG_UNIT:" + a.getId());
                    return true;
                }
            }
            if (GovernanceEnums.AssignmentTargetType.JURISDICTION.name().equals(tt)) {
                UUID jurisdictionId = UUID.fromString(a.getTargetId());
                List<JurisdictionLinkEntity> jlinks = jurisdictionLinkRepository
                        .findByTenantIdAndTargetTypeAndTargetIdAndStatus(
                                tenantId,
                                GovernanceEnums.AssignmentTargetType.FACILITY.name(),
                                facilityIdStr,
                                GovernanceEnums.LinkStatus.ACTIVE.name());
                boolean ok = jlinks.stream().anyMatch(j ->
                        j.getJurisdictionId().equals(jurisdictionId)
                                && GovernanceDates.isActiveForDate(j.getStartDate(), j.getEndDate(), today));
                if (ok) {
                    evidence.add("JURISDICTION:" + a.getId());
                    return true;
                }
            }
            if (isGroupLike(tt)) {
                UUID groupId = UUID.fromString(a.getTargetId());
                List<MultiSiteGroupMemberEntity> members = multiSiteGroupMemberRepository
                        .findByTenantIdAndGroupIdAndStatus(tenantId, groupId, GovernanceEnums.LinkStatus.ACTIVE.name());
                boolean ok = members.stream().anyMatch(m ->
                        GovernanceEnums.AssignmentTargetType.FACILITY.name().equals(m.getMemberTargetType())
                                && facilityIdStr.equals(m.getMemberTargetId())
                                && GovernanceDates.isActiveForDate(m.getStartDate(), m.getEndDate(), today));
                if (ok) {
                    evidence.add("GROUP_LIKE:" + tt + ":" + a.getId());
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private boolean isGroupLike(String targetType) {
        return GovernanceEnums.AssignmentTargetType.MULTI_SITE_GROUP.name().equals(targetType)
                || GovernanceEnums.AssignmentTargetType.PROGRAM.name().equals(targetType)
                || GovernanceEnums.AssignmentTargetType.VIRTUAL_HUB.name().equals(targetType)
                || GovernanceEnums.AssignmentTargetType.SERVICE_NETWORK.name().equals(targetType);
    }

    public record ScopeFacilityResult(boolean allowed, String reason, List<String> evidence) {}
}
