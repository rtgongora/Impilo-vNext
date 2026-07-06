package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityClaimDtos;
import zw.gov.mohcc.impilo.tuso.persistence.entity.EventOutboxEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacySource;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityLegitimacyStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilitySourceLegitimacyEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.EventOutboxRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityAdminAppointmentRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilitySourceLegitimacyRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityClaimServiceTest {

    @Mock private FacilityRepository facilityRepository;
    @Mock private FacilitySourceLegitimacyRepository legitimacyRepository;
    @Mock private FacilityAdminAppointmentRepository appointmentRepository;
    @Mock private EventOutboxRepository outboxRepository;

    private FacilityClaimService service;

    private final UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID facilityUuid = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private final String actor = "actor-admin-1";
    private FacilityEntity facility;

    @BeforeEach
    void setUp() {
        service = new FacilityClaimService(
                facilityRepository, legitimacyRepository, appointmentRepository, outboxRepository);
        TrustContextHolder.set(new TrustContext(
                tenantId, actor, "ADMIN", "FACILITY_ADMINISTRATION",
                null, UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
        facility = new FacilityEntity();
        facility.setId(42L);
        facility.setFacilityUuid(facilityUuid);
        facility.setTenantId(tenantId);
        facility.setFacilityCode("ZW010125");
        facility.setName("Bangure Clinic");
        facility.setFacilityType("CLINIC");
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void stubFacility() {
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(facility));
    }

    private FacilitySourceLegitimacyEntity legitimacy(boolean allowed) {
        FacilitySourceLegitimacyEntity e = new FacilitySourceLegitimacyEntity();
        e.setFacilityId(facilityUuid);
        e.setSource(FacilityLegitimacySource.MINISTRY_OPERATIONAL);
        e.setStatus(allowed ? FacilityLegitimacyStatus.REGISTERED_CURRENT : FacilityLegitimacyStatus.SUSPENDED);
        e.setAsOf(Instant.parse("2026-01-01T00:00:00Z"));
        e.setAllowedOnPlatform(allowed);
        return e;
    }

    // ── Eligibility gated on FacilitySourceLegitimacy.allowedOnPlatform ──────────

    @Test
    void eligibility_deniedWhenNotAllowedOnPlatform() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(false)));

        FacilityClaimDtos.EligibilityView view = service.checkEligibility(facilityUuid);

        assertThat(view.allowedOnPlatform()).isFalse();
        assertThat(view.claimable()).isFalse();
        assertThat(view.reasons()).anySatisfy(r -> assertThat(r).contains("denies platform operation"));
    }

    @Test
    void eligibility_deniedWhenNoLegitimacyRecorded() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid)).thenReturn(List.of());

        FacilityClaimDtos.EligibilityView view = service.checkEligibility(facilityUuid);

        // Silence never grants.
        assertThat(view.claimable()).isFalse();
        assertThat(view.reasons()).anySatisfy(r -> assertThat(r).contains("No source legitimacy recorded"));
    }

    @Test
    void eligibility_allowedWhenEverySourceAllows() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(true)));
        when(appointmentRepository.findByFacilityUuidOrderByCreatedAtDesc(facilityUuid)).thenReturn(List.of());

        FacilityClaimDtos.EligibilityView view = service.checkEligibility(facilityUuid);

        assertThat(view.claimable()).isTrue();
        assertThat(view.alreadyAdministered()).isFalse();
        assertThat(view.activeAdministratorCount()).isZero();
    }

    // ── Submit claim → PENDING appointment ──────────────────────────────────────

    @Test
    void submitClaim_createsPendingAppointment_andEmitsSubmittedEvent() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(true)));
        when(appointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                facilityUuid, "HID-100", FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(false);
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> {
                    FacilityAdminAppointmentEntity a = inv.getArgument(0);
                    a.setId(7L);
                    return a;
                });

        FacilityClaimDtos.AppointmentView view = service.submitClaim(facilityUuid,
                new FacilityClaimDtos.SubmitClaimRequest("HID-100", null, "doc://evidence/1", null, null, null));

        ArgumentCaptor<FacilityAdminAppointmentEntity> captor =
                ArgumentCaptor.forClass(FacilityAdminAppointmentEntity.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getApprovalState()).isEqualTo(FacilityAdminAppointmentEntity.STATE_PENDING);
        assertThat(captor.getValue().getPersonHealthId()).isEqualTo("HID-100");
        assertThat(captor.getValue().getRole())
                .isEqualTo(FacilityAdminAppointmentEntity.ROLE_FACILITY_ADMINISTRATOR);
        // appointedBy is bound to the session actor, never the request body.
        assertThat(captor.getValue().getAppointedBy()).isEqualTo(actor);
        assertThat(view.approvalState()).isEqualTo("PENDING");

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(FacilityClaimService.EVENT_SUBMITTED);
    }

    @Test
    void submitClaim_rejectedWhenFacilityNotAllowedOnPlatform() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(false)));

        assertThatThrownBy(() -> service.submitClaim(facilityUuid,
                new FacilityClaimDtos.SubmitClaimRequest("HID-100", null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.CONFLICT));
        verify(appointmentRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    // ── Approve → ACTIVE ─────────────────────────────────────────────────────────

    @Test
    void approve_flipsPendingToActive_andEmitsApprovedEvent() {
        FacilityAdminAppointmentEntity pending = new FacilityAdminAppointmentEntity();
        pending.setId(7L);
        pending.setFacilityUuid(facilityUuid);
        pending.setPersonHealthId("HID-100");
        pending.setRole(FacilityAdminAppointmentEntity.ROLE_FACILITY_ADMINISTRATOR);
        pending.setApprovalState(FacilityAdminAppointmentEntity.STATE_PENDING);

        when(appointmentRepository.findById(7L)).thenReturn(Optional.of(pending));
        stubFacility();
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FacilityClaimDtos.AppointmentView view = service.approve(7L,
                new FacilityClaimDtos.ApproveAppointmentRequest("national-admin-1"));

        assertThat(view.approvalState()).isEqualTo("ACTIVE");

        ArgumentCaptor<EventOutboxEntity> outboxCaptor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(outboxCaptor.capture());
        assertThat(outboxCaptor.getValue().getEventType()).isEqualTo(FacilityClaimService.EVENT_APPROVED);
        @SuppressWarnings("unchecked")
        var payload = outboxCaptor.getValue().getPayload();
        assertThat(payload.get("approvalState")).isEqualTo("ACTIVE");
        assertThat(payload.get("personHealthId")).isEqualTo("HID-100");
    }

    @Test
    void approve_unknownAppointmentIs404() {
        when(appointmentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(999L, null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    // ── Tenant isolation ─────────────────────────────────────────────────────────

    @Test
    void tenantIsolationIsEnforced() {
        FacilityEntity foreign = new FacilityEntity();
        foreign.setId(43L);
        foreign.setFacilityUuid(facilityUuid);
        foreign.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000099"));
        when(facilityRepository.findByFacilityUuid(facilityUuid)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> service.checkEligibility(facilityUuid))
                .isInstanceOf(SecurityException.class);
    }
}
