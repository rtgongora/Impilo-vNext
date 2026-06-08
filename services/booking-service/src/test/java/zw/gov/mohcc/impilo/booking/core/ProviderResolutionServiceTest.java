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
        when(varapiClient.getProvider(any(), eq("PRV-ZW-001")))
                .thenReturn(Map.of("providerPublicId", "PRV-ZW-001", "displayName", "Dr. Moyo"));

        ProviderResolutionService.ResolvedProvider resolved =
                service.resolve(TrustContextHolder.require(), "PRV-ZW-001");

        assertThat(resolved.providerPublicId()).isEqualTo("PRV-ZW-001");
        assertThat(resolved.providerName()).isEqualTo("Dr. Moyo");
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
