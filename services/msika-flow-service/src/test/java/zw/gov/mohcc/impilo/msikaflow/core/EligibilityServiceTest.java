package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.msikaflow.domain.MarketplaceProfile;
import zw.gov.mohcc.impilo.msikaflow.domain.PublicationMode;
import zw.gov.mohcc.impilo.msikaflow.domain.VendorStatus;
import zw.gov.mohcc.impilo.msikaflow.integration.TusoClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VarapiClient;
import zw.gov.mohcc.impilo.msikaflow.integration.VashandiClient;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.VendorProfileEntity;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * OF-B5 — §4.3 six-precondition matrix: each precondition failing ALONE fails
 * the whole evaluation (fail-closed), registry outages yield UNVERIFIED which
 * is never eligible, and controlled requests require controlled authority.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EligibilityServiceTest {

    @Mock private VarapiClient varapiClient;
    @Mock private TusoClient tusoClient;
    @Mock private VashandiClient vashandiClient;

    private final ObjectMapper mapper = new ObjectMapper();
    private EligibilityService service;

    private static final UUID TENANT = UUID.randomUUID();
    private static final String FACILITY_UUID = UUID.randomUUID().toString();
    private static final String STORE_UUID = UUID.randomUUID().toString();
    private static final String VASHANDI_PROFILE = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new EligibilityService(varapiClient, tusoClient, vashandiClient, mapper);
        stubAllHealthy();
    }

    private void stubAllHealthy() {
        ObjectNode standing = mapper.createObjectNode();
        standing.put("active", true);
        standing.put("hasValidLicense", true);
        when(varapiClient.getStandingSummary(anyString(), any())).thenReturn(standing);

        ObjectNode facility = mapper.createObjectNode();
        facility.put("operational", true);
        when(tusoClient.getFacilityStatusSummary(anyLong(), any())).thenReturn(facility);

        when(vashandiClient.hasActiveAssignment(anyString(), anyString(), any()))
                .thenReturn(Optional.of(Boolean.TRUE));
    }

    private VendorProfileEntity vendor(boolean controlledAuthority) {
        VendorProfileEntity vendor = new VendorProfileEntity();
        vendor.setVendorId("01VENDOR0000000000000000AA");
        vendor.setTenantId(TENANT);
        vendor.setStatus(VendorStatus.ACTIVE);
        vendor.setCoverage("""
                {"providerPublicId":"PRV-100","tusoFacilityId":42,
                 "premisesFacilityId":"%s","storeId":"%s",
                 "vashandiProfileId":"%s","zones":["zone-1"],
                 "capabilities":{"dispensing":true,"controlled_authority":%s,"cold_chain":true}}
                """.formatted(FACILITY_UUID, STORE_UUID, VASHANDI_PROFILE, controlledAuthority));
        return vendor;
    }

    private MarketplaceRequestEntity request(boolean controlled) {
        MarketplaceRequestEntity request = new MarketplaceRequestEntity();
        request.setRequestId("01REQ0000000000000000000AA");
        request.setTenantId(TENANT);
        request.setProfile(MarketplaceProfile.MEDICATION);
        request.setPublicationMode(PublicationMode.INVITED);
        request.setControlled(controlled);
        request.setPublishedLinesJson(
                "{\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-X\",\"quantity\":1,"
                        + "\"controlled\":" + controlled + ",\"coldChain\":false}]}");
        return request;
    }

    @Test
    void allSixPass_vendorIsEligible() {
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertTrue(result.eligible());
        assertEquals(6, result.preconditions().size());
        assertNull(result.firstRefusalCode());
    }

    @Test
    void p1_licenceLapsed_failsAlone() {
        ObjectNode lapsed = mapper.createObjectNode();
        lapsed.put("active", true);
        lapsed.put("hasValidLicense", false);
        when(varapiClient.getStandingSummary(anyString(), any())).thenReturn(lapsed);

        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_LICENCE_NOT_ACTIVE, result.firstRefusalCode());
    }

    @Test
    void p1_varapiUnreachable_isUnverified_andFailsClosed() {
        when(varapiClient.getStandingSummary(anyString(), any())).thenReturn(null);
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_REGISTRY_UNREACHABLE, result.firstRefusalCode());
        assertEquals(EligibilityService.OUTCOME_UNVERIFIED, result.preconditions().get(0).outcome());
    }

    @Test
    void p2_scopeMismatch_failsAlone() {
        VendorProfileEntity vendor = vendor(false);
        vendor.setCoverage(vendor.getCoverage().replace("\"dispensing\":true", "\"dispensing\":false"));
        EligibilityService.EligibilityResult result = service.evaluate(vendor, request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_SCOPE_MISMATCH, result.firstRefusalCode());
    }

    @Test
    void p3_premisesNotOperational_failsAlone() {
        ObjectNode closed = mapper.createObjectNode();
        closed.put("operational", false);
        closed.put("status", "SUSPENDED");
        when(tusoClient.getFacilityStatusSummary(anyLong(), any())).thenReturn(closed);

        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_PREMISES_NOT_OPERATIONAL, result.firstRefusalCode());
    }

    @Test
    void p3_tusoUnreachable_failsClosed() {
        when(tusoClient.getFacilityStatusSummary(anyLong(), any())).thenReturn(null);
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_PREMISES_UNVERIFIED, result.firstRefusalCode());
    }

    @Test
    void p4_noEmploymentBinding_failsAlone() {
        when(vashandiClient.hasActiveAssignment(anyString(), anyString(), any()))
                .thenReturn(Optional.of(Boolean.FALSE));
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_BINDING_ABSENT, result.firstRefusalCode());
    }

    @Test
    void p4_vashandiUnreachable_isUnverified_andFailsClosed() {
        when(vashandiClient.hasActiveAssignment(anyString(), anyString(), any()))
                .thenReturn(Optional.empty());
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_BINDING_UNVERIFIED, result.firstRefusalCode());
    }

    @Test
    void p5_controlledLine_withoutControlledAuthority_failsAlone() {
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(true), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_CONTROLLED_AUTHORITY_MISSING, result.firstRefusalCode());
    }

    @Test
    void p5_controlledLine_withControlledAuthority_passes() {
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(true), request(true), null);
        assertTrue(result.eligible());
    }

    @Test
    void p5_coldChainLine_withoutColdChainCapability_fails() {
        VendorProfileEntity vendor = vendor(false);
        vendor.setCoverage(vendor.getCoverage().replace("\"cold_chain\":true", "\"cold_chain\":false"));
        MarketplaceRequestEntity request = request(false);
        request.setPublishedLinesJson(
                "{\"lines\":[{\"lineRef\":\"L1\",\"itemCode\":\"ZIBO-INS\",\"quantity\":1,"
                        + "\"controlled\":false,\"coldChain\":true}]}");
        EligibilityService.EligibilityResult result = service.evaluate(vendor, request, null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_CAPABILITY_MISSING, result.firstRefusalCode());
    }

    @Test
    void p6_suspendedVendor_failsAlone() {
        VendorProfileEntity vendor = vendor(false);
        vendor.setStatus(VendorStatus.SUSPENDED);
        EligibilityService.EligibilityResult result = service.evaluate(vendor, request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_VENDOR_NOT_IN_GOOD_STANDING, result.firstRefusalCode());
    }

    @Test
    void missingProviderRef_failsClosed_notSilentlyPassed() {
        VendorProfileEntity vendor = vendor(false);
        vendor.setCoverage("{\"capabilities\":{\"dispensing\":true}}");
        EligibilityService.EligibilityResult result = service.evaluate(vendor, request(false), null);
        assertFalse(result.eligible());
        assertEquals(EligibilityService.CODE_PROVIDER_REF_MISSING, result.firstRefusalCode());
    }

    @Test
    void snapshotJson_recordsAllSixPreconditions() throws Exception {
        EligibilityService.EligibilityResult result =
                service.evaluate(vendor(false), request(false), null);
        String json = service.toSnapshotJson(result);
        var parsed = mapper.readTree(json);
        assertTrue(parsed.path("eligible").asBoolean());
        assertEquals(6, parsed.path("preconditions").size());
        assertEquals("PROFESSIONAL_REGISTRATION", parsed.path("preconditions").get(0).path("name").asText());
    }

    @Test
    void hasControlledAuthority_readsCapabilityFlag() {
        assertTrue(service.hasControlledAuthority(vendor(true)));
        assertFalse(service.hasControlledAuthority(vendor(false)));
    }

    @Test
    void duraSource_parsesRefs_andToleratesAbsence() {
        assertTrue(service.duraSource(vendor(false)).isPresent());
        VendorProfileEntity bare = vendor(false);
        bare.setCoverage("{}");
        assertTrue(service.duraSource(bare).isEmpty());
    }
}
