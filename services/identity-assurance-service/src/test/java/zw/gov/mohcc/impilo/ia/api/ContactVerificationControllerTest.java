package zw.gov.mohcc.impilo.ia.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
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
import zw.gov.mohcc.impilo.ia.core.AttestationType;
import zw.gov.mohcc.impilo.ia.core.ContactVerificationService;
import zw.gov.mohcc.impilo.ia.core.ContactVerificationService.ContactVerificationView;
import zw.gov.mohcc.impilo.ia.core.ContactVerificationService.VerifiedChannel;
import zw.gov.mohcc.impilo.ia.persistence.entity.AttestationEntity;

@WebMvcTest(ContactVerificationController.class)
@Import(TestSecurityConfig.class)
class ContactVerificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ContactVerificationService contactVerificationService;

    @Test
    void postContactVerified_returns201WithStatusView() throws Exception {
        AttestationEntity saved = new AttestationEntity();
        saved.setId(1L);
        saved.setTenantId(UUID.randomUUID());
        saved.setActorId("acct-1");
        saved.setAttestationType(AttestationType.CONTACT_VERIFIED);
        saved.setOutcome(AttestationOutcome.VERIFIED);

        when(contactVerificationService.recordContactVerified(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(saved);
        when(contactVerificationService.getContactVerification(any(), any()))
                .thenReturn(new ContactVerificationView("acct-1", true,
                        List.of(new VerifiedChannel("PHONE", "+263*******67", "OTP",
                                OffsetDateTime.now()))));

        mockMvc.perform(post("/internal/v1/attestations/contact-verified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "experience-bff")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .content("{\"accountId\":\"acct-1\",\"channel\":\"PHONE\","
                                + "\"maskedValue\":\"+263*******67\",\"method\":\"OTP\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.contactVerified").value(true))
                .andExpect(jsonPath("$.data.channels[0].channel").value("PHONE"));
    }

    @Test
    void postContactVerified_validationErrorReturns400() throws Exception {
        when(contactVerificationService.recordContactVerified(
                any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("channel must be one of [PHONE, EMAIL]"));

        mockMvc.perform(post("/internal/v1/attestations/contact-verified")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "experience-bff")
                        .header("X-Correlation-Id", UUID.randomUUID().toString())
                        .content("{\"accountId\":\"acct-1\",\"channel\":\"FAX\","
                                + "\"maskedValue\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getContactVerification_returns200() throws Exception {
        when(contactVerificationService.getContactVerification(any(), any()))
                .thenReturn(new ContactVerificationView("acct-1", false, List.of()));

        mockMvc.perform(get("/internal/v1/attestations/contact-verified/acct-1")
                        .header("X-Tenant-Id", UUID.randomUUID().toString())
                        .header("X-Actor-Id", "experience-bff")
                        .header("X-Correlation-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contactVerified").value(false));
    }
}
