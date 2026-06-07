package zw.gov.mohcc.impilo.madi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.madi.domain.HaemovigilanceCaseStatus;
import zw.gov.mohcc.impilo.madi.events.MadiEventEmitter;
import zw.gov.mohcc.impilo.madi.integration.NhumeIntegration;
import zw.gov.mohcc.impilo.madi.persistence.entity.AdverseTransfusionReactionEntity;
import zw.gov.mohcc.impilo.madi.persistence.entity.HaemovigilanceCaseEntity;
import zw.gov.mohcc.impilo.madi.persistence.repository.AdverseTransfusionReactionRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.HaemovigilanceCaseRepository;
import zw.gov.mohcc.impilo.madi.persistence.repository.TransfusionEpisodeRepository;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class HaemovigilanceService {

    private final AdverseTransfusionReactionRepository reactionRepository;
    private final HaemovigilanceCaseRepository caseRepository;
    private final TransfusionEpisodeRepository episodeRepository;
    private final NhumeIntegration nhumeIntegration;
    private final MadiEventEmitter eventEmitter;

    public HaemovigilanceService(AdverseTransfusionReactionRepository reactionRepository,
                                 HaemovigilanceCaseRepository caseRepository,
                                 TransfusionEpisodeRepository episodeRepository,
                                 NhumeIntegration nhumeIntegration,
                                 MadiEventEmitter eventEmitter) {
        this.reactionRepository = reactionRepository;
        this.caseRepository = caseRepository;
        this.episodeRepository = episodeRepository;
        this.nhumeIntegration = nhumeIntegration;
        this.eventEmitter = eventEmitter;
    }

    @Transactional
    public AdverseTransfusionReactionEntity reportReaction(UUID tenantId, UUID episodeId,
                                                           String reactionType, String severity,
                                                           String description, String reportedBy,
                                                           UUID facilityId) {
        episodeRepository.findByEpisodeIdAndTenantId(episodeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Transfusion episode not found"));
        AdverseTransfusionReactionEntity reaction = new AdverseTransfusionReactionEntity();
        reaction.setTenantId(tenantId);
        reaction.setEpisodeId(episodeId);
        reaction.setReactionType(reactionType);
        reaction.setSeverity(severity);
        reaction.setDescription(description);
        reaction.setReportedBy(reportedBy);
        reaction.setFacilityId(facilityId);
        reaction.setStatus("REPORTED");
        reaction.setReportedAt(OffsetDateTime.now());
        AdverseTransfusionReactionEntity saved = reactionRepository.save(reaction);
        eventEmitter.emit("HAEMOVIGILANCE", saved.getReactionId().toString(), "ADVERSE_REACTION_REPORTED",
                "HAEMOVIGILANCE", saved.getReactionId().toString(),
                Map.of("reactionType", reactionType, "severity", severity), tenantId);
        nhumeIntegration.notifyAdverseEvent(saved.getReactionId().toString(), reactionType, severity);
        return saved;
    }

    @Transactional
    public HaemovigilanceCaseEntity openCase(UUID tenantId, UUID reactionId, String assignedTo, UUID facilityId) {
        AdverseTransfusionReactionEntity reaction = reactionRepository.findByReactionIdAndTenantId(reactionId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Reaction not found"));
        HaemovigilanceCaseEntity haemoCase = new HaemovigilanceCaseEntity();
        haemoCase.setTenantId(tenantId);
        haemoCase.setReactionId(reactionId);
        haemoCase.setAssignedTo(assignedTo);
        haemoCase.setFacilityId(facilityId);
        haemoCase.setStatus(HaemovigilanceCaseStatus.OPEN.name());
        haemoCase.setOpenedAt(OffsetDateTime.now());
        haemoCase.setUpdatedAt(OffsetDateTime.now());
        HaemovigilanceCaseEntity saved = caseRepository.save(haemoCase);
        reaction.setStatus("UNDER_INVESTIGATION");
        reactionRepository.save(reaction);
        eventEmitter.emit("HAEMOVIGILANCE", saved.getCaseId().toString(), "CASE_OPENED", "HAEMOVIGILANCE",
                saved.getCaseId().toString(), Map.of("reactionId", reactionId.toString()), tenantId);
        return saved;
    }

    @Transactional
    public HaemovigilanceCaseEntity investigate(UUID tenantId, UUID caseId, String notes, String assignedTo) {
        HaemovigilanceCaseEntity haemoCase = requireCase(tenantId, caseId);
        haemoCase.setStatus(HaemovigilanceCaseStatus.INVESTIGATING.name());
        haemoCase.setInvestigationNotes(notes);
        haemoCase.setAssignedTo(assignedTo);
        haemoCase.setUpdatedAt(OffsetDateTime.now());
        return caseRepository.save(haemoCase);
    }

    @Transactional
    public HaemovigilanceCaseEntity closeCase(UUID tenantId, UUID caseId, String notes) {
        HaemovigilanceCaseEntity haemoCase = requireCase(tenantId, caseId);
        haemoCase.setStatus(HaemovigilanceCaseStatus.CLOSED.name());
        haemoCase.setInvestigationNotes(notes);
        haemoCase.setClosedAt(OffsetDateTime.now());
        haemoCase.setUpdatedAt(OffsetDateTime.now());
        HaemovigilanceCaseEntity saved = caseRepository.save(haemoCase);
        eventEmitter.emit("HAEMOVIGILANCE", caseId.toString(), "CASE_CLOSED", "HAEMOVIGILANCE",
                caseId.toString(), Map.of(), tenantId);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> nationalDashboard(UUID tenantId) {
        List<AdverseTransfusionReactionEntity> reactions =
                reactionRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
        List<HaemovigilanceCaseEntity> cases = caseRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);

        Map<String, Long> reactionsByType = reactions.stream()
                .collect(Collectors.groupingBy(AdverseTransfusionReactionEntity::getReactionType, Collectors.counting()));
        Map<String, Long> reactionsBySeverity = reactions.stream()
                .filter(r -> r.getSeverity() != null)
                .collect(Collectors.groupingBy(AdverseTransfusionReactionEntity::getSeverity, Collectors.counting()));
        Map<String, Long> casesByStatus = cases.stream()
                .collect(Collectors.groupingBy(HaemovigilanceCaseEntity::getStatus, Collectors.counting()));
        Map<String, Long> casesByFacility = reactions.stream()
                .filter(r -> r.getFacilityId() != null)
                .collect(Collectors.groupingBy(r -> r.getFacilityId().toString(), Collectors.counting()));

        long openCases = cases.stream()
                .filter(c -> !HaemovigilanceCaseStatus.CLOSED.name().equals(c.getStatus()))
                .count();
        OffsetDateTime thirtyDaysAgo = OffsetDateTime.now().minusDays(30);
        long closedCases30d = cases.stream()
                .filter(c -> HaemovigilanceCaseStatus.CLOSED.name().equals(c.getStatus()))
                .filter(c -> c.getClosedAt() != null && c.getClosedAt().isAfter(thirtyDaysAgo))
                .count();

        OffsetDateTime now = OffsetDateTime.now();
        long recentReactions = reactions.stream()
                .filter(r -> r.getReportedAt() != null && r.getReportedAt().isAfter(now.minusDays(30)))
                .count();
        long priorReactions = reactions.stream()
                .filter(r -> r.getReportedAt() != null
                        && r.getReportedAt().isAfter(now.minusDays(60))
                        && r.getReportedAt().isBefore(now.minusDays(30)))
                .count();
        String trendIndicator = recentReactions > priorReactions ? "UP"
                : recentReactions < priorReactions ? "DOWN" : "STABLE";

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("reactionsByType", reactionsByType);
        dashboard.put("reactionsBySeverity", reactionsBySeverity);
        dashboard.put("casesByStatus", casesByStatus);
        dashboard.put("casesByFacility", casesByFacility);
        dashboard.put("openCases", openCases);
        dashboard.put("closedCases30d", closedCases30d);
        dashboard.put("trendIndicator", trendIndicator);
        return dashboard;
    }

    private HaemovigilanceCaseEntity requireCase(UUID tenantId, UUID caseId) {
        return caseRepository.findByCaseIdAndTenantId(caseId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Haemovigilance case not found"));
    }
}
