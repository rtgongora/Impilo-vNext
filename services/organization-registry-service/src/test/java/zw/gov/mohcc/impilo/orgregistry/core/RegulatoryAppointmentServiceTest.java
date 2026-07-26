package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos.CreateAppointmentRequest;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AppointmentRoleEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrganizationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.RegulatoryAppointmentEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AppointmentRoleRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.RegulatoryAppointmentRepository;

import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegulatoryAppointmentServiceTest {

    @Mock RegulatoryAppointmentRepository appointmentRepository;
    @Mock AppointmentRoleRepository roleRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock OrgRegistryOutboxWriter outboxWriter;

    RegulatoryAppointmentService service;
    UUID tenant;
    UUID orgId;

    @BeforeEach
    void setUp() {
        service = new RegulatoryAppointmentService(
                appointmentRepository, roleRepository, organizationRepository, outboxWriter);
        tenant = UUID.randomUUID();
        orgId = UUID.randomUUID();
        OrganizationEntity org = new OrganizationEntity();
        org.setId(orgId);
        org.setCode("NCZ");
        lenient().when(organizationRepository.findByTenantIdAndId(tenant, orgId)).thenReturn(Optional.of(org));
        lenient().when(roleRepository.findById("REGISTRATION_OFFICER"))
                .thenReturn(Optional.of(new AppointmentRoleEntity()));
        lenient().when(appointmentRepository.save(any(RegulatoryAppointmentEntity.class))).thenAnswer(inv -> {
            RegulatoryAppointmentEntity a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
    }

    private CreateAppointmentRequest req() {
        return new CreateAppointmentRequest("HID-OFFICER-1", "registration_officer", null, null, null, null, null, null);
    }

    @Test
    void create_landsPendingVerification_grantsNoAuthority() throws Exception {
        RegulatoryAppointmentEntity appt = service.create(tenant, orgId, req(), "registrar-1");
        assertThat(appt.getStatus()).isEqualTo("PENDING_VERIFICATION");
        assertThat(appt.getRoleCode()).isEqualTo("REGISTRATION_OFFICER");   // normalised to upper
        assertThat(appt.getJurisdictionCode()).isEqualTo("NATIONAL");        // defaulted
        assertThat(appt.getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    void create_rejectsUnknownRole() {
        when(roleRepository.findById("NOT_A_ROLE")).thenReturn(Optional.empty());
        CreateAppointmentRequest bad =
                new CreateAppointmentRequest("HID-1", "not_a_role", null, null, null, null, null, null);
        assertThatThrownBy(() -> service.create(tenant, orgId, bad, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_promotesPendingToActive() throws Exception {
        RegulatoryAppointmentEntity appt = service.create(tenant, orgId, req(), "registrar-1");
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));
        when(appointmentRepository.existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
                eq(tenant), eq(orgId), anyString(), anyString(), eq("ACTIVE"))).thenReturn(false);

        RegulatoryAppointmentEntity active = service.verify(tenant, appt.getId(), "national-admin");
        assertThat(active.getStatus()).isEqualTo("ACTIVE");
        assertThat(active.getVerifiedBy()).isEqualTo("national-admin");
    }

    @Test
    void verify_rejectsSecondActiveForSamePersonOrgRole() throws Exception {
        RegulatoryAppointmentEntity appt = service.create(tenant, orgId, req(), "registrar-1");
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));
        when(appointmentRepository.existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
                eq(tenant), eq(orgId), anyString(), anyString(), eq("ACTIVE"))).thenReturn(true);

        assertThatThrownBy(() -> service.verify(tenant, appt.getId(), "national-admin"))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── RB-1: expiry is a state transition, not a decoration ────────────────
    //
    // valid_to was written by create() and read by nothing: no repository method mentioned it, no
    // scheduled bean existed, and the session path filters on status alone. An appointment that
    // lapsed a year ago stayed ACTIVE and kept minting 15-minute work-context tokens.

    private RegulatoryAppointmentEntity lapsed(LocalDate validTo) {
        RegulatoryAppointmentEntity a = new RegulatoryAppointmentEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenant);
        a.setOrganizationId(orgId);
        a.setPersonHealthId("person-1");
        a.setRoleCode("REGISTRATION_OFFICER");
        a.setJurisdictionCode("NATIONAL");
        a.setStatus("ACTIVE");
        a.setValidTo(validTo);
        return a;
    }

    @Test
    void expireLapsed_endsAnAppointmentWhoseValidityHasPassed() throws Exception {
        RegulatoryAppointmentEntity appt = lapsed(LocalDate.now().minusDays(1));
        when(appointmentRepository.findByStatusAndValidToBeforeAndExpirySweptAtIsNull(
                eq("ACTIVE"), any(LocalDate.class))).thenReturn(List.of(appt));

        int ended = service.expireLapsed(LocalDate.now());

        assertThat(ended).isEqualTo(1);
        assertThat(appt.getStatus()).isEqualTo("ENDED");
        assertThat(appt.getExpirySweptAt()).isNotNull();
    }

    /**
     * The teardown consumer keys on this event; without it the tokens already issued survive the
     * lapse. Publishing is therefore part of the transition, not a side effect of it.
     */
    @Test
    void expireLapsed_emitsTheEndedEventTheTrustPlaneConsumes() throws Exception {
        RegulatoryAppointmentEntity appt = lapsed(LocalDate.now().minusDays(1));
        when(appointmentRepository.findByStatusAndValidToBeforeAndExpirySweptAtIsNull(
                eq("ACTIVE"), any(LocalDate.class))).thenReturn(List.of(appt));

        service.expireLapsed(LocalDate.now());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxWriter).publish(eq(tenant), eq("REGULATORY_APPOINTMENT"), anyString(),
                eq("regulatory_appointment"), eq("ended"), anyString(), payload.capture());

        // Both anchors the consumer needs, plus the reason that distinguishes a lapse from a
        // revocation — ENDED alone cannot tell the holder why their access stopped.
        assertThat(payload.getValue())
                .containsEntry("personHealthId", "person-1")
                .containsEntry("organizationId", orgId.toString())
                .containsEntry("endReason", "EXPIRED");
    }

    /** Re-running the sweep must not re-end or re-emit; expirySweptAt is the idempotency marker. */
    @Test
    void expireLapsed_isIdempotentViaTheSweptStamp() throws Exception {
        when(appointmentRepository.findByStatusAndValidToBeforeAndExpirySweptAtIsNull(
                eq("ACTIVE"), any(LocalDate.class))).thenReturn(List.of());

        assertThat(service.expireLapsed(LocalDate.now())).isZero();
        verifyNoInteractions(outboxWriter);
    }

    // ── RB-2: administration is a role class, and a regulator is never left without one ────

    private void roleIsAdministrative(String roleCode, boolean administrative, boolean bootstrapOnly) {
        AppointmentRoleEntity role = new AppointmentRoleEntity();
        try {
            java.lang.reflect.Field a = AppointmentRoleEntity.class.getDeclaredField("administrative");
            a.setAccessible(true);
            a.setBoolean(role, administrative);
            java.lang.reflect.Field b = AppointmentRoleEntity.class.getDeclaredField("bootstrapOnly");
            b.setAccessible(true);
            b.setBoolean(role, bootstrapOnly);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        lenient().when(roleRepository.findById(roleCode)).thenReturn(Optional.of(role));
    }

    private RegulatoryAppointmentEntity activeAppointment(String roleCode) {
        RegulatoryAppointmentEntity a = new RegulatoryAppointmentEntity();
        a.setId(UUID.randomUUID());
        a.setTenantId(tenant);
        a.setOrganizationId(orgId);
        a.setPersonHealthId("person-1");
        a.setRoleCode(roleCode);
        a.setStatus("ACTIVE");
        return a;
    }

    /**
     * A regulator left with no administrator cannot add a user, restore its own access, or appoint
     * a replacement — recovery would mean a platform-root intervention on a live regulator. This is
     * the mandatory-second-administrator rule seen from the other end.
     */
    @Test
    void end_refusesToRemoveTheLastAdministrator() {
        RegulatoryAppointmentEntity appt = activeAppointment("REGULATOR_SECURITY_ADMINISTRATOR");
        roleIsAdministrative("REGULATOR_SECURITY_ADMINISTRATOR", true, false);
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));
        when(appointmentRepository.countActiveAdministrators(tenant, orgId, appt.getId())).thenReturn(0L);

        assertThatThrownBy(() -> service.end(tenant, appt.getId(), "resigned", "actor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LAST_ADMINISTRATOR");
        assertThat(appt.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void end_allowsRemovingAnAdministratorWhenAnotherRemains() throws Exception {
        RegulatoryAppointmentEntity appt = activeAppointment("REGULATOR_SECURITY_ADMINISTRATOR");
        roleIsAdministrative("REGULATOR_SECURITY_ADMINISTRATOR", true, false);
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));
        when(appointmentRepository.countActiveAdministrators(tenant, orgId, appt.getId())).thenReturn(1L);

        assertThat(service.end(tenant, appt.getId(), "resigned", "actor").getStatus()).isEqualTo("ENDED");
    }

    /** The guard is about administrators only — an inspector leaving is an ordinary departure. */
    @Test
    void end_doesNotGuardOperationalRoles() throws Exception {
        RegulatoryAppointmentEntity appt = activeAppointment("INSPECTOR");
        roleIsAdministrative("INSPECTOR", false, false);
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));

        assertThat(service.end(tenant, appt.getId(), "resigned", "actor").getStatus()).isEqualTo("ENDED");
        verify(appointmentRepository, never()).countActiveAdministrators(any(), any(), any());
    }

    /**
     * A founding role exists because nobody is there to approve it yet. Once a regulator has an
     * administrator, granting it again would route a privileged appointment around the four-eyes
     * rail it was created to require.
     */
    @Test
    void verify_refusesABootstrapRoleOnceAnAdministratorExists() throws Exception {
        RegulatoryAppointmentEntity appt = service.create(tenant, orgId, req(), "registrar-1");
        appt.setRoleCode("FOUNDING_REGULATOR_ADMINISTRATOR");
        roleIsAdministrative("FOUNDING_REGULATOR_ADMINISTRATOR", true, true);
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));
        when(appointmentRepository.existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
                eq(tenant), eq(orgId), anyString(), anyString(), eq("ACTIVE"))).thenReturn(false);
        when(appointmentRepository.countActiveAdministrators(tenant, orgId, null)).thenReturn(1L);

        assertThatThrownBy(() -> service.verify(tenant, appt.getId(), "national-admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ROLE_NOT_AVAILABLE");
    }

    @Test
    void verify_allowsTheFoundingRoleWhileTheSeatIsEmpty() throws Exception {
        RegulatoryAppointmentEntity appt = service.create(tenant, orgId, req(), "registrar-1");
        appt.setRoleCode("FOUNDING_REGULATOR_ADMINISTRATOR");
        roleIsAdministrative("FOUNDING_REGULATOR_ADMINISTRATOR", true, true);
        when(appointmentRepository.findByTenantIdAndId(tenant, appt.getId())).thenReturn(Optional.of(appt));
        when(appointmentRepository.existsByTenantIdAndOrganizationIdAndPersonHealthIdAndRoleCodeAndStatus(
                eq(tenant), eq(orgId), anyString(), anyString(), eq("ACTIVE"))).thenReturn(false);
        when(appointmentRepository.countActiveAdministrators(tenant, orgId, null)).thenReturn(0L);

        assertThat(service.verify(tenant, appt.getId(), "national-admin").getStatus()).isEqualTo("ACTIVE");
    }

    /**
     * Expiry deliberately ignores the succession guard: a lapse is a fact about the world, and
     * refusing to record it to keep somebody signed in would be the system granting regulatory
     * authority to itself.
     */
    @Test
    void expiry_isNotBlockedByTheSuccessionGuard() throws Exception {
        RegulatoryAppointmentEntity appt = lapsed(LocalDate.now().minusDays(1));
        appt.setRoleCode("REGULATOR_SECURITY_ADMINISTRATOR");
        when(appointmentRepository.findByStatusAndValidToBeforeAndExpirySweptAtIsNull(
                eq("ACTIVE"), any(LocalDate.class))).thenReturn(List.of(appt));

        assertThat(service.expireLapsed(LocalDate.now())).isEqualTo(1);
        assertThat(appt.getStatus()).isEqualTo("ENDED");
        verify(appointmentRepository, never()).countActiveAdministrators(any(), any(), any());
    }

    /**
     * The separation-of-duties rule spans rows and lands as a trigger, so it is asserted at the
     * schema rather than mocked here: granting access and being accountable for its use must never
     * be the same person.
     */
    @Test
    void theSchemaSeparatesTheSecurityAdministratorFromTheBusinessOwner() throws Exception {
        String migration = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/resources/db/migration/V009__regulator_administration_roles.sql"));
        assertThat(migration).contains("trg_regulator_duty_separation");
        assertThat(migration).contains("separation of duties");
        assertThat(migration)
                .as("the founding role must be capped so it cannot become a standing privileged class")
                .contains("'FOUNDING_REGULATOR_ADMINISTRATOR'");
        assertThat(migration)
                .as("the country root is established as an identity, never granted a broad ALLOW here")
                .doesNotContain("policy_rule");
    }

    /**
     * The sweep never renews. Putting a lapsed appointment back is a registrar's decision made
     * with evidence through verify(); a sweep that extended validity would be the system granting
     * regulatory authority to itself.
     */
    @Test
    void expireLapsed_neverExtendsValidity() throws Exception {
        LocalDate validTo = LocalDate.now().minusDays(30);
        RegulatoryAppointmentEntity appt = lapsed(validTo);
        when(appointmentRepository.findByStatusAndValidToBeforeAndExpirySweptAtIsNull(
                eq("ACTIVE"), any(LocalDate.class))).thenReturn(List.of(appt));

        service.expireLapsed(LocalDate.now());

        assertThat(appt.getValidTo()).isEqualTo(validTo);
        assertThat(appt.getStatus()).isNotEqualTo("ACTIVE");
    }
}
