package zw.gov.mohcc.impilo.coverage.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentRequest;
import zw.gov.mohcc.impilo.coverage.api.dto.SubsidyEnrolmentResponse;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyEnrolmentEntity;
import zw.gov.mohcc.impilo.coverage.domain.SubsidyProgramEntity;
import zw.gov.mohcc.impilo.coverage.integration.HealthWorkerRecognitionClient;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyEnrolmentRepository;
import zw.gov.mohcc.impilo.coverage.repository.SubsidyProgramRepository;

import java.util.List;
import java.util.UUID;

/**
 * Health-worker recognition subsidy: explicit self opt-in + automatic
 * suspension when recognition lapses.
 *
 * <p>OPT-IN: requires a positive server-side recognition check (fail-closed —
 * no registry answer, no enrolment). Records
 * {@code enrolled_by = SELF_OPT_IN:RECOGNITION:<class>} and
 * {@code exemption_category = HEALTH_WORKER} on the unified ledgered model,
 * so COSTA billing-category resolution picks it up like any other exemption
 * enrolment. The programme itself must be ACTIVE (seeded DRAFT; a tenant must
 * activate it) or the opt-in is rejected by the enrolment service.</p>
 *
 * <p>SUSPENSION SWEEP (design choice: scheduled POLL — no VARAPI/Vashandi
 * lifecycle event consumer exists in coverage today, so polling recognition
 * is the grounded mechanism; an event-driven upgrade can replace the sweep
 * later without schema change): re-verifies every ACTIVE recognition
 * enrolment and SUSPENDs those with a DEFINITE negative. A registry outage
 * (sources unreachable) never suspends anyone.</p>
 */
@Service
public class HealthWorkerSubsidyService {

    private static final Logger log = LoggerFactory.getLogger(HealthWorkerSubsidyService.class);

    public static final String ENROLLED_BY_PREFIX = "SELF_OPT_IN:RECOGNITION";
    public static final String HEALTH_WORKER_CATEGORY = "HEALTH_WORKER";

    private final SubsidyProgramRepository programRepository;
    private final SubsidyEnrolmentRepository enrolmentRepository;
    private final SubsidyEnrolmentService enrolmentService;
    private final HealthWorkerRecognitionClient recognitionClient;

    public HealthWorkerSubsidyService(SubsidyProgramRepository programRepository,
                                      SubsidyEnrolmentRepository enrolmentRepository,
                                      SubsidyEnrolmentService enrolmentService,
                                      HealthWorkerRecognitionClient recognitionClient) {
        this.programRepository = programRepository;
        this.enrolmentRepository = enrolmentRepository;
        this.enrolmentService = enrolmentService;
        this.recognitionClient = recognitionClient;
    }

    /**
     * Explicit self opt-in. The health id must resolve as a recognised health
     * worker RIGHT NOW; otherwise 403 with a generic message (no detail about
     * whether the person exists in any registry — enumeration resistance is
     * preserved end-to-end because the upstreams are generic too).
     */
    @Transactional
    public SubsidyEnrolmentResponse optIn(UUID tenantId, UUID programId, String healthId) {
        if (healthId == null || healthId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "member health id is required");
        }
        HealthWorkerRecognitionClient.Recognition recognition =
                recognitionClient.resolve(tenantId, healthId);
        if (!recognition.recognised()) {
            // Same message whether unknown, lapsed, or unreachable: fail-closed, generic.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Health worker recognition could not be confirmed");
        }
        SubsidyEnrolmentRequest request = new SubsidyEnrolmentRequest(
                programId, null, healthId.trim(), null, null,
                ENROLLED_BY_PREFIX + ":" + recognition.recognitionClass(),
                HEALTH_WORKER_CATEGORY);
        SubsidyEnrolmentResponse response = enrolmentService.enrol(tenantId, request);
        log.info("Health-worker subsidy opt-in enrolled (tenant={}, class={})",
                tenantId, recognition.recognitionClass());
        return response;
    }

    /**
     * Scheduled suspension sweep: licence lapse / employment end revokes the
     * advantage. Runs daily by default ({@code coverage.health-worker.sweep-cron}).
     */
    @Scheduled(cron = "${coverage.health-worker.sweep-cron:0 20 2 * * *}")
    @Transactional
    public void suspendLapsedRecognitions() {
        int suspended = sweepOnce();
        if (suspended > 0) {
            log.info("Health-worker recognition sweep suspended {} enrolment(s)", suspended);
        }
    }

    /** One sweep pass; returns the number of enrolments suspended. */
    @Transactional
    public int sweepOnce() {
        List<SubsidyEnrolmentEntity> active = enrolmentRepository
                .findByStatusAndEnrolledByStartingWith("ACTIVE", ENROLLED_BY_PREFIX);
        int suspended = 0;
        for (SubsidyEnrolmentEntity enrolment : active) {
            HealthWorkerRecognitionClient.Recognition recognition =
                    recognitionClient.resolve(enrolment.getTenantId(), enrolment.getMemberCpid());
            if (recognition.recognised()) {
                continue;
            }
            if (!recognition.sourcesReachable()) {
                // Outage is NOT a lapse — never suspend on "could not verify".
                log.debug("Sweep skipped enrolment {}: recognition sources unreachable", enrolment.getId());
                continue;
            }
            enrolment.setStatus("SUSPENDED");
            enrolmentRepository.save(enrolment);
            suspended++;
            log.info("Suspended health-worker subsidy enrolment {} (recognition lapsed)", enrolment.getId());
        }
        return suspended;
    }

    /** Programme lookup used by the opt-in endpoint for a friendly 404. */
    @Transactional(readOnly = true)
    public SubsidyProgramEntity requireProgram(UUID tenantId, UUID programId) {
        return programRepository.findByIdAndTenantId(programId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Subsidy programme not found: " + programId));
    }
}
