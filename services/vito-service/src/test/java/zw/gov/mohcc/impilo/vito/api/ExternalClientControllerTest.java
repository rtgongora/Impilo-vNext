package zw.gov.mohcc.impilo.vito.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationRequest;
import zw.gov.mohcc.impilo.vito.api.dto.ExternalClientRegistrationResponse;
import zw.gov.mohcc.impilo.vito.service.ExternalRegistrationService;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ExternalClientControllerTest {

    @Mock
    private ExternalRegistrationService externalRegistrationService;

    @Mock
    private zw.gov.mohcc.impilo.vito.service.ClientUpdateService clientUpdateService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID tenantId;
    private UUID correlationId;

    @BeforeEach
    void setUp() {
        ExternalClientController controller = new ExternalClientController(externalRegistrationService, clientUpdateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        tenantId = UUID.randomUUID();
        correlationId = UUID.randomUUID();

        TrustContextHolder.set(new TrustContext(
                tenantId,
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
    void registerExternal_newPatient_returns201WithCreated() throws Exception {
        ExternalClientRegistrationRequest request = new ExternalClientRegistrationRequest(
                "John", null, "Doe",
                LocalDate.of(1990, 1, 15),
                "MALE", "63-123456A-00", "+263771234567",
                "123 Main St", "Harare", "Harare", "Harare Province",
                "MRS", "mrs-patient-001", "facility-abc",
                null, null
        );

        UUID healthId = UUID.randomUUID();
        ExternalClientRegistrationResponse response = new ExternalClientRegistrationResponse(
                healthId, "IMP-001-2024", "REGISTERED", "CREATED", correlationId.toString()
        );

        when(externalRegistrationService.register(
                eq(tenantId), any(ExternalClientRegistrationRequest.class),
                anyString(), eq("SYSTEM"), eq(correlationId.toString())))
                .thenReturn(response);

        mockMvc.perform(post("/v1/clients/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncStatus").value("CREATED"))
                .andExpect(jsonPath("$.data.status").value("REGISTERED"))
                .andExpect(jsonPath("$.data.healthId").value(healthId.toString()));
    }

    @Test
    void registerExternal_duplicateByNationalId_returns200WithExisting() throws Exception {
        ExternalClientRegistrationRequest request = new ExternalClientRegistrationRequest(
                "Jane", null, "Smith",
                LocalDate.of(1985, 6, 20),
                "FEMALE", "63-999999B-00", "+263771000000",
                null, null, null, null,
                "MRS", "mrs-patient-002", null,
                null, null
        );

        UUID existingHealthId = UUID.randomUUID();
        ExternalClientRegistrationResponse response = new ExternalClientRegistrationResponse(
                existingHealthId, "IMP-002-2024", "DEDUPLICATED", "EXISTING", correlationId.toString()
        );

        when(externalRegistrationService.register(
                eq(tenantId), any(ExternalClientRegistrationRequest.class),
                anyString(), eq("SYSTEM"), eq(correlationId.toString())))
                .thenReturn(response);

        mockMvc.perform(post("/v1/clients/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncStatus").value("EXISTING"))
                .andExpect(jsonPath("$.data.status").value("DEDUPLICATED"))
                .andExpect(jsonPath("$.data.healthId").value(existingHealthId.toString()));
    }

    @Test
    void registerExternal_probableDuplicate_returns202WithPendingReview() throws Exception {
        ExternalClientRegistrationRequest request = new ExternalClientRegistrationRequest(
                "Tendai", null, "Moyo",
                LocalDate.of(1992, 3, 10),
                "MALE", null, "+263771555555",
                null, "Bulawayo", null, null,
                "MRS", "mrs-patient-003", null,
                null, null
        );

        UUID provisionalHealthId = UUID.randomUUID();
        ExternalClientRegistrationResponse response = new ExternalClientRegistrationResponse(
                provisionalHealthId, null, "PROVISIONAL_REVIEW", "PENDING_REVIEW", correlationId.toString()
        );

        when(externalRegistrationService.register(
                eq(tenantId), any(ExternalClientRegistrationRequest.class),
                anyString(), eq("SYSTEM"), eq(correlationId.toString())))
                .thenReturn(response);

        mockMvc.perform(post("/v1/clients/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncStatus").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.data.status").value("PROVISIONAL_REVIEW"));
    }

    @Test
    void registerExternal_missingGivenName_returns400() throws Exception {
        String invalidJson = """
                {
                  "givenName": "",
                  "familyName": "Doe",
                  "dateOfBirth": "1990-01-15",
                  "sex": "MALE",
                  "sourceSystem": "MRS"
                }
                """;

        mockMvc.perform(post("/v1/clients/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_withPreAllocatedHealthId_usesPooledUuid() throws Exception {
        UUID preAllocatedHealthId = UUID.randomUUID();
        ExternalClientRegistrationRequest request = new ExternalClientRegistrationRequest(
                "Mary", null, "Ndlovu",
                LocalDate.of(1995, 8, 12),
                "FEMALE", null, null,
                null, null, null, null,
                "MRS", "mrs-patient-004", "facility-xyz",
                preAllocatedHealthId, null
        );

        ExternalClientRegistrationResponse response = new ExternalClientRegistrationResponse(
                preAllocatedHealthId, "IMP-004-2024", "REGISTERED", "CREATED", correlationId.toString()
        );

        when(externalRegistrationService.register(
                eq(tenantId), any(ExternalClientRegistrationRequest.class),
                anyString(), eq("SYSTEM"), eq(correlationId.toString())))
                .thenReturn(response);

        mockMvc.perform(post("/v1/clients/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.healthId").value(preAllocatedHealthId.toString()))
                .andExpect(jsonPath("$.data.syncStatus").value("CREATED"));
    }

    @Test
    void register_withMrsPhid_storesAsCrossReference() throws Exception {
        String mrsPhid = "123B4561";
        ExternalClientRegistrationRequest request = new ExternalClientRegistrationRequest(
                "Chiedza", null, "Mutasa",
                LocalDate.of(1988, 4, 25),
                "FEMALE", null, null,
                null, "Harare", null, null,
                "MRS", "mrs-patient-005", "facility-def",
                null, mrsPhid
        );

        UUID healthId = UUID.randomUUID();
        ExternalClientRegistrationResponse response = new ExternalClientRegistrationResponse(
                healthId, "IMP-005-2024", "REGISTERED", "CREATED", correlationId.toString()
        );

        ArgumentCaptor<ExternalClientRegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(ExternalClientRegistrationRequest.class);

        when(externalRegistrationService.register(
                eq(tenantId), requestCaptor.capture(),
                anyString(), eq("SYSTEM"), eq(correlationId.toString())))
                .thenReturn(response);

        mockMvc.perform(post("/v1/clients/external")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.syncStatus").value("CREATED"));

        assertThat(requestCaptor.getValue().mrsPhid()).isEqualTo(mrsPhid);
    }
}
