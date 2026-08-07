package zw.gov.mohcc.impilo.indawo.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import zw.gov.mohcc.impilo.companion.context.RequestContext;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.indawo.core.PlaceLinkService;
import zw.gov.mohcc.impilo.indawo.domain.PlaceLinkEntity;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The steward gate on cross-registry place links, and what changed about it.
 *
 * <p>It was {@code Set.of("SYSTEM", "REGISTRY_ADMIN", "NATIONAL_ADMIN", "DATA_STEWARD")}. Only
 * {@code SYSTEM} is an actor type any client emits, so the three steward names never matched and
 * <b>no human steward could pass this gate at all</b> — the surface was machine-only by accident,
 * not by design. It now enforces {@code BACK_OFFICE_WRITERS}, so an admin-console operator can do
 * the stewarding the guard was named for, and the three role names are kept as recorded intent.</p>
 *
 * <p>These tests pin both directions of that: the operator who is now admitted, and the citizen who
 * still is not.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlaceLinkStewardGuardTest {

    @Mock
    private PlaceLinkService placeLinkService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new PlaceLinkController(placeLinkService)).build();
        // The controller resolves the companion v1.1 context before the actor gate; without it the
        // request fails on well-formedness and the test would prove nothing about authorization.
        RequestContextHolder.set(RequestContext.of(
                UUID.randomUUID().toString(), "test", UUID.randomUUID().toString(),
                UUID.randomUUID().toString(), null, null, null));
        when(placeLinkService.create(any(), any(), any(), any(), any()))
                .thenReturn(mock(PlaceLinkEntity.class));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.clear();
    }

    private static final String BODY = """
            {"leftRefType":"FACILITY","leftRefId":"%s","rightRefType":"SITE","rightRefId":"%s",
             "linkType":"CO_LOCATED"}
            """.formatted(UUID.randomUUID(), UUID.randomUUID());

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder createAs(String actorType) {
        var b = post("/internal/v1/place-links")
                .header("X-Tenant-ID", UUID.randomUUID().toString())
                .header("X-Actor-ID", "actor-1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY);
        return actorType == null ? b : b.header("X-Actor-Type", actorType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CITIZEN", "PROVIDER", "CAREGIVER"})
    void aPersonalOrClinicalSessionCannotMaintainPlaceLinks(String actorType) throws Exception {
        mockMvc.perform(createAs(actorType)).andExpect(status().isForbidden());
        verify(placeLinkService, never()).create(any(), any(), any(), any(), any());
    }

    @Test
    void aRequestWithNoActorTypeIsRefused() throws Exception {
        mockMvc.perform(createAs(null)).andExpect(status().isForbidden());
    }

    /**
     * The role name the guard was written for. It still does not pass — recording intent is not
     * enforcing it, and this test exists so that distinction cannot quietly erode.
     */
    @Test
    void theRecordedStewardRoleStillDoesNotSatisfyTheGate() throws Exception {
        mockMvc.perform(createAs("DATA_STEWARD")).andExpect(status().isForbidden());
    }

    /**
     * The behaviour change. Before this migration no human could pass, because every non-SYSTEM
     * member of the old set was unemittable.
     */
    @ParameterizedTest
    @ValueSource(strings = {"OPERATOR", "SYSTEM", "SERVICE"})
    void aBackOfficeWriterReachesTheHandler(String actorType) throws Exception {
        mockMvc.perform(createAs(actorType)).andExpect(status().isCreated());
        verify(placeLinkService).create(any(), any(), any(), any(), any());
    }
}
