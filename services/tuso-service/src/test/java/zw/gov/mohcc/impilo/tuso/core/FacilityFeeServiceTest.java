package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityApplicationState;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryApplicationTypeEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityApplicationRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRegulatoryProfileRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.RegulatoryApplicationTypeRepository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityFeeServiceTest {

    @Mock private RegulatoryFeeScheduleService feeScheduleService;
    @Mock private RegulatoryApplicationTypeRepository applicationTypeRepository;
    @Mock private FacilityRegulatoryProfileRepository profileRepository;
    @Mock private FacilityApplicationRepository applicationRepository;
    @Mock private EventOutboxRepository outboxRepository;
    @Mock private zw.gov.mohcc.impilo.tuso.integration.MushexPaymentIntentClient mushexClient;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final TrustContext ctx = new TrustContext(tenantId, "tester", "HPA_ADMIN", "REGULATION", null,
            UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);

    private FacilityFeeService service() {
        lenient().when(applicationRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new FacilityFeeService(feeScheduleService, applicationTypeRepository, profileRepository,
                applicationRepository, outboxRepository, mushexClient);
    }

    private FacilityApplicationEntity app(String catalogueCode) {
        FacilityApplicationEntity a = new FacilityApplicationEntity();
        a.setApplicationId(UUID.randomUUID());
        a.setTenantId(tenantId);
        a.setCatalogueCode(catalogueCode);
        a.setCurrentWorkflowState(FacilityApplicationState.SUBMITTED);
        FacilityEntity f = new FacilityEntity();
        f.setId(1L);
        a.setFacility(f);
        return a;
    }

    private void feeRequired(String code, boolean required) {
        RegulatoryApplicationTypeEntity t = new RegulatoryApplicationTypeEntity();
        t.setFeeRequired(required);
        lenient().when(applicationTypeRepository.findById(code)).thenReturn(Optional.of(t));
    }

    @Test
    void configuredFeeMovesToAwaitingFeeDueAndEmitsFeeDue() {
        feeRequired("INITIAL_REGISTRATION_PRIVATE", true);
        when(feeScheduleService.resolve(eq("INITIAL_REGISTRATION_PRIVATE"), any()))
                .thenReturn(new RegulatoryFeeScheduleService.FeeResolution(
                        RegulatoryFeeScheduleService.Kind.CONFIGURED, UUID.randomUUID(), "FEE_X",
                        new BigDecimal("120.00"), "USD", "SI 78 of 2017"));
        var a = app("INITIAL_REGISTRATION_PRIVATE");

        service().initiateFee(ctx, a);

        assertThat(a.getFeeState()).isEqualTo("DUE");
        assertThat(a.getCurrentWorkflowState()).isEqualTo(FacilityApplicationState.AWAITING_FEE);
        assertThat(a.getFeeScheduleRef()).isNotNull();
        verify(outboxRepository).save(any());   // fee_due emitted
    }

    @Test
    void feeRequiredButNotConfiguredProceedsWithoutAGate() {
        feeRequired("RENEWAL", true);
        when(feeScheduleService.resolve(eq("RENEWAL"), any()))
                .thenReturn(RegulatoryFeeScheduleService.FeeResolution.notConfigured());
        var a = app("RENEWAL");

        service().initiateFee(ctx, a);

        assertThat(a.getFeeState()).isEqualTo("NOT_CONFIGURED");
        assertThat(a.getCurrentWorkflowState()).isEqualTo(FacilityApplicationState.SUBMITTED); // no gate
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void feeNotRequiredIsNotRequired() {
        feeRequired("VOLUNTARY_CLOSURE", false);
        var a = app("VOLUNTARY_CLOSURE");
        service().initiateFee(ctx, a);
        assertThat(a.getFeeState()).isEqualTo("NOT_REQUIRED");
    }

    @Test
    void feeBlockerHoldsInspectionWhileDue() {
        feeRequired("INITIAL_REGISTRATION_PRIVATE", true);
        var a = app("INITIAL_REGISTRATION_PRIVATE");
        a.setFeeState("DUE");
        assertThat(service().feeBlocker(a)).contains("outstanding registration fee");
        a.setFeeState("PAID");
        assertThat(service().feeBlocker(a)).isNull();
    }

    @Test
    void createPaymentIntentMintsOnceAndStoresTheReference() {
        var a = app("INITIAL_REGISTRATION_PRIVATE");
        a.setFeeState("DUE");
        var scheduleId = UUID.randomUUID();
        a.setFeeScheduleRef(scheduleId);
        var pinned = new zw.gov.mohcc.impilo.tuso.persistence.entity.RegulatoryFeeScheduleEntity();
        pinned.setAmount(new BigDecimal("120.00"));
        pinned.setCurrency("USD");
        when(feeScheduleService.require(scheduleId)).thenReturn(pinned);
        when(mushexClient.createPaymentIntent(any(), any(), any(), any())).thenReturn("01INTENT");

        service().createFeePaymentIntent(ctx, a);
        assertThat(a.getPaymentReference()).isEqualTo("01INTENT");

        // Idempotent: a second call returns the same intent without re-minting.
        service().createFeePaymentIntent(ctx, a);
        verify(mushexClient, org.mockito.Mockito.times(1)).createPaymentIntent(any(), any(), any(), any());
    }

    @Test
    void createPaymentIntentRefusesWhenNoFeeIsDue() {
        var a = app("INITIAL_REGISTRATION_PRIVATE");
        a.setFeeState("NOT_CONFIGURED");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service().createFeePaymentIntent(ctx, a));
    }

    @Test
    void waiveFeeRequiresARegulatorAndADueFeeAndAReason() {
        var due = app("INITIAL_REGISTRATION_PRIVATE");
        due.setFeeState("DUE");
        due.setCurrentWorkflowState(FacilityApplicationState.AWAITING_FEE);
        var s = service();

        // non-regulator → 403-equivalent
        var applicant = new TrustContext(tenantId, "x", "FACILITY_APPLICANT", "REGULATION", null,
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
        org.junit.jupiter.api.Assertions.assertThrows(SecurityException.class,
                () -> s.waiveFee(applicant, due, "hardship"));

        // missing reason → 400-equivalent
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> s.waiveFee(ctx, due, "  "));

        // happy path
        s.waiveFee(ctx, due, "public-interest waiver");
        assertThat(due.getFeeState()).isEqualTo("WAIVED");
        assertThat(due.getFeeWaiverReason()).isEqualTo("public-interest waiver");
        assertThat(due.getCurrentWorkflowState()).isEqualTo(FacilityApplicationState.SUBMITTED);
        verify(outboxRepository).save(any());   // fee_waived emitted
    }

    @Test
    void markPaidClearsTheGateAndEmitsFeePaid() {
        var a = app("INITIAL_REGISTRATION_PRIVATE");
        a.setFeeState("DUE");
        a.setCurrentWorkflowState(FacilityApplicationState.AWAITING_FEE);

        service().markPaid(ctx, a, "01INTENT");

        assertThat(a.getFeeState()).isEqualTo("PAID");
        assertThat(a.getPaymentReference()).isEqualTo("01INTENT");
        assertThat(a.getCurrentWorkflowState()).isEqualTo(FacilityApplicationState.SUBMITTED);
        verify(outboxRepository).save(any());   // fee_paid emitted
    }
}
