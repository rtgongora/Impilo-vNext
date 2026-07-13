package zw.gov.mohcc.impilo.tuso.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityFeeDtos.ApplicationFeeView;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationState;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryApplicationTypeEntity;
import zw.gov.mohcc.impilo.tuso.integration.MushexPaymentIntentClient;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityApplicationRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryApplicationTypeRepository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Regulatory fee orchestration: resolve an application's fee against the governed
 * schedule, drive the {@code AWAITING_FEE} gate, and emit the {@code fee_due}
 * event COSTA consumes to mint the charge.
 *
 * <p>The gate is feeState-authoritative (not workflow-state-authoritative) because
 * the application workflow states are set imperatively elsewhere. It enforces ONLY
 * when a configured amount exists; an unconfigured fee is recorded NOT_CONFIGURED
 * and never silently skipped.</p>
 */
@Service
public class FacilityFeeService {

    private static final Logger log = LoggerFactory.getLogger(FacilityFeeService.class);

    private final RegulatoryFeeScheduleService feeScheduleService;
    private final RegulatoryApplicationTypeRepository applicationTypeRepository;
    private final FacilityRegulatoryProfileRepository profileRepository;
    private final FacilityApplicationRepository applicationRepository;
    private final EventOutboxRepository outboxRepository;
    private final MushexPaymentIntentClient mushexClient;

    public FacilityFeeService(RegulatoryFeeScheduleService feeScheduleService,
                              RegulatoryApplicationTypeRepository applicationTypeRepository,
                              FacilityRegulatoryProfileRepository profileRepository,
                              FacilityApplicationRepository applicationRepository,
                              EventOutboxRepository outboxRepository,
                              MushexPaymentIntentClient mushexClient) {
        this.feeScheduleService = feeScheduleService;
        this.applicationTypeRepository = applicationTypeRepository;
        this.profileRepository = profileRepository;
        this.applicationRepository = applicationRepository;
        this.outboxRepository = outboxRepository;
        this.mushexClient = mushexClient;
    }

    /**
     * Mint (or return the existing) MusheX payment intent for an application's DUE
     * fee. Idempotent: a payment_reference already present is returned unchanged;
     * the amount is priced from the pinned schedule row.
     */
    @Transactional
    public FacilityApplicationEntity createFeePaymentIntent(TrustContext ctx, FacilityApplicationEntity application) {
        if (!"DUE".equals(application.getFeeState())) {
            throw new IllegalArgumentException("No fee is DUE for application " + application.getApplicationId()
                    + " (fee_state=" + application.getFeeState() + ")");
        }
        if (application.getPaymentReference() != null && !application.getPaymentReference().isBlank()) {
            return application; // idempotent — intent already minted
        }
        RegulatoryFeeScheduleEntity pinned = application.getFeeScheduleRef() != null
                ? feeScheduleService.require(application.getFeeScheduleRef()) : null;
        BigDecimal amount = pinned != null ? pinned.getAmount() : null;
        String currency = pinned != null ? pinned.getCurrency() : null;
        if (amount == null) {
            throw new IllegalArgumentException("Fee amount is not configured for application "
                    + application.getApplicationId() + " — cannot mint a payment intent");
        }
        String intentId = mushexClient.createPaymentIntent(
                application.getApplicationId().toString(), amount, currency,
                "HPA_FACILITY_FEE:" + application.getApplicationId());
        if (intentId != null) {
            application.setPaymentReference(intentId);
            applicationRepository.save(application);
            log.info("HPA fee intent {} minted for application {}", intentId, application.getApplicationId());
        }
        return application;
    }

    /**
     * Called on submit. Resolves the fee and sets fee state:
     * NOT_REQUIRED (type carries no fee), NOT_CONFIGURED (fee required but no ACTIVE
     * amount — proceeds, surfaced) or DUE (configured — pins the schedule row, moves
     * to AWAITING_FEE, emits fee_due for COSTA).
     */
    @Transactional
    public void initiateFee(TrustContext ctx, FacilityApplicationEntity application) {
        if (!isFeeRequired(application)) {
            application.setFeeState("NOT_REQUIRED");
            applicationRepository.save(application);
            return;
        }
        String classCode = facilityClass(application);
        RegulatoryFeeScheduleService.FeeResolution fee =
                feeScheduleService.resolve(application.getCatalogueCode(), classCode);
        switch (fee.kind()) {
            case CONFIGURED -> {
                application.setFeeState("DUE");
                application.setFeeScheduleRef(fee.feeId());
                application.setCurrentWorkflowState(FacilityApplicationState.AWAITING_FEE);
                applicationRepository.save(application);
                emitFeeDue(ctx, application, fee, classCode);
                log.info("Fee DUE for application {}: {} {} ({})",
                        application.getApplicationId(), fee.amount(), fee.currency(), fee.feeCode());
            }
            case NOT_CONFIGURED -> {
                // Honest: fee required but no configured amount (SI 78 not yet loaded).
                application.setFeeState("NOT_CONFIGURED");
                applicationRepository.save(application);
                log.info("Fee required but NOT_CONFIGURED for application {} (catalogue {}); proceeding, no gate",
                        application.getApplicationId(), application.getCatalogueCode());
            }
            default -> {
                application.setFeeState("NOT_REQUIRED");
                applicationRepository.save(application);
            }
        }
    }

    /** Human-readable blocker when the fee gate should hold READY_FOR_INSPECTION; null when clear. */
    @Transactional(readOnly = true)
    public String feeBlocker(FacilityApplicationEntity application) {
        if (isFeeRequired(application) && "DUE".equals(application.getFeeState())) {
            return "Application " + application.getApplicationId() + " has an outstanding registration fee "
                    + "(fee_state=DUE); it must be PAID or WAIVED before inspection.";
        }
        return null;
    }

    /** Actor types permitted to waive a fee. */
    static final java.util.Set<String> FEE_WAIVER_ROLES =
            java.util.Set.of("HPA_REGISTRAR", "HPA_ADMIN", "SYSTEM_ADMIN");

    /**
     * Waive a DUE fee: records WAIVED + waiver provenance, opens the gate, and
     * emits fee_waived (COSTA voids the charge). A late PAID after waiver is
     * ignored loudly by markPaid's idempotency.
     */
    @Transactional
    public FacilityApplicationEntity waiveFee(TrustContext ctx, FacilityApplicationEntity application, String reason) {
        String actorType = ctx.actorType();
        if (actorType != null && !actorType.isBlank() && !FEE_WAIVER_ROLES.contains(actorType)) {
            throw new SecurityException("Actor type " + actorType + " cannot waive a registration fee");
        }
        if (!"DUE".equals(application.getFeeState())) {
            throw new IllegalArgumentException("Only a DUE fee can be waived (fee_state="
                    + application.getFeeState() + ")");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A waiver reason is required");
        }
        application.setFeeState("WAIVED");
        application.setFeeWaivedBy(ctx.actorId());
        application.setFeeWaivedAt(java.time.Instant.now());
        application.setFeeWaiverReason(reason);
        if (application.getCurrentWorkflowState() == FacilityApplicationState.AWAITING_FEE) {
            application.setCurrentWorkflowState(FacilityApplicationState.SUBMITTED);
        }
        applicationRepository.save(application);
        emit(ctx, application, "tuso.facility.application.fee_waived", Map.of(
                "applicationId", application.getApplicationId().toString(),
                "tenantId", ctx.tenantId().toString(),
                "waivedBy", ctx.actorId() != null ? ctx.actorId() : "",
                "reason", reason));
        log.info("Fee WAIVED for application {} by {}: {}", application.getApplicationId(), ctx.actorId(), reason);
        return application;
    }

    /** Link the COSTA charge id onto the application (from costa.charge.created). Idempotent. */
    @Transactional
    public void linkChargeReference(String applicationId, String chargeId) {
        if (applicationId == null || chargeId == null) return;
        try {
            applicationRepository.findById(UUID.fromString(applicationId)).ifPresent(a -> {
                if (a.getFeeReference() == null || a.getFeeReference().isBlank()) {
                    a.setFeeReference(chargeId);
                    applicationRepository.save(a);
                    log.info("Linked COSTA charge {} to application {}", chargeId, applicationId);
                }
            });
        } catch (IllegalArgumentException ignored) {
            // applicationId not a UUID — not an HPA fee charge; ignore.
        }
    }

    /** Applied by the payment consumer (PAID) and the waiver path (WAIVED). Idempotent. */
    @Transactional
    public FacilityApplicationEntity markPaid(TrustContext ctx, FacilityApplicationEntity application,
                                              String mushexIntentId) {
        if ("PAID".equals(application.getFeeState())) {
            return application;
        }
        application.setFeeState("PAID");
        if (mushexIntentId != null) application.setPaymentReference(mushexIntentId);
        if (application.getCurrentWorkflowState() == FacilityApplicationState.AWAITING_FEE) {
            application.setCurrentWorkflowState(FacilityApplicationState.SUBMITTED);
        }
        applicationRepository.save(application);
        emit(ctx, application, "tuso.facility.application.fee_paid", Map.of(
                "applicationId", application.getApplicationId().toString(),
                "tenantId", ctx.tenantId().toString(),
                "mushexIntentId", mushexIntentId != null ? mushexIntentId : ""));
        log.info("Fee PAID for application {} (intent {})", application.getApplicationId(), mushexIntentId);
        return application;
    }

    @Transactional(readOnly = true)
    public ApplicationFeeView getApplicationFee(FacilityApplicationEntity application) {
        boolean required = isFeeRequired(application);
        RegulatoryFeeScheduleService.FeeResolution fee = required
                ? feeScheduleService.resolve(application.getCatalogueCode(), facilityClass(application))
                : RegulatoryFeeScheduleService.FeeResolution.notRequired();
        // If a schedule row was pinned at submit, price from it (amount-drift guard).
        java.math.BigDecimal amount = fee.amount();
        String currency = fee.currency();
        String sourceInstrument = fee.sourceInstrument();
        if (application.getFeeScheduleRef() != null) {
            var pinned = feeScheduleService.require(application.getFeeScheduleRef());
            amount = pinned.getAmount();
            currency = pinned.getCurrency();
            sourceInstrument = pinned.getSourceInstrument();
        }
        return new ApplicationFeeView(
                application.getApplicationId(), application.getCatalogueCode(), required,
                application.getFeeState(), fee.feeCode(), amount, currency, sourceInstrument,
                application.getFeeScheduleRef(), application.getFeeReference(), application.getPaymentReference(),
                application.getFeeWaivedBy(), application.getFeeWaivedAt(), application.getFeeWaiverReason(),
                feeBlocker(application) != null);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private boolean isFeeRequired(FacilityApplicationEntity application) {
        String code = application.getCatalogueCode();
        if (code == null || code.isBlank()) return false;
        return applicationTypeRepository.findById(code)
                .map(RegulatoryApplicationTypeEntity::isFeeRequired)
                .orElse(false);
    }

    private String facilityClass(FacilityApplicationEntity application) {
        if (application.getFacility() == null) return null;
        Optional<String> cls = profileRepository.findByFacilityId(application.getFacility().getId())
                .map(p -> p.getHpaClass());
        return cls.filter(c -> c != null && c.length() == 1).orElse(null); // A/B/C only
    }

    private void emitFeeDue(TrustContext ctx, FacilityApplicationEntity application,
                            RegulatoryFeeScheduleService.FeeResolution fee, String classCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("applicationId", application.getApplicationId().toString());
        payload.put("applicationNumber", application.getApplicationNumber());
        payload.put("facilityId", application.getFacility() != null ? application.getFacility().getId() : null);
        payload.put("tenantId", ctx.tenantId().toString());
        payload.put("catalogueCode", application.getCatalogueCode());
        payload.put("classCode", classCode);
        payload.put("feeCode", fee.feeCode());
        payload.put("feeScheduleId", fee.feeId() != null ? fee.feeId().toString() : null);
        payload.put("amount", fee.amount() != null ? fee.amount().toPlainString() : null);
        payload.put("currency", fee.currency());
        emit(ctx, application, "tuso.facility.application.fee_due", payload);
    }

    private void emit(TrustContext ctx, FacilityApplicationEntity application, String eventType,
                      Map<String, Object> payload) {
        EventOutboxEntity event = new EventOutboxEntity();
        event.setAggregateType("FACILITY_APPLICATION_FEE");
        event.setAggregateId(application.getApplicationId().toString());
        event.setEventType(eventType);
        event.setPayload(new LinkedHashMap<>(payload));
        event.setTenantId(ctx.tenantId().toString());
        event.setCorrelationId(ctx.correlationId() != null ? ctx.correlationId().toString() : null);
        event.setProducer("tuso");
        event.setSubjectType("FacilityApplication");
        event.setSubjectId(application.getApplicationId().toString());
        event.setPartitionKey(application.getApplicationId().toString());
        event.setSchemaVersion(1);
        event.setPodId("national-spine");
        event.setIdempotencyKey("tuso:fee:" + eventType + ":" + application.getApplicationId() + ":" + UUID.randomUUID());
        outboxRepository.save(event);
    }

    // Package-visible for the payment consumer to resolve the application by payment_reference.
    @Transactional(readOnly = true)
    public Optional<FacilityApplicationEntity> findByPaymentReference(String mushexIntentId) {
        return applicationRepository.findAll().stream()
                .filter(a -> mushexIntentId != null && mushexIntentId.equals(a.getPaymentReference()))
                .findFirst();
    }

    TrustContext currentCtx() { return TrustContextHolder.require(); }
}
