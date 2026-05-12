package zw.gov.mohcc.impilo.tuso.api.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnapshotControllerTest {

    @Mock
    private FacilityRepository facilityRepository;

    @InjectMocks
    private SnapshotController snapshotController;

    private static final String TENANT_ID = "dddd0000-0000-0000-0000-000000000001";
    private static final UUID TENANT_UUID = UUID.fromString(TENANT_ID);
    private static final String POD_ID = "pod-1";
    private static final String REQUEST_ID = "req-1";
    private static final String CORRELATION_ID = "corr-1";

    private FacilityEntity makeFacility(Long id, String status) {
        FacilityEntity entity = new FacilityEntity();
        entity.setId(id);
        entity.setFacilityCode("FC-" + id);
        entity.setName("Facility " + id);
        entity.setFacilityType("CLINIC");
        entity.setProvince("Harare");
        entity.setDistrict("Harare Central");
        entity.setStatus(status);
        entity.setTenantId(TENANT_UUID);
        return entity;
    }

    @Test
    void facilitySnapshot_withStatusFilter_returnsOnlyMatchingFacilities() {
        FacilityEntity active = makeFacility(1L, "ACTIVE");
        Page<FacilityEntity> activePage = new PageImpl<>(Collections.singletonList(active));

        when(facilityRepository.findByTenantIdAndStatus(eq(TENANT_UUID), eq("ACTIVE"), any(Pageable.class)))
                .thenReturn(activePage);

        ResponseEntity<Map<String, Object>> response = snapshotController.facilitySnapshot(
                TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID, 0, 100, "ACTIVE");

        assertEquals(200, response.getStatusCodeValue());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        List<?> items = (List<?>) body.get("items");
        assertEquals(1, items.size());

        Map<?, ?> item = (Map<?, ?>) items.get(0);
        assertEquals("ACTIVE", item.get("status"));
        assertEquals("FC-1", item.get("facility_code"));

        verify(facilityRepository).findByTenantIdAndStatus(eq(TENANT_UUID), eq("ACTIVE"), any(Pageable.class));
    }

    @Test
    void facilitySnapshot_withInactiveStatusFilter_returnsOnlyInactiveFacilities() {
        FacilityEntity inactive = makeFacility(2L, "INACTIVE");
        Page<FacilityEntity> inactivePage = new PageImpl<>(Collections.singletonList(inactive));

        when(facilityRepository.findByTenantIdAndStatus(eq(TENANT_UUID), eq("INACTIVE"), any(Pageable.class)))
                .thenReturn(inactivePage);

        ResponseEntity<Map<String, Object>> response = snapshotController.facilitySnapshot(
                TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID, 0, 100, "INACTIVE");

        assertEquals(200, response.getStatusCodeValue());
        List<?> items = (List<?>) response.getBody().get("items");
        assertEquals(1, items.size());
        assertEquals("INACTIVE", ((Map<?, ?>) items.get(0)).get("status"));
    }

    @Test
    void facilitySnapshot_withNoStatusFilter_returnsAllFacilities() {
        FacilityEntity active = makeFacility(1L, "ACTIVE");
        FacilityEntity inactive = makeFacility(2L, "INACTIVE");
        Page<FacilityEntity> allPage = new PageImpl<>(Arrays.asList(active, inactive));

        when(facilityRepository.findByTenantId(eq(TENANT_UUID), any(Pageable.class)))
                .thenReturn(allPage);

        ResponseEntity<Map<String, Object>> response = snapshotController.facilitySnapshot(
                TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID, 0, 100, null);

        assertEquals(200, response.getStatusCodeValue());
        List<?> items = (List<?>) response.getBody().get("items");
        assertEquals(2, items.size());

        verify(facilityRepository).findByTenantId(eq(TENANT_UUID), any(Pageable.class));
    }

    @Test
    void facilitySnapshot_withBlankStatusFilter_returnsAllFacilities() {
        FacilityEntity active = makeFacility(1L, "ACTIVE");
        Page<FacilityEntity> allPage = new PageImpl<>(Collections.singletonList(active));

        when(facilityRepository.findByTenantId(eq(TENANT_UUID), any(Pageable.class)))
                .thenReturn(allPage);

        ResponseEntity<Map<String, Object>> response = snapshotController.facilitySnapshot(
                TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID, 0, 100, "  ");

        assertEquals(200, response.getStatusCodeValue());
        verify(facilityRepository).findByTenantId(eq(TENANT_UUID), any(Pageable.class));
    }

    @Test
    void facilitySnapshot_responseContainsRequiredFields() {
        FacilityEntity entity = makeFacility(10L, "ACTIVE");
        Page<FacilityEntity> page = new PageImpl<>(Collections.singletonList(entity));

        when(facilityRepository.findByTenantId(eq(TENANT_UUID), any(Pageable.class)))
                .thenReturn(page);

        ResponseEntity<Map<String, Object>> response = snapshotController.facilitySnapshot(
                TENANT_ID, POD_ID, REQUEST_ID, CORRELATION_ID, 0, 100, null);

        Map<String, Object> body = response.getBody();
        assertNotNull(body.get("as_of"));
        assertNotNull(body.get("cursor"));
        assertNotNull(body.get("limit"));
        assertNotNull(body.get("has_more"));
        assertNotNull(body.get("total"));
        assertNotNull(body.get("items"));

        Map<?, ?> item = (Map<?, ?>) ((List<?>) body.get("items")).get(0);
        assertNotNull(item.get("id"));
        assertNotNull(item.get("type"));
        assertNotNull(item.get("facility_code"));
        assertNotNull(item.get("name"));
        assertNotNull(item.get("status"));
    }
}
