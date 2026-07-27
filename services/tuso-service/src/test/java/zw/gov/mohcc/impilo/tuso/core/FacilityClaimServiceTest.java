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

    // ── Eligibility is GENERIC — the D-L4 anti-enumeration boundary ──────────────

    @Test
    void eligibility_isGenericAndNeverDisclosesLegitimacyOrRoster() {
        // Probing a facility UUID must reveal nothing: no legitimacy verdicts, no
        // administrator counts — the SAME response for every facility.
        stubFacility();

        FacilityClaimDtos.EligibilityView view = service.checkEligibility(facilityUuid);

        assertThat(view.allowedOnPlatform()).isTrue();
        assertThat(view.claimable()).isTrue();
        assertThat(view.activeAdministratorCount()).isZero();
        assertThat(view.reasons()).hasSize(1);
        assertThat(view.reasons().get(0)).doesNotContain("platform operation", "legitimacy");
        // The legitimacy verdict is never even consulted on the eligibility path.
        verify(legitimacyRepository, never()).findByFacilityIdOrderBySourceAsc(any());
    }

    @Test
    void eligibility_reportsOnlyTheCallersOwnState() {
        stubFacility();
        when(appointmentRepository.existsByFacilityUuidAndPersonHealthIdAndApprovalState(
                facilityUuid, actor, FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(true);

        FacilityClaimDtos.EligibilityView view = service.checkEligibility(facilityUuid);

        assertThat(view.alreadyAdministered()).isTrue();
    }

    // ── Submit claim → PENDING appointment ──────────────────────────────────────

    @Test
    void submitClaim_createsPendingAppointment_andEmitsSubmittedEvent() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(true)));
        when(appointmentRepository.existsByFacilityUuidAndPersonHealthIdAndRoleAndApprovalState(
                facilityUuid, "HID-100", FacilityAdminAppointmentEntity.ROLE_FACILITY_ADMINISTRATOR,
                FacilityAdminAppointmentEntity.STATE_ACTIVE)).thenReturn(false);
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> {
                    FacilityAdminAppointmentEntity a = inv.getArgument(0);
                    a.setId(7L);
                    return a;
                });

        FacilityClaimDtos.AppointmentView view = service.submitClaim(facilityUuid,
                new FacilityClaimDtos.SubmitClaimRequest("HID-100", null, "doc://evidence/1", null, null, null, null));

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
    void submitClaim_notAllowedFacilityStillLandsPendingForStewardReview() {
        // ANTI-ENUM (D-L4): the legitimacy verdict is a STEWARD input, not a
        // submission gate — a probing claimant learns nothing by submitting.
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(false)));
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> {
                    FacilityAdminAppointmentEntity a = inv.getArgument(0);
                    a.setId(8L);
                    return a;
                });

        FacilityClaimDtos.AppointmentView view = service.submitClaim(facilityUuid,
                new FacilityClaimDtos.SubmitClaimRequest("HID-100", null, "doc://evidence/1", null, null, null, null));

        assertThat(view.approvalState()).isEqualTo("PENDING");
        verify(outboxRepository).save(any());
    }

    @Test
    void submitClaim_requiresProofOfAuthority() {
        stubFacility();

        assertThatThrownBy(() -> service.submitClaim(facilityUuid,
                new FacilityClaimDtos.SubmitClaimRequest("HID-100", null, null, null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void submitClaim_rejectsUnknownRoleScope() {
        stubFacility();

        assertThatThrownBy(() -> service.submitClaim(facilityUuid,
                new FacilityClaimDtos.SubmitClaimRequest("HID-100", "SUPREME_LEADER", "doc://e/1", null, null, null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void submitClaim_recoveryClaimTypeIsRecorded() {
        stubFacility();
        when(legitimacyRepository.findByFacilityIdOrderBySourceAsc(facilityUuid))
                .thenReturn(List.of(legitimacy(true)));
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.submitClaim(facilityUuid, new FacilityClaimDtos.SubmitClaimRequest(
                "HID-100", "DATA_STEWARD", "doc://evidence/2", "RECOVERY", null, null, null));

        ArgumentCaptor<FacilityAdminAppointmentEntity> captor =
                ArgumentCaptor.forClass(FacilityAdminAppointmentEntity.class);
        verify(appointmentRepository).save(captor.capture());
        assertThat(captor.getValue().getClaimType())
                .isEqualTo(FacilityAdminAppointmentEntity.CLAIM_TYPE_RECOVERY);
        assertThat(captor.getValue().getRole()).isEqualTo("DATA_STEWARD");
    }

    // ── Approve → ACTIVE ─────────────────────────────────────────────────────────

    // ── FCV-W0d: REJECTED and REVOKED were legal values no code path could write ─────────────

    private FacilityAdminAppointmentEntity appointmentIn(String state) {
        FacilityAdminAppointmentEntity a = new FacilityAdminAppointmentEntity();
        a.setId(9L);
        a.setFacilityUuid(facilityUuid);
        a.setPersonHealthId("HID-200");
        a.setRole(FacilityAdminAppointmentEntity.ROLE_FACILITY_ADMINISTRATOR);
        a.setApprovalState(state);
        return a;
    }

    @Test
    void reject_turnsDownAPendingClaimWithAReason() {
        FacilityAdminAppointmentEntity pending = appointmentIn(FacilityAdminAppointmentEntity.STATE_PENDING);
        when(appointmentRepository.findById(9L)).thenReturn(Optional.of(pending));
        stubFacility();
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FacilityClaimDtos.AppointmentView view = service.reject(9L, "Appointment letter did not match the facility");

        assertThat(view.approvalState()).isEqualTo("REJECTED");
        ArgumentCaptor<EventOutboxEntity> captor = ArgumentCaptor.forClass(EventOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertThat(captor.getValue().getEventType()).isEqualTo(FacilityClaimService.EVENT_REJECTED);
    }

    @Test
    void reject_requiresAReason() {
        when(appointmentRepository.findById(9L))
                .thenReturn(Optional.of(appointmentIn(FacilityAdminAppointmentEntity.STATE_PENDING)));
        stubFacility();

        assertThatThrownBy(() -> service.reject(9L, "  "))
                .isInstanceOf(ResponseStatusException.class);
        verify(appointmentRepository, never()).save(any());
    }

    @Test
    void revoke_withdrawsAnActiveAppointment_butNotAPendingOne() {
        when(appointmentRepository.findById(9L))
                .thenReturn(Optional.of(appointmentIn(FacilityAdminAppointmentEntity.STATE_ACTIVE)));
        stubFacility();
        when(appointmentRepository.save(any(FacilityAdminAppointmentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.revoke(9L, "Left the facility").approvalState()).isEqualTo("REVOKED");

        // A pending claim is rejected, never revoked — the two decisions are not interchangeable.
        when(appointmentRepository.findById(9L))
                .thenReturn(Optional.of(appointmentIn(FacilityAdminAppointmentEntity.STATE_PENDING)));
        assertThatThrownBy(() -> service.revoke(9L, "Left the facility"))
                .isInstanceOf(ResponseStatusException.class);
    }

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

    // ── Cross-facility review queue (Trust Console) ───────────────────────────────

    @Test
    void listByState_returnsTenantFacilitiesOnly_newestFirst() {
        UUID foreignFacilityUuid = UUID.fromString("99999999-8888-7777-6666-555555555555");
        FacilityEntity foreign = new FacilityEntity();
        foreign.setId(43L);
        foreign.setFacilityUuid(foreignFacilityUuid);
        foreign.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000099"));

        FacilityAdminAppointmentEntity mine = new FacilityAdminAppointmentEntity();
        mine.setId(1L);
        mine.setFacilityUuid(facilityUuid);
        mine.setPersonHealthId("HID-1");
        mine.setApprovalState(FacilityAdminAppointmentEntity.STATE_PENDING);

        FacilityAdminAppointmentEntity foreignAppointment = new FacilityAdminAppointmentEntity();
        foreignAppointment.setId(2L);
        foreignAppointment.setFacilityUuid(foreignFacilityUuid);
        foreignAppointment.setPersonHealthId("HID-2");
        foreignAppointment.setApprovalState(FacilityAdminAppointmentEntity.STATE_PENDING);

        stubFacility();
        when(facilityRepository.findByFacilityUuid(foreignFacilityUuid)).thenReturn(Optional.of(foreign));
        when(appointmentRepository.findByApprovalStateOrderByCreatedAtDesc(
                FacilityAdminAppointmentEntity.STATE_PENDING))
                .thenReturn(List.of(mine, foreignAppointment));

        List<FacilityClaimDtos.AppointmentView> queue = service.listByState("pending");

        assertThat(queue).hasSize(1);
        assertThat(queue.get(0).personHealthId()).isEqualTo("HID-1");
    }

    @Test
    void listByState_rejectsUnknownState() {
        assertThatThrownBy(() -> service.listByState("MAYBE"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(t -> assertThat(((ResponseStatusException) t).getStatusCode())
                        .isEqualTo(HttpStatus.BAD_REQUEST));
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
