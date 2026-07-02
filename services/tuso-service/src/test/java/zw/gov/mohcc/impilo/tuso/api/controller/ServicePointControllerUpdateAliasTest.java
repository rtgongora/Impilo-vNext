package zw.gov.mohcc.impilo.tuso.api.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.shared.response.ApiResponse;
import zw.gov.mohcc.impilo.tuso.api.dto.ServicePointDto;
import zw.gov.mohcc.impilo.tuso.core.ServicePointService;
import zw.gov.mohcc.impilo.tuso.persistence.entity.ServicePointEntity;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the POST update alias ({@code POST /service-points/{id}/update}) accepts an update and drives
 * the same service path as PATCH. The alias exists because the BFF's RestTemplate
 * ({@code SimpleClientHttpRequestFactory}) cannot issue PATCH at runtime.
 */
@ExtendWith(MockitoExtension.class)
class ServicePointControllerUpdateAliasTest {

    @Mock private ServicePointService servicePointService;
    private ServicePointController controller;
    private final UUID tenantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new ServicePointController(servicePointService);
        TrustContextHolder.set(new TrustContext(tenantId, "actor-1", "DEVELOPER", "FACILITY_SETUP",
                "device-1", UUID.randomUUID(), null, null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void updatePost_acceptsUpdateAndInvokesService() {
        UUID spId = UUID.randomUUID();
        ServicePointEntity entity = mock(ServicePointEntity.class);
        when(entity.getId()).thenReturn(spId);
        when(entity.getFacilityId()).thenReturn(7L);
        when(entity.getName()).thenReturn("New Name");
        when(entity.isActive()).thenReturn(true);
        when(servicePointService.update(any(), eq(spId), any())).thenReturn(entity);

        ResponseEntity<ApiResponse<ServicePointDto.Response>> response = controller.updatePost(
                7L, spId, new ServicePointDto.UpdateRequest(
                        "New Name", null, null, null, null, null, null, null, null));

        assertEquals(200, response.getStatusCode().value());
        assertEquals("New Name", response.getBody().data().name());
        verify(servicePointService).update(any(), eq(spId), any());
    }
}
