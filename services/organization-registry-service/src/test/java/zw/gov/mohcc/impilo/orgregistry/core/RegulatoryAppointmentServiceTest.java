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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
}
