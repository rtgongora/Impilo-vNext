package zw.gov.mohcc.impilo.tshepo.authz.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the static derivation methods on AuthzInternalRequest.
 *
 * <p>These methods extract action, resource type, and resource ID from
 * HTTP method + path, providing the inputs for RBAC/ABAC evaluation.</p>
 */
class AuthzInternalRequestTest {

    // ════════════════════════════════════════════════════════════════════
    // deriveAction
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deriveAction: concatenates method and path with colon separator")
    void deriveAction_concatenatesMethodAndPath() {
        String action = AuthzInternalRequest.deriveAction("GET", "/v1/patients");

        assertEquals("GET:/v1/patients", action,
                "deriveAction must produce METHOD:PATH format");
    }

    @Test
    @DisplayName("deriveAction: uppercases the HTTP method")
    void deriveAction_uppercasesMethod() {
        String action = AuthzInternalRequest.deriveAction("post", "/v1/encounters");

        assertEquals("POST:/v1/encounters", action,
                "deriveAction must uppercase the method");
    }

    @Test
    @DisplayName("deriveAction: null method -> UNKNOWN")
    void deriveAction_withNullMethod_returnsUnknown() {
        String action = AuthzInternalRequest.deriveAction(null, "/v1/patients");

        assertEquals("UNKNOWN", action,
                "Null method must return UNKNOWN");
    }

    @Test
    @DisplayName("deriveAction: null path -> method + /")
    void deriveAction_withNullPath_usesSlash() {
        String action = AuthzInternalRequest.deriveAction("DELETE", null);

        assertEquals("DELETE:/", action,
                "Null path must default to /");
    }

    @Test
    @DisplayName("deriveAction: both null -> UNKNOWN")
    void deriveAction_withBothNull_returnsUnknown() {
        String action = AuthzInternalRequest.deriveAction(null, null);

        assertEquals("UNKNOWN", action,
                "Both null must return UNKNOWN");
    }

    // ════════════════════════════════════════════════════════════════════
    // deriveResourceType
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deriveResourceType: extracts last non-UUID segment from path")
    void deriveResourceType_extractsLastNonUuidSegment() {
        String type = AuthzInternalRequest.deriveResourceType("/v1/patients");

        assertEquals("patients", type,
                "Must extract 'patients' as resource type");
    }

    @Test
    @DisplayName("deriveResourceType: path with UUID at end -> skips UUID, returns resource name")
    void deriveResourceType_withUuidInPath_skipsUuid() {
        String type = AuthzInternalRequest.deriveResourceType(
                "/v1/patients/550e8400-e29b-41d4-a716-446655440000");

        assertEquals("patients", type,
                "Must skip UUID and return 'patients'");
    }

    @Test
    @DisplayName("deriveResourceType: nested path -> extracts deepest non-UUID segment")
    void deriveResourceType_nestedPath_extractsDeepest() {
        String type = AuthzInternalRequest.deriveResourceType(
                "/v1/patients/550e8400-e29b-41d4-a716-446655440000/encounters");

        assertEquals("encounters", type,
                "Must extract deepest non-UUID segment 'encounters'");
    }

    @Test
    @DisplayName("deriveResourceType: null path -> UNKNOWN")
    void deriveResourceType_withNullPath_returnsUnknown() {
        String type = AuthzInternalRequest.deriveResourceType(null);

        assertEquals("UNKNOWN", type,
                "Null path must return UNKNOWN");
    }

    @Test
    @DisplayName("deriveResourceType: very short path -> UNKNOWN")
    void deriveResourceType_withShortPath_returnsUnknown() {
        String type = AuthzInternalRequest.deriveResourceType("/");

        assertEquals("UNKNOWN", type,
                "Root path must return UNKNOWN");
    }

    @Test
    @DisplayName("deriveResourceType: skips 'v1' and 'api' segments")
    void deriveResourceType_skipsVersionAndApiSegments() {
        String type = AuthzInternalRequest.deriveResourceType("/api/v1");

        // Both 'api' and 'v1' should be skipped -> UNKNOWN since no other segment
        assertEquals("UNKNOWN", type,
                "Path with only v1/api segments must return UNKNOWN");
    }

    @Test
    @DisplayName("deriveResourceType: Experience shell workspace synthetic path -> workspace-state")
    void deriveResourceType_experienceShellWorkspacePath() {
        String type = AuthzInternalRequest.deriveResourceType("/internal/v1/shell/workspace-state");
        assertEquals("workspace-state", type,
                "Synthetic BFF path must map policy resource_type workspace-state (see V008/V009 migrations)");
    }

    // ════════════════════════════════════════════════════════════════════
    // deriveResourceId
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("deriveResourceId: extracts UUID from path")
    void deriveResourceId_extractsUuid() {
        String id = AuthzInternalRequest.deriveResourceId(
                "/v1/patients/550e8400-e29b-41d4-a716-446655440000");

        assertEquals("550e8400-e29b-41d4-a716-446655440000", id,
                "Must extract UUID from path");
    }

    @Test
    @DisplayName("deriveResourceId: multiple UUIDs -> extracts last one")
    void deriveResourceId_multipleUuids_extractsLast() {
        String id = AuthzInternalRequest.deriveResourceId(
                "/v1/patients/550e8400-e29b-41d4-a716-446655440000/encounters/660e8400-e29b-41d4-a716-446655440001");

        assertEquals("660e8400-e29b-41d4-a716-446655440001", id,
                "Must extract the last UUID from the path");
    }

    @Test
    @DisplayName("deriveResourceId: no UUID in path -> null")
    void deriveResourceId_withNoUuid_returnsNull() {
        String id = AuthzInternalRequest.deriveResourceId("/v1/patients");

        assertNull(id, "Path with no UUID must return null");
    }

    @Test
    @DisplayName("deriveResourceId: null path -> null")
    void deriveResourceId_withNullPath_returnsNull() {
        String id = AuthzInternalRequest.deriveResourceId(null);

        assertNull(id, "Null path must return null");
    }

    @Test
    @DisplayName("deriveResourceId: UUID-like string that is not 36 chars -> null")
    void deriveResourceId_withNonUuidSegment_returnsNull() {
        String id = AuthzInternalRequest.deriveResourceId("/v1/patients/short-id");

        assertNull(id, "Non-UUID segment must not be extracted");
    }
}
