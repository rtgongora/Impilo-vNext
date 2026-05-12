package zw.gov.mohcc.impilo.varapi.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.ProviderRepository;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class SnapshotControllerTest {

    @Mock
    private ProviderRepository providerRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        SnapshotController controller = new SnapshotController(providerRepository);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    private ProviderEntity buildProvider(String status, String publicId) {
        ProviderEntity e = new ProviderEntity();
        e.setId(1L);
        e.setProviderPublicId(publicId);
        e.setImpiloHealthId(UUID.randomUUID());
        e.setGivenName("Alice");
        e.setMiddleName("M");
        e.setFamilyName("Smith");
        e.setProfession("DOCTOR");
        e.setCadre("SPECIALIST");
        e.setPracticeNumber("MD-1234");
        e.setStatus(status);
        e.setLicenceStatus("VALID");
        e.setVersion(1);
        return e;
    }

    @Test
    void snapshot_returnsAllRequiredFields() throws Exception {
        ProviderEntity provider = buildProvider("ACTIVE", "VA-0000000001");
        Page<ProviderEntity> page = new PageImpl<>(List.of(provider));
        when(providerRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/v1/snapshots/providers")
                        .header("X-Tenant-ID", "tenant-1")
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].provider_public_id").value("VA-0000000001"))
                .andExpect(jsonPath("$.items[0].impilo_health_id").isNotEmpty())
                .andExpect(jsonPath("$.items[0].given_name").value("Alice"))
                .andExpect(jsonPath("$.items[0].middle_name").value("M"))
                .andExpect(jsonPath("$.items[0].family_name").value("Smith"))
                .andExpect(jsonPath("$.items[0].profession").value("DOCTOR"))
                .andExpect(jsonPath("$.items[0].cadre").value("SPECIALIST"))
                .andExpect(jsonPath("$.items[0].practice_number").value("MD-1234"))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].licence_status").value("VALID"))
                .andExpect(jsonPath("$.items[0]").value(hasKey("primary_council_id")))
                .andExpect(jsonPath("$.items[0].version").value(1))
                .andExpect(jsonPath("$.items[0]").value(hasKey("updated_at")));
    }

    @Test
    void snapshot_withStatusActive_returnsOnlyActiveProviders() throws Exception {
        ProviderEntity active = buildProvider("ACTIVE", "VA-0000000001");
        Page<ProviderEntity> page = new PageImpl<>(List.of(active));
        when(providerRepository.findByStatus(eq("ACTIVE"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/v1/snapshots/providers")
                        .param("status", "ACTIVE")
                        .header("X-Tenant-ID", "tenant-1")
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("ACTIVE"));
    }

    @Test
    void snapshot_withStatusInactive_returnsOnlyInactiveProviders() throws Exception {
        ProviderEntity inactive = buildProvider("INACTIVE", "VA-0000000002");
        Page<ProviderEntity> page = new PageImpl<>(List.of(inactive));
        when(providerRepository.findByStatus(eq("INACTIVE"), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/v1/snapshots/providers")
                        .param("status", "INACTIVE")
                        .header("X-Tenant-ID", "tenant-1")
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].status").value("INACTIVE"));
    }

    @Test
    void snapshot_withoutStatusParam_returnsAllProviders() throws Exception {
        ProviderEntity active = buildProvider("ACTIVE", "VA-0000000001");
        ProviderEntity inactive = buildProvider("INACTIVE", "VA-0000000002");
        Page<ProviderEntity> page = new PageImpl<>(List.of(active, inactive));
        when(providerRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/v1/snapshots/providers")
                        .header("X-Tenant-ID", "tenant-1")
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)));
    }

    @Test
    void snapshot_paginationMetadataIsPresent() throws Exception {
        ProviderEntity provider = buildProvider("ACTIVE", "VA-0000000001");
        Page<ProviderEntity> page = new PageImpl<>(List.of(provider));
        when(providerRepository.findAll(any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/internal/v1/snapshots/providers")
                        .param("cursor", "0")
                        .param("limit", "50")
                        .header("X-Tenant-ID", "tenant-1")
                        .header("X-Pod-ID", "pod-1")
                        .header("X-Request-ID", "req-1")
                        .header("X-Correlation-ID", "corr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(0))
                .andExpect(jsonPath("$.limit").value(50))
                .andExpect(jsonPath("$.has_more").value(false))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.as_of").isNotEmpty());
    }
}
