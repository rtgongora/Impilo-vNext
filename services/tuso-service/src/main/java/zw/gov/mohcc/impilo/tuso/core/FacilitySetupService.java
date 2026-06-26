package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySetupStateEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySetupStateRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityUnitRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.ServicePointRepository;

import java.util.List;

/**
 * Orchestrates the facility-mode setup wizard:
 * dept -> service-point -> queue -> workflow -> workforce -> OROS-routing ->
 * Khuluma-channel -> Fundo-readiness -> go-live.
 *
 * <p>Facility config persists in TUSO (facility SoR); never in the experience-bff.
 * Each step toggle is audited via the outbox. Honest stubs are used where a
 * downstream isn't ready (e.g. Fundo C5 readiness is only set true by an explicit
 * confirmation flow — never auto-faked).</p>
 *
 * <p>Authz: callers route through TSHEPO ext_authz; policy {@code FACILITY-SETUP}
 * (spec-only, queued to track P) gates who may advance setup.</p>
 */
@Service
public class FacilitySetupService {

    private static final Logger log = LoggerFactory.getLogger(FacilitySetupService.class);

    private final FacilityRepository facilityRepository;
    private final FacilitySetupStateRepository setupStateRepository;
    private final FacilityUnitRepository facilityUnitRepository;
    private final ServicePointRepository servicePointRepository;
    private final EventOutboxRepository outboxRepository;

    public FacilitySetupService(FacilityRepository facilityRepository,
                                FacilitySetupStateRepository setupStateRepository,
                                FacilityUnitRepository facilityUnitRepository,
                                ServicePointRepository servicePointRepository,
                                EventOutboxRepository outboxRepository) {
        this.facilityRepository = facilityRepository;
        this.setupStateRepository = setupStateRepository;
        this.facilityUnitRepository = facilityUnitRepository;
        this.servicePointRepository = servicePointRepository;
        this.outboxRepository = outboxRepository;
    }

    /** Setup-wizard steps. Order matters for the {@link #nextStep} hint. */
    public enum SetupStep {
        DEPARTMENTS,
        SERVICE_POINTS,
        QUEUES,
        WORKFLOWS,
        WORKFORCE,
        OROS_ROUTING,
        KHULUMA_CHANNELS,
        FUNDO_READINESS,
        GO_LIVE
    }

    /**
     * Fetch the setup state, lazily creating it on first read. Reconciles a few
     * derived flags (departments / service-points) from live TUSO entities so the
     * wizard reflects reality rather than only the persisted toggle.
     */
    @Transactional
    public FacilitySetupStateEntity getOrCreateState(TrustContext ctx, Long facilityId) {
        FacilityEntity facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));

        FacilitySetupStateEntity state = setupStateRepository.findByFacilityId(facilityId)
                .orElseGet(() -> {
                    FacilitySetupStateEntity s = new FacilitySetupStateEntity();
                    s.setFacilityId(facilityId);
                    s.setTenantId(facility.getTenantId());
                    s.setCreatedBy(actor(ctx));
                    s.setUpdatedBy(actor(ctx));
                    return setupStateRepository.save(s);
                });

        // Reconcile derived flags from live entities (honest reflection, no faking).
        boolean hasDepartments = !facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId).isEmpty();
        boolean hasServicePoints = servicePointRepository.countByFacilityIdAndActiveTrue(facilityId) > 0;
        boolean changed = false;
        if (hasDepartments && !state.isDepartmentsConfigured()) {
            state.setDepartmentsConfigured(true);
            changed = true;
        }
        if (hasServicePoints && !state.isServicePointsConfigured()) {
            state.setServicePointsConfigured(true);
            changed = true;
        }
        if (changed) {
            state.setUpdatedBy(actor(ctx));
            setupStateRepository.save(state);
        }
        return state;
    }

    /**
     * Advance (or set) a single setup step. {@code GO_LIVE} can only be set true once
     * every prerequisite step is complete; the call fails-loud otherwise (no fake go-live).
     */
    @Transactional
    public FacilitySetupStateEntity advanceStep(TrustContext ctx, Long facilityId,
                                                SetupStep step, boolean complete) {
        FacilitySetupStateEntity state = getOrCreateState(ctx, facilityId);

        switch (step) {
            case DEPARTMENTS -> state.setDepartmentsConfigured(complete);
            case SERVICE_POINTS -> state.setServicePointsConfigured(complete);
            case QUEUES -> state.setQueuesConfigured(complete);
            case WORKFLOWS -> state.setWorkflowsConfigured(complete);
            case WORKFORCE -> state.setWorkforceLinked(complete);
            case OROS_ROUTING -> state.setOrosRoutingConfigured(complete);
            case KHULUMA_CHANNELS -> state.setKhulumaChannelsConfigured(complete);
            case FUNDO_READINESS -> state.setFundoReady(complete);
            case GO_LIVE -> {
                if (complete && !allPrerequisitesMet(state)) {
                    throw new IllegalStateException(
                            "Cannot go live: setup prerequisites incomplete. Next required step: "
                                    + nextStep(state));
                }
                state.setGoLive(complete);
                state.setGoLiveAt(complete ? java.time.Instant.now() : null);
            }
        }
        state.setUpdatedBy(actor(ctx));
        FacilitySetupStateEntity saved = setupStateRepository.save(state);

        publishEvent(facility(ctx, facilityId), "FACILITY_SETUP_STEP_CHANGED",
                String.format("{\"facilityId\":%d,\"step\":\"%s\",\"complete\":%b}",
                        facilityId, step, complete));
        log.info("Facility {} setup step {} -> {} (actor={})", facilityId, step, complete, actor(ctx));
        return saved;
    }

    /** True when every prerequisite step (everything except go-live) is complete. */
    public boolean allPrerequisitesMet(FacilitySetupStateEntity s) {
        return s.isDepartmentsConfigured()
                && s.isServicePointsConfigured()
                && s.isQueuesConfigured()
                && s.isWorkflowsConfigured()
                && s.isWorkforceLinked()
                && s.isOrosRoutingConfigured()
                && s.isKhulumaChannelsConfigured()
                && s.isFundoReady();
    }

    /** The next incomplete step in wizard order, or {@code null} if ready for go-live. */
    public SetupStep nextStep(FacilitySetupStateEntity s) {
        if (!s.isDepartmentsConfigured()) return SetupStep.DEPARTMENTS;
        if (!s.isServicePointsConfigured()) return SetupStep.SERVICE_POINTS;
        if (!s.isQueuesConfigured()) return SetupStep.QUEUES;
        if (!s.isWorkflowsConfigured()) return SetupStep.WORKFLOWS;
        if (!s.isWorkforceLinked()) return SetupStep.WORKFORCE;
        if (!s.isOrosRoutingConfigured()) return SetupStep.OROS_ROUTING;
        if (!s.isKhulumaChannelsConfigured()) return SetupStep.KHULUMA_CHANNELS;
        if (!s.isFundoReady()) return SetupStep.FUNDO_READINESS;
        if (!s.isGoLive()) return SetupStep.GO_LIVE;
        return null;
    }

    public List<FacilityUnitEntity> departments(Long facilityId) {
        return facilityUnitRepository.findByFacilityIdOrderByCreatedAtAsc(facilityId);
    }

    private FacilityEntity facility(TrustContext ctx, Long facilityId) {
        return facilityRepository.findById(facilityId)
                .orElseThrow(() -> new IllegalArgumentException("Facility not found: " + facilityId));
    }

    private void publishEvent(FacilityEntity facility, String eventType, String payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY_SETUP");
        event.setAggregateId(String.valueOf(facility.getId()));
        event.setEventType(eventType);
        event.setPayload(payload);
        outboxRepository.save(event);
    }

    private static String actor(TrustContext ctx) {
        return ctx != null && ctx.actorId() != null ? ctx.actorId() : "system";
    }
}
