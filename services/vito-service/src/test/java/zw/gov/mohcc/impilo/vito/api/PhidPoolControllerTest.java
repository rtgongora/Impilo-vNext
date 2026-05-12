package zw.gov.mohcc.impilo.vito.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.core.PhidPoolService;
import zw.gov.mohcc.impilo.vito.persistence.entity.PhidPoolEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PhidPoolControllerTest {

    @Mock
    private PhidPoolService phidPoolService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID correlationId;

    @BeforeEach
    void setUp() {
        PhidPoolController controller = new PhidPoolController(phidPoolService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
        correlationId = UUID.randomUUID();

        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(),
                "system-actor-1",
                "SYSTEM",
                "PATIENT_REGISTRATION",
                null,
                correlationId,
                null,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL
        ));
    }

    @AfterEach
    void tearDown() {
        TrustContextHolder.clear();
    }

    @Test
    void allocate_validRequest_returns201WithEntries() throws Exception {
        String facilityId = "facility-abc";
        int count = 5;

        List<PhidPoolService.PhidPoolEntry> entries = List.of(
                new PhidPoolService.PhidPoolEntry(UUID.randomUUID(), "123A4561"),
                new PhidPoolService.PhidPoolEntry(UUID.randomUUID(), "456B7892"),
                new PhidPoolService.PhidPoolEntry(UUID.randomUUID(), "789C0123"),
                new PhidPoolService.PhidPoolEntry(UUID.randomUUID(), "012D3454"),
                new PhidPoolService.PhidPoolEntry(UUID.randomUUID(), "345E6785")
        );

        when(phidPoolService.allocate(eq(facilityId), eq(count))).thenReturn(entries);

        String requestBody = objectMapper.writeValueAsString(
                new PhidPoolController.AllocateRequest(facilityId, count));

        mockMvc.perform(post("/v1/phid-pool/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.facilityId").value(facilityId))
                .andExpect(jsonPath("$.data.allocated").value(5))
                .andExpect(jsonPath("$.data.entries").isArray())
                .andExpect(jsonPath("$.data.entries.length()").value(5));
    }

    @Test
    void allocate_nonSystemActor_returns403() throws Exception {
        TrustContextHolder.set(new TrustContext(
                UUID.randomUUID(),
                "human-actor-1",
                "CLINICIAN",
                "PATIENT_CARE",
                null,
                correlationId,
                null,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL
        ));

        String requestBody = objectMapper.writeValueAsString(
                new PhidPoolController.AllocateRequest("facility-abc", 10));

        mockMvc.perform(post("/v1/phid-pool/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isForbidden());
    }

    @Test
    void claim_availableEntry_returns200() throws Exception {
        UUID healthId = UUID.randomUUID();
        String mrsPatientId = "mrs-patient-001";

        PhidPoolEntity entity = new PhidPoolEntity();
        entity.setHealthId(healthId);
        entity.setPhid("123A4561");
        entity.setStatus("ASSIGNED");
        entity.setAssignedAt(OffsetDateTime.now());
        entity.setMrsPatientId(mrsPatientId);

        when(phidPoolService.claim(eq(healthId), eq(mrsPatientId))).thenReturn(entity);

        String requestBody = objectMapper.writeValueAsString(
                new PhidPoolController.ClaimRequest(healthId, mrsPatientId));

        mockMvc.perform(post("/v1/phid-pool/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.healthId").value(healthId.toString()))
                .andExpect(jsonPath("$.data.phid").value("123A4561"))
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"));
    }

    @Test
    void claim_alreadyAssigned_returns409() throws Exception {
        UUID healthId = UUID.randomUUID();

        when(phidPoolService.claim(any(UUID.class), anyString()))
                .thenThrow(new AccessDeniedException("Pool entry is not AVAILABLE (status=ASSIGNED)"));

        String requestBody = objectMapper.writeValueAsString(
                new PhidPoolController.ClaimRequest(healthId, "mrs-patient-002"));

        mockMvc.perform(post("/v1/phid-pool/claim")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict());
    }

    @Test
    void getStats_returnsCorrectCounts() throws Exception {
        String facilityId = "facility-xyz";
        PhidPoolService.PhidPoolStats stats = new PhidPoolService.PhidPoolStats(850L, 150L, 1000L);

        when(phidPoolService.getStats(eq(facilityId))).thenReturn(stats);

        mockMvc.perform(get("/v1/phid-pool/stats")
                        .param("facilityId", facilityId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.facilityId").value(facilityId))
                .andExpect(jsonPath("$.data.available").value(850))
                .andExpect(jsonPath("$.data.assigned").value(150))
                .andExpect(jsonPath("$.data.total").value(1000));
    }
}
