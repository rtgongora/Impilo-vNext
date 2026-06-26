package zw.gov.mohcc.impilo.tshepo.identity.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IdentifierResolveRequest;
import zw.gov.mohcc.impilo.tshepo.identity.api.dto.IdentifierResolveResponse;
import zw.gov.mohcc.impilo.tshepo.identity.core.IdResolutionService;
import zw.gov.mohcc.impilo.tshepo.identity.core.SilentIdentifierResolutionService;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Finding 3 regression: the body-supplied {@code tenantId} must be cross-checked
 * against the trusted {@code X-Tenant-ID} header. A caller must not be able to
 * resolve identifiers in a tenant they are not scoped to by spoofing the body.
 */
@ExtendWith(MockitoExtension.class)
class ResolutionControllerTenantGuardTest {

    @Mock
    IdResolutionService resolutionService;
    @Mock
    SilentIdentifierResolutionService silentResolutionService;

    private ResolutionController controller() {
        return new ResolutionController(resolutionService, silentResolutionService);
    }

    @Test
    void rejectsWhenBodyTenantDiffersFromTrustedHeader_andNeverResolves() {
        UUID trustedTenant = UUID.randomUUID();
        UUID spoofedTenant = UUID.randomUUID();
        IdentifierResolveRequest request =
                new IdentifierResolveRequest(spoofedTenant, "HEALTH_ID", UUID.randomUUID().toString());

        assertThatThrownBy(() -> controller().resolveIdentifier(request, trustedTenant))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId does not match");

        // The resolver is never reached for a tenant-spoofed request.
        verifyNoInteractions(silentResolutionService);
    }

    @Test
    void resolvesWhenBodyTenantMatchesTrustedHeader() {
        UUID tenant = UUID.randomUUID();
        IdentifierResolveRequest request =
                new IdentifierResolveRequest(tenant, "HEALTH_ID", UUID.randomUUID().toString());
        IdentifierResolveResponse resolved = IdentifierResolveResponse.notResolved();
        when(silentResolutionService.resolve(any())).thenReturn(resolved);

        ResponseEntity<ApiResponse<IdentifierResolveResponse>> response =
                controller().resolveIdentifier(request, tenant);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        verify(silentResolutionService).resolve(request);
        verify(resolutionService, never()).resolve(any());
    }
}
