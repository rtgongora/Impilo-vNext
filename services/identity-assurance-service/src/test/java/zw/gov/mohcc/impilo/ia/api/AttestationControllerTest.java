package zw.gov.mohcc.impilo.ia.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import zw.gov.mohcc.impilo.ia.config.TestSecurityConfig;
import zw.gov.mohcc.impilo.ia.core.AttestationOutcome;
import zw.gov.mohcc.impilo.ia.core.AttestationService;
import zw.gov.mohcc.impilo.ia.core.AttestationType;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;

@WebMvcTest(AttestationController.class)
@Import(TestSecurityConfig.class)
class AttestationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AttestationService attestationService;

    @Test
    void postAttestation_returns201() throws Exception {
        AttestationEntity saved = new AttestationEntity();
        saved.setId(1L);
        saved.setTenantId(UUID.randomUUID());
        saved.setActorId("user-1");
        saved.setAttestationType(AttestationType.DEVICE_BINDING);
        saved.setOutcome(AttestationOutcome.VERIFIED);
        saved.setConfidence(new BigDecimal("0.9500"));
        saved.setRecordedBy("user-1");

        when(attestationService.recordAttestation(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(saved);

        mockMvc.perform(post("/internal/v1/attestations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "user-1")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .content("{\"attestationType\":\"DEVICE_BINDING\",\"evidence\":\"{}\",\"outcome\":\"VERIFIED\",\"confidence\":0.95}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getAttestations_returns200() throws Exception {
        when(attestationService.listAttestations(any())).thenReturn(List.of());

        mockMvc.perform(get("/internal/v1/attestations")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "user-1")
                        .header("X-Correlation-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
