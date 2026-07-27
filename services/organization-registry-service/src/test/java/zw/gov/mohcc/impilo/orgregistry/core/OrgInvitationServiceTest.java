package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.AppointmentRoleRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrgInvitationRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.repository.OrganizationRepository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.NoSuchElementException;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgInvitationServiceTest {

    @Mock OrgInvitationRepository invitationRepository;
    @Mock OrganizationRepository organizationRepository;
    @Mock AffiliationService affiliationService;
    @Mock RegulatoryAppointmentService appointmentService;
    @Mock AppointmentRoleRepository roleRepository;
    @Mock OrgRegistryOutboxWriter outboxWriter;

    OrgInvitationService service;
    UUID tenant;
    UUID org;

    @BeforeEach
    void setUp() {
        service = new OrgInvitationService(invitationRepository, organizationRepository,
                affiliationService, appointmentService, roleRepository, outboxWriter);
        tenant = UUID.randomUUID();
        org = UUID.randomUUID();
    }

    private static OrgInvitationEntity savedAnswer(org.mockito.invocation.InvocationOnMock inv) {
        return inv.getArgument(0);
    }

    /** Legacy free-text-role invite (affiliation path). */
    private OrgRegistryDtos.CreateInvitationRequest legacyReq() {
        return new OrgRegistryDtos.CreateInvitationRequest(
                UUID.randomUUID(), "admin@example.org", "EMAIL", "FACILITY_ADMIN", null, UUID.randomUUID(), 24);
    }

    // ── create: the token is hashed, never stored raw ───────────────────────

    @Test
    void create_returnsARawTokenButPersistsOnlyItsHash() throws Exception {
        when(organizationRepository.existsById(org)).thenReturn(true);
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        OrgInvitationEntity out = service.create(tenant, org, legacyReq());

        assertThat(out.getStatus()).isEqualTo("PENDING");
        assertThat(out.getRawToken()).as("the raw token is returned to the issuer once").isNotBlank();
        assertThat(out.getTokenHash()).as("only the hash is persisted").isNotBlank();
        assertThat(out.getTokenHash())
                .as("the stored hash must not equal the raw token — a table read is not a live link")
                .isNotEqualTo(out.getRawToken())
                .hasSize(64);
        verify(outboxWriter).publish(eq(tenant), any(), any(), any(), eq("issued"), any(), any());
    }

    @Test
    void create_rejectsUnknownOrganisation() {
        when(organizationRepository.existsById(org)).thenReturn(false);
        assertThatThrownBy(() -> service.create(tenant, org, legacyReq()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void create_rejectsWhenNeitherRoleNorRoleCodeGiven() {
        when(organizationRepository.existsById(org)).thenReturn(true);
        OrgRegistryDtos.CreateInvitationRequest bad = new OrgRegistryDtos.CreateInvitationRequest(
                UUID.randomUUID(), "admin@example.org", "EMAIL", "  ", null, null, null);
        assertThatThrownBy(() -> service.create(tenant, org, bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_rejectsARoleCodeOutsideTheVocabulary() {
        when(organizationRepository.existsById(org)).thenReturn(true);
        when(roleRepository.findById("NOT_A_ROLE")).thenReturn(Optional.empty());
        OrgRegistryDtos.CreateInvitationRequest bad = new OrgRegistryDtos.CreateInvitationRequest(
                UUID.randomUUID(), "x@y.z", "EMAIL", null, "not_a_role", null, null);
        assertThatThrownBy(() -> service.create(tenant, org, bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── accept ──────────────────────────────────────────────────────────────

    private OrgInvitationEntity pending(String roleCode) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OrgInvitationEntity inv = new OrgInvitationEntity();
        inv.setId(UUID.randomUUID());
        inv.setTenantId(tenant);
        inv.setOrganizationId(org);
        inv.setInvitedByRepId(UUID.randomUUID());
        inv.setInviteeIdentifier("admin@example.org");
        inv.setRole(roleCode == null ? "FACILITY_ADMIN" : roleCode);
        inv.setRoleCode(roleCode);
        inv.setTokenHash("hash-of-TOK");
        inv.setStatus("PENDING");
        inv.setCreatedAt(now);
        inv.setExpiresAt(now.plusHours(24));
        return inv;
    }

    @Test
    void accept_legacyRole_createsAffiliationViaAtomicRedeem() throws Exception {
        OrgInvitationEntity inv = pending(null);
        AffiliationEntity aff = new AffiliationEntity();
        aff.setId(UUID.randomUUID());
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.of(inv));
        when(invitationRepository.redeem(eq(inv.getId()), any(), eq("HID-1"))).thenReturn(1);
        when(affiliationService.create(eq(tenant), eq(org), any())).thenReturn(aff);
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        OrgInvitationEntity out = service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1"));

        assertThat(out.getStatus()).isEqualTo("ACCEPTED");
        assertThat(out.getAffiliationId()).isEqualTo(aff.getId());
        verify(affiliationService).create(eq(tenant), eq(org), any());
        verify(appointmentService, never()).create(any(), any(), any(), any());
    }

    /** A governed role mints a PENDING_VERIFICATION appointment (source=INVITATION), not an affiliation. */
    @Test
    void accept_governedRole_mintsAppointmentNotAffiliation() throws Exception {
        OrgInvitationEntity inv = pending("REGISTRATION_OFFICER");
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.of(inv));
        when(invitationRepository.redeem(eq(inv.getId()), any(), eq("HID-1"))).thenReturn(1);
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        service.accept(tenant, new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1"));

        ArgumentCaptor<OrgRegistryDtos.CreateAppointmentRequest> captor =
                ArgumentCaptor.forClass(OrgRegistryDtos.CreateAppointmentRequest.class);
        verify(appointmentService).create(eq(tenant), eq(org), captor.capture(), eq("ORG_INVITATION"));
        assertThat(captor.getValue().source()).isEqualTo("INVITATION");
        assertThat(captor.getValue().roleCode()).isEqualTo("REGISTRATION_OFFICER");
        verify(affiliationService, never()).create(any(), any(), any());
    }

    /** The atomic claim's loser (redeem == 0) is refused, never granted a second acceptance. */
    @Test
    void accept_losesTheRedeemRace_isRefused() throws Exception {
        OrgInvitationEntity inv = pending(null);
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.of(inv));
        when(invitationRepository.redeem(eq(inv.getId()), any(), eq("HID-1"))).thenReturn(0);

        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1")))
                .isInstanceOf(NoSuchElementException.class);
        verify(affiliationService, never()).create(any(), any(), any());
    }

    // ── uniform, state-free answer for every non-redeemable token ───────────

    @Test
    void accept_unknownToken_isNotRecognised() {
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("NOPE", "HID-1")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Invitation is not valid");
    }

    @Test
    void accept_acceptedByAnother_isTheSameUniformAnswerAsUnknown() {
        OrgInvitationEntity inv = pending(null);
        inv.setStatus("ACCEPTED");
        inv.setAcceptedByHealthId("HID-1");
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-2")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Invitation is not valid");
    }

    @Test
    void accept_expired_isTheSameUniformAnswer_andStampsExpired() {
        OrgInvitationEntity inv = pending(null);
        inv.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.of(inv));
        lenient().when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Invitation is not valid");
        assertThat(inv.getStatus()).isEqualTo("EXPIRED");
    }

    @Test
    void accept_isIdempotentForSamePerson() throws Exception {
        OrgInvitationEntity inv = pending(null);
        inv.setStatus("ACCEPTED");
        inv.setAcceptedByHealthId("HID-1");
        when(invitationRepository.findByTokenHashAndTenantId(anyString(), eq(tenant)))
                .thenReturn(Optional.of(inv));

        OrgInvitationEntity out = service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1"));

        assertThat(out.getStatus()).isEqualTo("ACCEPTED");
        verify(affiliationService, never()).create(any(), any(), any());
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Test
    void revoke_movesPendingToRevoked() throws Exception {
        OrgInvitationEntity inv = pending(null);
        when(invitationRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        OrgInvitationEntity out = service.revoke(tenant, inv.getId());

        assertThat(out.getStatus()).isEqualTo("REVOKED");
        verify(outboxWriter).publish(eq(tenant), any(), any(), any(), eq("revoked"), any(), any());
    }

    @Test
    void revoke_rejectsNonPending() {
        OrgInvitationEntity inv = pending(null);
        inv.setStatus("ACCEPTED");
        when(invitationRepository.findById(inv.getId())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.revoke(tenant, inv.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
