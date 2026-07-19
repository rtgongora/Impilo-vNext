package zw.gov.mohcc.impilo.varapi.api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderCouncilRegistrationRecordRepository;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * D-P2 anti-enumeration contract: uniform response shape on hit and miss, a
 * timing floor on the miss path, INTERNAL-only access, and no data beyond the
 * provider public id ever leaves the resolver.
 */
@ExtendWith(MockitoExtension.class)
class ProviderRegistryResolveControllerTest {

    @Mock private ProviderRepository providerRepository;
    @Mock private ProviderCouncilRegistrationRecordRepository registrationRepository;

    private ProviderRegistryResolveController controller;

    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ProviderRegistryResolveController(providerRepository, registrationRepository);
        seedContext(AccessMode.INTERNAL);
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    private void seedContext(AccessMode mode) {
        TrustContextHolder.set(new TrustContext(
                tenantId, "svc-caller", "SYSTEM", "SYSTEM", "device",
                UUID.randomUUID(), null, null, null, mode));
    }

    @Test
    void resolve_hitAndMissShareOneShape() {
        ProviderEntity provider = new ProviderEntity();
        provider.setProviderPublicId("ABCDEF1234567890ABCDEF1234");
        when(providerRepository.findByProviderPublicIdAndTenantId(eq("ABCDEF1234567890ABCDEF1234"), eq(tenantId)))
                .thenReturn(Optional.of(provider));

        ApiResponse<Map<String, Object>> hit = controller.resolve(
                new ProviderRegistryResolveController.ResolveRequest(
                        tenantId, "PROVIDER_ID", "abcdef1234567890abcdef1234", null)).getBody();
        ApiResponse<Map<String, Object>> miss = controller.resolve(
                new ProviderRegistryResolveController.ResolveRequest(
                        tenantId, "PROVIDER_ID", "ZZZZZZ9999999999ZZZZZZ9999", null)).getBody();

        assertThat(hit.data().keySet()).containsExactlyElementsOf(miss.data().keySet());
        assertThat(hit.data().get("resolved")).isEqualTo(true);
        assertThat(hit.data().get("providerPublicId")).isEqualTo("ABCDEF1234567890ABCDEF1234");
        assertThat(miss.data().get("resolved")).isEqualTo(false);
        assertThat(miss.data().get("providerPublicId")).isNull();
    }

    @Test
    void resolve_unknownKindAndBlankValueAreUniformMisses() {
        ApiResponse<Map<String, Object>> unknownKind = controller.resolve(
                new ProviderRegistryResolveController.ResolveRequest(
                        tenantId, "NATIONAL_ID", "63-123456A70", null)).getBody();
        ApiResponse<Map<String, Object>> blank = controller.resolve(
                new ProviderRegistryResolveController.ResolveRequest(
                        tenantId, "PROVIDER_ID", "  ", null)).getBody();

        assertThat(unknownKind.data().get("resolved")).isEqualTo(false);
        assertThat(blank.data().get("resolved")).isEqualTo(false);
    }

    @Test
    void resolve_missTakesAtLeastTheTimingFloor() {
        when(registrationRepository.findFirstByTenantIdAndRegistrationNumberIgnoreCase(any(), any()))
                .thenReturn(Optional.empty());

        long start = System.nanoTime();
        controller.resolve(new ProviderRegistryResolveController.ResolveRequest(
                tenantId, "COUNCIL_REG", "MDPC-000000", null));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        assertThat(elapsedMs).isGreaterThanOrEqualTo(ProviderRegistryResolveController.TIMING_FLOOR_MS);
    }

    @Test
    void resolve_rejectsNonInternalCallers() {
        seedContext(AccessMode.EXTERNAL);
        assertThatThrownBy(() -> controller.resolve(
                new ProviderRegistryResolveController.ResolveRequest(
                        tenantId, "PROVIDER_ID", "ABCDEF1234567890ABCDEF1234", null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("INTERNAL_ONLY");
    }
}
