package zw.gov.mohcc.impilo.vashandi.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceAssignmentEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.entity.WorkforceProfileEntity;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceAssignmentRepository;
import zw.gov.mohcc.impilo.vashandi.persistence.repository.WorkforceProfileRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Answers "is this Health ID a recognised health worker with a current
 * assignment?" for downstream advantage surfaces (badge, COSTA fee exemption,
 * coverage subsidy opt-in).
 *
 * <p>Recognised = a workforce profile exists for the health id, its lifecycle
 * status is not a disqualified terminal state, and it holds at least one
 * active/approved assignment whose end date has not passed.</p>
 *
 * <p>ENUMERATION RESISTANCE (same doctrine as the C2 session-context
 * read-model): unknown health ids and disqualified profiles yield the
 * IDENTICAL generic {@code recognised=false} result — never a 404, never
 * leaked detail. Recognition is administrative/financial only; it MUST NOT
 * drive clinical-triage priority.</p>
 */
@Service
public class WorkforceRecognitionService {

    /** Profile lifecycle states that can never be recognised. */
    private static final Set<String> DISQUALIFIED_PROFILE_STATUSES =
            Set.of("offboarded", "suspended", "terminated", "deceased");

    /** Assignment statuses that count as a current appointment. */
    private static final List<String> ACTIVE_ASSIGNMENT_STATUSES = List.of("active", "approved");

    private final WorkforceProfileRepository profileRepository;
    private final WorkforceAssignmentRepository assignmentRepository;

    public WorkforceRecognitionService(WorkforceProfileRepository profileRepository,
                                       WorkforceAssignmentRepository assignmentRepository) {
        this.profileRepository = profileRepository;
        this.assignmentRepository = assignmentRepository;
    }

    /** Generic recognition result — negatives carry no detail at all. */
    public record RecognitionResult(boolean recognised, String cadre, String profession) {
        public static RecognitionResult notRecognised() {
            return new RecognitionResult(false, null, null);
        }
    }

    @Transactional(readOnly = true)
    public RecognitionResult recognitionByHealthId(UUID tenantId, String healthId) {
        if (healthId == null || healthId.isBlank()) {
            return RecognitionResult.notRecognised();
        }
        Optional<WorkforceProfileEntity> profileOpt =
                profileRepository.findByTenantIdAndHealthId(tenantId, healthId.trim());
        if (profileOpt.isEmpty()) {
            return RecognitionResult.notRecognised();
        }
        WorkforceProfileEntity profile = profileOpt.get();
        String status = profile.getCurrentStatus() == null
                ? "" : profile.getCurrentStatus().toLowerCase(Locale.ROOT);
        if (DISQUALIFIED_PROFILE_STATUSES.contains(status)) {
            return RecognitionResult.notRecognised();
        }
        boolean hasCurrentAssignment = assignmentRepository
                .findByTenantIdAndWorkforceProfileIdAndStatusIn(
                        tenantId, profile.getId(), ACTIVE_ASSIGNMENT_STATUSES)
                .stream()
                .anyMatch(WorkforceRecognitionService::isCurrent);
        if (!hasCurrentAssignment) {
            return RecognitionResult.notRecognised();
        }
        return new RecognitionResult(true, profile.getCadre(), profile.getProfession());
    }

    private static boolean isCurrent(WorkforceAssignmentEntity a) {
        LocalDate today = LocalDate.now();
        return a.getEndDate() == null || !a.getEndDate().isBefore(today);
    }
}
