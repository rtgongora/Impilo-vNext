package zw.gov.mohcc.impilo.tshepo.offline.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.ProviderDeviceCredentialEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.ProviderDeviceCredentialRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * PJ13/D-P9: no cold issuance (online session mandatory); an active credential
 * permits routine actions but refuses high-risk ones offline; expired/missing
 * credentials deny; a new device credential supersedes the prior one.
 */
@ExtendWith(MockitoExtension.class)
class ProviderDeviceCredentialServiceTest {

    @Mock private ProviderDeviceCredentialRepository repository;

    private ProviderDeviceCredentialService service;

    private final UUID tenantId = UUID.randomUUID();
    private final UUID facilityId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ProviderDeviceCredentialService(repository, new ObjectMapper());
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private ProviderDeviceCredentialService.IssueRequest request(String sessionRef) {
        return new ProviderDeviceCredentialService.IssueRequest(
                tenantId, "PROVPUB1", "HID-1", "device-1", facilityId,
                Map.of("lifecycleStatus", "LICENCED_ACTIVE"), sessionRef, null);
    }

    @Test
    void issuanceRequiresAnOnlineSession() {
        assertThatThrownBy(() -> service.issue(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("online session");
    }

    @Test
    void issuedCredentialCachesStatusAndSetsDenylistAndExpiry() {
        when(repository.findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        ProviderDeviceCredentialEntity credential = service.issue(request("online-session-9"));

        assertThat(credential.getStatusSnapshot()).contains("LICENCED_ACTIVE");
        assertThat(credential.getHighRiskDenylist()).contains("PRESCRIBE");
        assertThat(credential.getIssuedFromSession()).isEqualTo("online-session-9");
        assertThat(credential.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    void routineActionAllowedOffline_highRiskRefused() {
        ProviderDeviceCredentialEntity active = active(Instant.now().plus(1, ChronoUnit.HOURS));
        when(repository.findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
                tenantId, "PROVPUB1", "device-1", ProviderDeviceCredentialEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(active));

        assertThat(service.decideOffline(tenantId, "PROVPUB1", "device-1", "RECORD_VITALS").allowed())
                .isTrue();
        ProviderDeviceCredentialService.OfflineDecision prescribe =
                service.decideOffline(tenantId, "PROVPUB1", "device-1", "prescribe");
        assertThat(prescribe.allowed()).isFalse();
        assertThat(prescribe.reason()).isEqualTo("HIGH_RISK_ACTION_REQUIRES_ONLINE");
    }

    @Test
    void expiredOrMissingCredentialDenies() {
        when(repository.findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
                tenantId, "PROVPUB1", "device-1", ProviderDeviceCredentialEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(active(Instant.now().minus(1, ChronoUnit.HOURS))));
        assertThat(service.decideOffline(tenantId, "PROVPUB1", "device-1", "RECORD_VITALS").reason())
                .isEqualTo("OFFLINE_CREDENTIAL_EXPIRED");

        when(repository.findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
                tenantId, "PROVPUB1", "device-2", ProviderDeviceCredentialEntity.STATUS_ACTIVE))
                .thenReturn(Optional.empty());
        assertThat(service.decideOffline(tenantId, "PROVPUB1", "device-2", "RECORD_VITALS").reason())
                .isEqualTo("NO_OFFLINE_CREDENTIAL");
    }

    @Test
    void reissueSupersedesPriorDeviceCredential() {
        ProviderDeviceCredentialEntity prior = active(Instant.now().plus(1, ChronoUnit.HOURS));
        when(repository.findByTenantIdAndProviderPublicIdAndDeviceIdAndStatus(
                tenantId, "PROVPUB1", "device-1", ProviderDeviceCredentialEntity.STATUS_ACTIVE))
                .thenReturn(Optional.of(prior));

        service.issue(request("online-session-10"));

        assertThat(prior.getStatus()).isEqualTo(ProviderDeviceCredentialEntity.STATUS_REVOKED);
    }

    private ProviderDeviceCredentialEntity active(Instant expiresAt) {
        ProviderDeviceCredentialEntity e = new ProviderDeviceCredentialEntity();
        e.setTenantId(tenantId);
        e.setProviderPublicId("PROVPUB1");
        e.setDeviceId("device-1");
        e.setStatus(ProviderDeviceCredentialEntity.STATUS_ACTIVE);
        e.setExpiresAt(expiresAt);
        e.setHighRiskDenylist("[\"PRESCRIBE\",\"SIGN_RECORD\"]");
        return e;
    }
}
