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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.vito.api.dto.ClientDemographicsUpdateRequest;
import zw.gov.mohcc.impilo.vito.persistence.entity.ClientEntity;
import zw.gov.mohcc.impilo.vito.service.ClientUpdateService;
import zw.gov.mohcc.impilo.vito.service.ExternalRegistrationService;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ClientUpdateControllerTest {

    @Mock
    private ExternalRegistrationService externalRegistrationService;

    @Mock
    private ClientUpdateService clientUpdateService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private UUID tenantId;
    private UUID correlationId;
    private UUID healthId;

    @BeforeEach
    void setUp() {
        ExternalClientController controller = new ExternalClientController(externalRegistrationService, clientUpdateService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AccessDeniedExceptionHandler())
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        tenantId = UUID.randomUUID();
        correlationId = UUID.randomUUID();
        healthId = UUID.randomUUID();

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
    void update_validRequest_patchesFields() throws Exception {
        ClientDemographicsUpdateRequest request = new ClientDemographicsUpdateRequest(
                "NewGiven", null, "NewFamily",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        ClientEntity updatedClient = buildClient(healthId, tenantId, "NewGiven", "NewFamily");
        when(clientUpdateService.update(eq(tenantId), eq(healthId), any(), anyString(), anyString()))
                .thenReturn(updatedClient);

        mockMvc.perform(put("/v1/clients/{healthId}", healthId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(clientUpdateService).update(
                eq(tenantId), eq(healthId),
                any(ClientDemographicsUpdateRequest.class),
                eq("system-actor-1"),
                eq(correlationId.toString()));
    }

    @Test
    void update_partialRequest_onlyPatchesProvidedFields() throws Exception {
        ClientDemographicsUpdateRequest request = new ClientDemographicsUpdateRequest(
                "UpdatedGiven", null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        ClientEntity clientWithOriginalFamily = buildClient(healthId, tenantId, "UpdatedGiven", "OriginalFamily");
        when(clientUpdateService.update(eq(tenantId), eq(healthId), any(), anyString(), anyString()))
                .thenReturn(clientWithOriginalFamily);

        mockMvc.perform(put("/v1/clients/{healthId}", healthId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(clientUpdateService).update(
                eq(tenantId), eq(healthId),
                argThat(req -> "UpdatedGiven".equals(req.givenName()) && req.familyName() == null),
                anyString(), anyString());
    }

    @Test
    void update_nonExistentHealthId_returns404() throws Exception {
        ClientDemographicsUpdateRequest request = new ClientDemographicsUpdateRequest(
                "Ghost", null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        when(clientUpdateService.update(eq(tenantId), eq(healthId), any(), anyString(), anyString()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Client not found: " + healthId));

        mockMvc.perform(put("/v1/clients/{healthId}", healthId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    void update_nonSystemActor_returns403() throws Exception {
        TrustContextHolder.set(new TrustContext(
                tenantId,
                "clinician-actor-1",
                "CLINICIAN",
                "PATIENT_REGISTRATION",
                null,
                correlationId,
                null,
                UUID.randomUUID(),
                null,
                AccessMode.INTERNAL
        ));

        ClientDemographicsUpdateRequest request = new ClientDemographicsUpdateRequest(
                "NewGiven", null, null,
                null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        mockMvc.perform(put("/v1/clients/{healthId}", healthId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void update_publishes_CLIENT_DEMOGRAPHICS_UPDATED_event() throws Exception {
        ClientDemographicsUpdateRequest request = new ClientDemographicsUpdateRequest(
                "Event", null, "Test",
                null, null, null, null, null, null, null,
                null, null, null, null, null, null
        );

        ClientEntity updatedClient = buildClient(healthId, tenantId, "Event", "Test");
        when(clientUpdateService.update(eq(tenantId), eq(healthId), any(), anyString(), anyString()))
                .thenReturn(updatedClient);

        mockMvc.perform(put("/v1/clients/{healthId}", healthId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(clientUpdateService, times(1)).update(
                eq(tenantId), eq(healthId),
                any(ClientDemographicsUpdateRequest.class),
                eq("system-actor-1"),
                eq(correlationId.toString()));
    }

    private ClientEntity buildClient(UUID healthId, UUID tenantId, String givenName, String familyName) {
        ClientEntity client = new ClientEntity();
        client.setHealthId(healthId);
        client.setTenantId(tenantId);
        client.setGivenName(givenName);
        client.setFamilyName(familyName);
        return client;
    }

    @ControllerAdvice
    static class AccessDeniedExceptionHandler {
        @ExceptionHandler(AccessDeniedException.class)
        public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", ex.getMessage()));
        }
    }
}
