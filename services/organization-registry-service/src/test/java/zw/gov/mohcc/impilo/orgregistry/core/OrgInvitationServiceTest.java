package zw.gov.mohcc.impilo.orgregistry.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.orgregistry.api.OrgRegistryDtos;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.AffiliationEntity;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.OrgInvitationEntity;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrgInvitationServiceTest {

    @Mock
    OrgInvitationRepository invitationRepository;
    @Mock
    OrganizationRepository organizationRepository;
    @Mock
    AffiliationService affiliationService;
    @Mock
    OrgRegistryOutboxWriter outboxWriter;

    OrgInvitationService service;

    UUID tenant;
    UUID org;

    @BeforeEach
    void setUp() {
        service = new OrgInvitationService(
                invitationRepository, organizationRepository, affiliationService, outboxWriter);
        tenant = UUID.randomUUID();
        org = UUID.randomUUID();
    }

    private static OrgInvitationEntity savedAnswer(org.mockito.invocation.InvocationOnMock inv) {
        return inv.getArgument(0);
    }

    private OrgRegistryDtos.CreateInvitationRequest req() {
        return new OrgRegistryDtos.CreateInvitationRequest(
                UUID.randomUUID(), "admin@example.org", "EMAIL", "FACILITY_ADMIN", UUID.randomUUID(), 24);
    }

    // ── create ────────────────────────────────────────────────────────────────

    @Test
    void create_issuesPendingInvitationWithTokenAndExpiry() throws Exception {
        when(organizationRepository.existsById(org)).thenReturn(true);
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        OrgInvitationEntity out = service.create(tenant, org, req());

        assertThat(out.getStatus()).isEqualTo("PENDING");
        assertThat(out.getToken()).isNotBlank();
        assertThat(out.getRole()).isEqualTo("FACILITY_ADMIN");
        assertThat(out.getTenantId()).isEqualTo(tenant);
        assertThat(out.getExpiresAt()).isAfter(out.getCreatedAt());
        verify(outboxWriter).publish(eq(tenant), any(), any(), any(), eq("issued"), any(), any());
    }

    @Test
    void create_rejectsUnknownOrganisation() {
        when(organizationRepository.existsById(org)).thenReturn(false);
        assertThatThrownBy(() -> service.create(tenant, org, req()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void create_rejectsMissingRole() {
        when(organizationRepository.existsById(org)).thenReturn(true);
        OrgRegistryDtos.CreateInvitationRequest bad = new OrgRegistryDtos.CreateInvitationRequest(
                UUID.randomUUID(), "admin@example.org", "EMAIL", "  ", null, null);
        assertThatThrownBy(() -> service.create(tenant, org, bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── accept ────────────────────────────────────────────────────────────────

    private OrgInvitationEntity pending() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OrgInvitationEntity inv = new OrgInvitationEntity();
        inv.setId(UUID.randomUUID());
        inv.setTenantId(tenant);
        inv.setOrganizationId(org);
        inv.setInvitedByRepId(UUID.randomUUID());
        inv.setInviteeIdentifier("admin@example.org");
        inv.setRole("FACILITY_ADMIN");
        inv.setToken("TOK");
        inv.setStatus("PENDING");
        inv.setCreatedAt(now);
        inv.setExpiresAt(now.plusHours(24));
        return inv;
    }

    @Test
    void accept_createsAffiliationAndMarksAccepted() throws Exception {
        OrgInvitationEntity inv = pending();
        AffiliationEntity aff = new AffiliationEntity();
        aff.setId(UUID.randomUUID());
        when(invitationRepository.findByTokenAndTenantId("TOK", tenant)).thenReturn(Optional.of(inv));
        when(affiliationService.create(eq(tenant), eq(org), any())).thenReturn(aff);
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        OrgInvitationEntity out = service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1"));

        assertThat(out.getStatus()).isEqualTo("ACCEPTED");
        assertThat(out.getAcceptedByHealthId()).isEqualTo("HID-1");
        assertThat(out.getAffiliationId()).isEqualTo(aff.getId());
        verify(affiliationService).create(eq(tenant), eq(org), any());
    }

    @Test
    void accept_rejectsUnknownToken() {
        when(invitationRepository.findByTokenAndTenantId("NOPE", tenant)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("NOPE", "HID-1")))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void accept_isIdempotentForSamePerson() throws Exception {
        OrgInvitationEntity inv = pending();
        inv.setStatus("ACCEPTED");
        inv.setAcceptedByHealthId("HID-1");
        when(invitationRepository.findByTokenAndTenantId("TOK", tenant)).thenReturn(Optional.of(inv));

        OrgInvitationEntity out = service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1"));

        assertThat(out.getStatus()).isEqualTo("ACCEPTED");
        verify(affiliationService, never()).create(any(), any(), any());
    }

    @Test
    void accept_rejectsAcceptedByDifferentPerson() {
        OrgInvitationEntity inv = pending();
        inv.setStatus("ACCEPTED");
        inv.setAcceptedByHealthId("HID-1");
        when(invitationRepository.findByTokenAndTenantId("TOK", tenant)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-2")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void accept_expiresAndRejectsPastExpiry() {
        OrgInvitationEntity inv = pending();
        inv.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(invitationRepository.findByTokenAndTenantId("TOK", tenant)).thenReturn(Optional.of(inv));
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        assertThatThrownBy(() -> service.accept(tenant,
                new OrgRegistryDtos.AcceptInvitationRequest("TOK", "HID-1")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(inv.getStatus()).isEqualTo("EXPIRED");
    }

    // ── revoke ──────────────────────────────────────────────────────────────────

    @Test
    void revoke_movesPendingToRevoked() throws Exception {
        OrgInvitationEntity inv = pending();
        when(invitationRepository.findById(inv.getId())).thenReturn(Optional.of(inv));
        when(invitationRepository.save(any())).thenAnswer(OrgInvitationServiceTest::savedAnswer);

        OrgInvitationEntity out = service.revoke(tenant, inv.getId());

        assertThat(out.getStatus()).isEqualTo("REVOKED");
        verify(outboxWriter).publish(eq(tenant), any(), any(), any(), eq("revoked"), any(), any());
    }

    @Test
    void revoke_rejectsNonPending() {
        OrgInvitationEntity inv = pending();
        inv.setStatus("ACCEPTED");
        when(invitationRepository.findById(inv.getId())).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> service.revoke(tenant, inv.getId()))
                .isInstanceOf(IllegalStateException.class);
    }
}
