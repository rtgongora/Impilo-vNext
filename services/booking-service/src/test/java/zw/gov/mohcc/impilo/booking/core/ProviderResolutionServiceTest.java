package zw.gov.mohcc.impilo.booking.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.booking.integration.VarapiClient;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderResolutionServiceTest {

    @Mock private VarapiClient varapiClient;

    private ProviderResolutionService service;

    @BeforeEach
    void setUp() {
        service = new ProviderResolutionService(varapiClient);
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(), "actor-1", "STAFF", "TREATMENT", "device",
                UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @Test
    void resolve_returnsProviderWhenVarapiValidates() {
        // `claimed` is now part of the happy path: the practitioner has opted in to receiving
        // requests here. Without it this fixture would (correctly) be refused.
        when(varapiClient.getProvider(any(), eq("PRV-ZW-001")))
                .thenReturn(Map.of("providerPublicId", "PRV-ZW-001", "displayName", "Dr. Moyo",
                        "claimed", true));

        ProviderResolutionService.ResolvedProvider resolved =
                service.resolve(TrustContextHolder.require(), "PRV-ZW-001");

        assertThat(resolved.providerPublicId()).isEqualTo("PRV-ZW-001");
        assertThat(resolved.providerName()).isEqualTo("Dr. Moyo");
    }

    // ── Registration makes a practitioner findable; opting in makes them bookable ──────────
    // Provider search returns the whole Health Professions Authority roll — 4,241
    // practitioners who are registered, and so correctly searchable and verifiable, but who
    // have never agreed to receive requests on this platform. Resolving an id must therefore
    // stop meaning "may be booked".

    @Test
    void resolve_refusesARegisteredPractitionerWhoHasNotOptedIn() {
        when(varapiClient.getProvider(any(), eq("PROV-ZW-04241")))
                .thenReturn(Map.of("providerPublicId", "PROV-ZW-04241", "displayName", "T. Moyo",
                        "status", "INACTIVE", "lifecycleStatus", "PRELOADED",
                        "registryStatus", "PENDING_VERIFICATION", "claimed", false));

        assertThatThrownBy(() -> service.resolve(TrustContextHolder.require(), "PROV-ZW-04241"))
                .as("booking a practitioner who never joined would commit a named real person "
                        + "to an appointment they cannot see")
                .isInstanceOf(ProviderResolutionService.ProviderNotParticipatingException.class)
                .hasMessageContaining("has not joined")
                .hasMessageContaining("searchable and verifiable");
    }

    @Test
    void resolve_failsClosedWhenParticipationIsUnknown() {
        // A registry response that omits the field tells us nothing, and silence is not
        // consent — especially here, where discovery is far wider than participation.
        when(varapiClient.getProvider(any(), eq("PRV-ZW-002")))
                .thenReturn(Map.of("providerPublicId", "PRV-ZW-002", "displayName", "Dr. Ncube"));

        assertThatThrownBy(() -> service.resolve(TrustContextHolder.require(), "PRV-ZW-002"))
                .isInstanceOf(ProviderResolutionService.ProviderNotParticipatingException.class);
    }

    @Test
    void resolve_acceptsAClaimedAtTimestampAsTheOptIn() {
        // The opt-in is stored as claimed_at; a caller may surface either shape.
        when(varapiClient.getProvider(any(), eq("PRV-ZW-003")))
                .thenReturn(Map.of("providerPublicId", "PRV-ZW-003", "displayName", "Dr. Sibanda",
                        "claimedAt", "2026-08-06T04:00:00Z"));

        assertThat(service.resolve(TrustContextHolder.require(), "PRV-ZW-003").providerPublicId())
                .isEqualTo("PRV-ZW-003");
    }

    @Test
    void resolve_rejectsUnknownProvider() {
        when(varapiClient.getProvider(any(), eq("PRV-INVALID"))).thenReturn(Map.of());

        assertThatThrownBy(() -> service.resolve(TrustContextHolder.require(), "PRV-INVALID"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid provider_id");
    }

    @Test
    void resolve_rejectsBlankProviderId() {
        assertThatThrownBy(() -> service.resolve(TrustContextHolder.require(), "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider_id is required");
    }
}
