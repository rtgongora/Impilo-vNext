package zw.gov.mohcc.impilo.butano.interceptor;

import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.exceptions.ForbiddenOperationException;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Resource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.butano.config.ButanoProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the {@link TenantEnforcementInterceptor} — the multi-tenant
 * isolation layer for the BUTANO Shared Health Record.
 *
 * <p>Tests cover three main responsibilities:</p>
 * <ol>
 *   <li>Stamping a tenant tag on newly created resources</li>
 *   <li>Rejecting updates when the resource belongs to a different tenant</li>
 *   <li>Adding a tenant filter to search/read operations</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class TenantEnforcementInterceptorTest {

    private static final String TAG_SYSTEM = "https://impilo.gov.zw/tenant";
    private static final String TENANT_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
    private static final String TENANT_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";

    @Mock
    private RequestDetails requestDetails;

    private TenantEnforcementInterceptor interceptor;

    @BeforeEach
    void setUp() {
        ButanoProperties properties = new ButanoProperties();
        properties.getTenant().setEnforce(true);
        properties.getTenant().setTagSystem(TAG_SYSTEM);
        interceptor = new TenantEnforcementInterceptor(properties);
    }

    /**
     * Helper to configure the mock RequestDetails with a tenant ID in user data,
     * simulating what HeaderValidationInterceptor would do.
     */
    private void setTenantInUserData(String tenantId) {
        Map<Object, Object> userData = new HashMap<>();
        userData.put(HeaderValidationInterceptor.UD_TENANT_ID, tenantId);
        when(requestDetails.getUserData()).thenReturn(userData);
    }

    // ════════════════════════════════════════════════════════════════════
    // CREATE: tenant tag stamping
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("stamps tenant tag on newly created resource")
    void testStampsTenantTagOnCreate() {
        setTenantInUserData(TENANT_A);

        Patient patient = new Patient();
        patient.setActive(true);

        interceptor.onResourceCreated(patient, requestDetails);

        // Verify the tenant tag was stamped on the resource's meta
        List<Coding> tags = patient.getMeta().getTag();
        assertFalse(tags.isEmpty(), "Resource must have at least one meta tag after create");

        Coding tenantTag = tags.stream()
                .filter(t -> TAG_SYSTEM.equals(t.getSystem()))
                .findFirst()
                .orElse(null);

        assertNotNull(tenantTag, "Resource must have a tenant tag with system " + TAG_SYSTEM);
        assertEquals(TENANT_A, tenantTag.getCode(),
                "Tenant tag code must match the requesting tenant's ID");
    }

    @Test
    @DisplayName("replaces pre-existing tenant tags on create to prevent injection")
    void testReplacesExistingTenantTagOnCreate() {
        setTenantInUserData(TENANT_A);

        Observation observation = new Observation();
        // Pre-inject a fraudulent tenant tag
        observation.getMeta().addTag()
                .setSystem(TAG_SYSTEM)
                .setCode(TENANT_B);

        interceptor.onResourceCreated(observation, requestDetails);

        // Verify the injected tag was replaced with the real tenant
        long tenantTagCount = observation.getMeta().getTag().stream()
                .filter(t -> TAG_SYSTEM.equals(t.getSystem()))
                .count();
        assertEquals(1, tenantTagCount,
                "Must have exactly one tenant tag (the injected one should be replaced)");

        String tagCode = observation.getMeta().getTag().stream()
                .filter(t -> TAG_SYSTEM.equals(t.getSystem()))
                .map(Coding::getCode)
                .findFirst()
                .orElse(null);
        assertEquals(TENANT_A, tagCode,
                "Tenant tag must be stamped with the requesting tenant, not the injected value");
    }

    // ════════════════════════════════════════════════════════════════════
    // UPDATE: cross-tenant protection
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("rejects update when existing resource belongs to a different tenant")
    void testRejectMismatchedTenantOnUpdate() {
        setTenantInUserData(TENANT_A);

        // Existing resource belongs to TENANT_B
        Patient oldPatient = new Patient();
        oldPatient.getMeta().addTag()
                .setSystem(TAG_SYSTEM)
                .setCode(TENANT_B);

        Patient newPatient = new Patient();
        newPatient.setActive(true);

        ForbiddenOperationException ex = assertThrows(
                ForbiddenOperationException.class,
                () -> interceptor.onResourceUpdated(oldPatient, newPatient, requestDetails),
                "Cross-tenant update must be rejected with 403 Forbidden"
        );

        assertTrue(ex.getMessage().contains("different tenant"),
                "Error message must indicate a tenant isolation violation");
    }

    @Test
    @DisplayName("allows update when existing resource belongs to the same tenant")
    void testAllowSameTenantUpdate() {
        setTenantInUserData(TENANT_A);

        // Existing resource belongs to TENANT_A (same tenant)
        Patient oldPatient = new Patient();
        oldPatient.getMeta().addTag()
                .setSystem(TAG_SYSTEM)
                .setCode(TENANT_A);

        Patient newPatient = new Patient();
        newPatient.setActive(true);

        assertDoesNotThrow(
                () -> interceptor.onResourceUpdated(oldPatient, newPatient, requestDetails),
                "Same-tenant update should be allowed"
        );

        // Verify tenant tag is re-stamped on the new resource
        Coding tenantTag = newPatient.getMeta().getTag().stream()
                .filter(t -> TAG_SYSTEM.equals(t.getSystem()))
                .findFirst()
                .orElse(null);
        assertNotNull(tenantTag, "New resource must have tenant tag after update");
        assertEquals(TENANT_A, tenantTag.getCode());
    }

    // ════════════════════════════════════════════════════════════════════
    // READ: tenant filter on search operations
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("adds _tag search parameter to filter by tenant on search operations")
    void testReadFiltersByTenant() {
        Map<Object, Object> userData = new HashMap<>();
        userData.put(HeaderValidationInterceptor.UD_TENANT_ID, TENANT_A);
        when(requestDetails.getUserData()).thenReturn(userData);
        when(requestDetails.getRestOperationType()).thenReturn(RestOperationTypeEnum.SEARCH_TYPE);

        // Return empty parameters map (no existing _tag)
        Map<String, String[]> params = new HashMap<>();
        when(requestDetails.getParameters()).thenReturn(params);

        interceptor.onIncomingRequest(requestDetails);

        // Verify that addParameter was called with the tenant tag filter
        String expectedTagFilter = TAG_SYSTEM + "|" + TENANT_A;
        verify(requestDetails).addParameter(eq("_tag"), argThat(arr ->
                arr.length == 1 && expectedTagFilter.equals(arr[0])
        ));
    }

    @Test
    @DisplayName("rejects tenant tag override attempt in search parameters")
    void testRejectsTenantTagOverrideInSearch() {
        Map<Object, Object> userData = new HashMap<>();
        userData.put(HeaderValidationInterceptor.UD_TENANT_ID, TENANT_A);
        when(requestDetails.getUserData()).thenReturn(userData);
        when(requestDetails.getRestOperationType()).thenReturn(RestOperationTypeEnum.SEARCH_TYPE);

        // Existing _tag parameter tries to use a different tenant
        String fraudulentFilter = TAG_SYSTEM + "|" + TENANT_B;
        Map<String, String[]> params = new HashMap<>();
        params.put("_tag", new String[]{fraudulentFilter});
        when(requestDetails.getParameters()).thenReturn(params);

        ForbiddenOperationException ex = assertThrows(
                ForbiddenOperationException.class,
                () -> interceptor.onIncomingRequest(requestDetails),
                "Tenant tag override attempt must be rejected with 403 Forbidden"
        );

        assertTrue(ex.getMessage().contains("Cannot override tenant filter"),
                "Error message must indicate a tenant filter override attempt");
    }
}
