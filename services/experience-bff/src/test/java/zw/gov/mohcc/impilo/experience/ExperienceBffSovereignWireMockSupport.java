package zw.gov.mohcc.impilo.experience;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.springframework.test.context.DynamicPropertyRegistry;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

/**
 * In-process stubs for sovereign HTTP dependencies used by JVM integration tests
 * (pharmacy-service, TUSO staffing, marketplace) so CI does not require live services.
 */
public final class ExperienceBffSovereignWireMockSupport {

    private static final WireMockServer SERVER = new WireMockServer(wireMockConfig().dynamicPort());
    private static volatile boolean started;

    private ExperienceBffSovereignWireMockSupport() {}

    public static void register(DynamicPropertyRegistry registry) {
        synchronized (SERVER) {
            if (!started) {
                SERVER.start();
                stubPharmacy();
                stubVashandiStaffing();
                stubMarketplace();
                stubSupport();
                stubPct();
                stubProviderTier2();
                started = true;
            }
        }
        String base = "http://localhost:" + SERVER.port();
        registry.add("impilo.services.pharmacy-base-url", () -> base);
        registry.add("impilo.services.tuso-base-url", () -> base);
        registry.add("impilo.services.msika-flow-base-url", () -> base);
        registry.add("impilo.services.support-base-url", () -> base);
        registry.add("impilo.services.pct-base-url", () -> base);
        // Staffing repointed off tuso to vashandi (V009): the BFF now calls
        // /v1/internal/vashandi/staffing/*, so vashandi must resolve to this WireMock server or the
        // calls escape to a real host and 500/empty.
        registry.add("impilo.services.vashandi-base-url", () -> base);
        // The rota composes display names against varapi; point it here too so the composition legs
        // (vashandi profile -> health id, varapi health id -> display facts) run through the stub.
        registry.add("impilo.services.varapi-base-url", () -> base);
        // Deliberately NOT registered: impilo.services.inpatient-base-url. RbacIntegrationTest is the
        // only test that calls /internal/v1/beds/wards, and it asserts the honest 502
        // INPATIENT_UNAVAILABLE that proves the request reached the sovereign client. Pointing
        // inpatient at this stub fakes the service into availability and turns that 502 into a 200.
        registry.add("impilo.services.oros-base-url", () -> base);
        registry.add("impilo.services.reporting-base-url", () -> base);
    }

    /** The workforce profile the seeded on-call assignment is keyed on, and its resolved facts. */
    static final String SEED_PROFILE_ID = "aa000000-0000-4000-8000-0000000000a1";
    static final String SEED_HEALTH_ID = "cc000000-0000-4000-8000-0000000000c1";
    static final String SEED_DISPLAY_NAME = "Dr Rumbi Chikova";
    static final String SEED_PHONE = "+263773000111";

    private static void stubPharmacy() {
        SERVER.stubFor(get(urlPathMatching("/v1/prescriptions/patient/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
    }

    /**
     * Vashandi staffing stubs (V009). The BFF was repointed off tuso {@code /v1/staffing/*} — which
     * nothing served — to vashandi {@code /v1/internal/vashandi/staffing/*}, and the swap contract
     * moved from typed names to shift/profile ids. These stubs return vashandi's real response
     * shapes ({@code shift}, {@code on-call-assignment}, {@code shift-swap-request}) so the test
     * asserts the contract the estate actually speaks, not the retired tuso one.
     */
    private static void stubVashandiStaffing() {
        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/staffing/roster-week.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));

        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/staffing/on-call/swaps.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":[{"type":"shift-swap-request","id":"swap-seed-1",\
                                "attributes":{"status":"PENDING"}}]}\
                                """)));

        // The swap decision is a PATCH downstream (vashandi @PatchMapping), where the retired tuso
        // path used POST — matching POST here would leave the decision unstubbed.
        SERVER.stubFor(patch(urlPathMatching("/v1/internal/vashandi/staffing/on-call/swaps/[^/]+"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"type":"shift-swap-request",\
                                "id":"a1b2c3d4-0001-4000-8000-000000000099",\
                                "attributes":{"status":"APPROVED"}}}\
                                """)));

        SERVER.stubFor(post(urlPathMatching("/v1/internal/vashandi/staffing/on-call/swaps.*"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"type":"shift-swap-request",\
                                "id":"a1b2c3d4-0001-4000-8000-000000000099",\
                                "attributes":{"status":"PENDING"}}}\
                                """)));

        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/staffing/on-call"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(("""
                                {"data":[{"type":"on-call-assignment","id":"2026-04-06|General",\
                                "attributes":{"assignment_date":"2026-04-06","specialty":"General",\
                                "primary_workforce_profile_id":"%s",\
                                "primary_staff_reference":"PW-004821","primary_phone":null,\
                                "backup_workforce_profile_id":null,"backup_staff_reference":null,\
                                "backup_phone":null}}]}\
                                """).formatted(SEED_PROFILE_ID))));

        // Composition leg 1: vashandi maps the rostered profile to a person anchor (nothing more).
        SERVER.stubFor(get(urlPathMatching("/v1/internal/vashandi/workforce-profiles/identity-refs.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(("""
                                {"data":[{"workforce_profile_id":"%s","health_id":"%s",\
                                "provider_worker_id":"PW-004821"}]}\
                                """).formatted(SEED_PROFILE_ID, SEED_HEALTH_ID))));

        // Composition leg 2: varapi answers the display facts for that anchor.
        SERVER.stubFor(post(urlPathMatching("/v1/internal/providers/by-health-id/resolve-display"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(("""
                                {"data":[{"healthId":"%s","resolved":true,"providerPublicId":"PUB-1",\
                                "displayName":"%s","phone":"%s","profession":"MEDICAL_PRACTITIONER",\
                                "cadre":"REGISTRAR"}]}\
                                """).formatted(SEED_HEALTH_ID, SEED_DISPLAY_NAME, SEED_PHONE))));
    }

    private static void stubMarketplace() {
        SERVER.stubFor(post(urlPathMatching("/v1/orders"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"type":"MarketplaceOrder","id":"ord-wm-1",\
                                "attributes":{"status":"SUBMITTED"}}\
                                """)));
    }

    private static void stubPct() {
        SERVER.stubFor(post(urlPathMatching("/v1/journeys/.*/encounter/start"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"type":"Encounter","id":"42",\
                                "attributes":{"status":"IN_PROGRESS"}}\
                                """)));

        SERVER.stubFor(post(urlPathMatching("/v1/encounters/.*/complete"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"type":"Encounter","id":"42",\
                                "attributes":{"status":"COMPLETED"}}\
                                """)));

        SERVER.stubFor(get(urlPathMatching("/v1/patient/.*/timeline"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));
    }

    private static void stubSupport() {
        SERVER.stubFor(get(urlPathMatching("/internal/v1/support/tickets.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
    }

    /**
     * Mobile-provider tier-2 verticals used by MobileProviderTier2ResponseShapeIT:
     * OROS lab orders, reporting runs, single-prescription five-rights + dispense, and
     * PCT journey lookup. Concrete matchers only; realistic JSON:API-style payloads. The
     * single-prescription stub carries the {@code attributes} PharmacyFiveRightsVerifier
     * checks (status + patient + drug + dose + route + frequency) so a five-rights
     * verification passes for the deterministic prescription id the test sends.
     */
    private static void stubProviderTier2() {
        // OROS — resulted orders for a patient (labs/results list). More specific than the
        // patient-orders path below, so register it first.
        SERVER.stubFor(get(urlPathMatching("/v1/orders/patient/[^/]+/results"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
        // OROS — patient lab orders (labs list): GET /v1/orders/patient/{cpid}
        SERVER.stubFor(get(urlPathMatching("/v1/orders/patient/[^/]+"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
        // OROS — single lab order (labs get): GET /v1/orders/{uuid}
        SERVER.stubFor(get(urlPathMatching("/v1/orders/[0-9a-fA-F-]{36}"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"type\":\"LabOrder\",\"id\":\"lab-order-1\",\"attributes\":{\"status\":\"ORDERED\"}}}")));
        // OROS — place lab order (labs create): POST /v1/orders
        SERVER.stubFor(post(urlPathEqualTo("/v1/orders"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"type\":\"LabOrder\",\"id\":\"lab-order-1\",\"attributes\":{\"status\":\"ORDERED\"}}}")));
        // OROS — acknowledge a resulted order: POST /v1/orders/{uuid}/ack
        SERVER.stubFor(post(urlPathMatching("/v1/orders/[0-9a-fA-F-]{36}/ack"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"type\":\"LabOrder\",\"id\":\"lab-order-1\",\"attributes\":{\"status\":\"ACKNOWLEDGED\"}}}")));

        // Reporting — tenant-wide runs (reports list) + a named report run (facility KPIs)
        SERVER.stubFor(get(urlPathEqualTo("/internal/v1/reports/tenant-runs"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[{\"id\":\"run-1\",\"category\":\"CLINICAL\",\"status\":\"COMPLETED\"}]}")));
        SERVER.stubFor(post(urlPathMatching("/internal/v1/reports/[^/]+/run"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"report_id\":\"facility-kpis\",\"status\":\"COMPLETED\",\"entries\":[{\"key\":\"total_visits\",\"value\":42}]}}")));

        // Pharmacy — worklist (pending), single prescription (five-rights), dispense
        SERVER.stubFor(get(urlPathEqualTo("/v1/worklists"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":[]}")));
        SERVER.stubFor(get(urlPathMatching("/v1/prescriptions/[0-9a-fA-F-]{36}"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"id":"rx-1","attributes":{"status":"ACTIVE",\
                                "patient_id":"pat-verify-1","medication_name":"Amoxicillin",\
                                "dosage":"500mg","route":"ORAL","frequency":"TID"}}}\
                                """)));
        SERVER.stubFor(post(urlPathMatching("/v1/prescriptions/[0-9a-fA-F-]{36}/dispense"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"data\":{\"id\":\"rx-1\",\"attributes\":{\"status\":\"DISPENSED\"}}}")));

        // PCT — journey lookup (triage list). The BFF reads the journey's "triage" node,
        // so the payload must carry one.
        SERVER.stubFor(get(urlPathMatching("/v1/journeys/[^/]+"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"data":{"id":"journey-1","triage":{"acuity":2,"status":"COMPLETE",\
                                "triaged_by":"provider-1"}}}\
                                """)));
        // PCT — record triage: POST /v1/journeys/{id}/triage
        SERVER.stubFor(post(urlPathMatching("/v1/journeys/[^/]+/triage"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"acuity\":2,\"status\":\"COMPLETE\",\"triaged_by\":\"provider-1\"}")));
    }
}
