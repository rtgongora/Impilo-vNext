package zw.gov.mohcc.impilo.pct.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * The medicine list, and the one property that matters more than any of the parsing.
 *
 * <p>Interaction checking, duplicate-therapy detection and deprescribing all read this list. An
 * empty list is therefore an affirmative clinical claim — "nothing to interact with" — and the
 * dangerous failure is not an error, it is an error that becomes an empty list.</p>
 */
class OrosMedicationIntegrationTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final String CPID = "CPID-9";
    private static final String URL = "http://oros.test/v1/prescriptions/patient/" + CPID;

    private static ServiceTokenProvider configuredProvider() {
        return new ServiceTokenProvider(new com.fasterxml.jackson.databind.ObjectMapper(), "http://kc.test", "svc", "secret") {
            @Override public boolean isConfigured() { return true; }
            @Override public Optional<String> accessToken() { return Optional.of("test-token"); }
        };
    }

    private static ServiceTokenProvider unconfiguredProvider() {
        return new ServiceTokenProvider(new com.fasterxml.jackson.databind.ObjectMapper(), "http://kc.test", null, null) {
            @Override public boolean isConfigured() { return false; }
            @Override public Optional<String> accessToken() { return Optional.empty(); }
        };
    }

    // ── Unreachable is never "takes no medicines" ───────────────────────────────

    /**
     * The property the class exists to hold. Returning an empty list here would give an
     * interaction checker a confident all-clear produced by a system that could not ask.
     */
    @Test
    void anUnreachableOrosYieldsAnAbsentFactNotAnEmptyMedicineList() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL)).andRespond(withServerError());

        List<String> codes = new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID);

        assertThat(codes).isNull();
    }

    @Test
    void anUnauthenticatedCallYieldsAnAbsentFactNotAnEmptyMedicineList() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThat(new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID)).isNull();
    }

    /** An unconfigured token is indistinguishable from unknown and must not read as "none". */
    @Test
    void anUnconfiguredTokenDoesNotCallOrosAndReportsTheFactAbsent() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();

        assertThat(new OrosMedicationIntegration(rt, unconfiguredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID)).isNull();
        server.verify();  // no request was made
    }

    // ── The call itself ─────────────────────────────────────────────────────────

    /** Trust headers are context, not credentials. Without the bearer this is a 401. */
    @Test
    void carriesABearerTokenAndTheTrustHeaders() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-token"))
                .andExpect(header("X-Tenant-ID", TENANT.toString()))
                .andExpect(header("X-Actor-Type", "SERVICE"))
                .andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID);
        server.verify();
    }

    @Test
    void readsDistinctCodesFromWhicheverCodedFieldAPrescriptionCarries() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"data":[
                  {"medication_code":"C09AA05","status":"ACTIVE"},
                  {"atcCode":"J01CA04","status":"ACTIVE"},
                  {"medication_code":"C09AA05","status":"ACTIVE"}
                ]}""", MediaType.APPLICATION_JSON));

        assertThat(new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID))
                .containsExactly("C09AA05", "J01CA04");
    }

    @Test
    void excludesMedicinesThePatientIsNoLongerTaking() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("""
                {"data":[
                  {"medication_code":"A","status":"ACTIVE"},
                  {"medication_code":"B","status":"STOPPED"},
                  {"medication_code":"C","status":"CANCELLED"}
                ]}""", MediaType.APPLICATION_JSON));

        assertThat(new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID)).containsExactly("A");
    }

    /**
     * An unrecorded status counts as current. The cost of a spurious interaction warning is an
     * explanation; the cost of a missed one is the interaction.
     */
    @Test
    void treatsAMedicineWithNoRecordedStatusAsStillBeingTaken() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"data\":[{\"medication_code\":\"A\"}]}", MediaType.APPLICATION_JSON));

        assertThat(new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID)).containsExactly("A");
    }

    /** An answered call with nothing on it is a real empty list, and stays one. */
    @Test
    void anAnsweredCallWithNoPrescriptionsIsGenuinelyAnEmptyList() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"data\":[]}", MediaType.APPLICATION_JSON));

        assertThat(new OrosMedicationIntegration(rt, configuredProvider(), "http://oros.test")
                .currentMedicationCodes(TENANT, CPID)).isEmpty();
    }
}
