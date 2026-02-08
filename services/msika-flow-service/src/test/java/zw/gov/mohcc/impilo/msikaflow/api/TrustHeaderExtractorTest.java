package zw.gov.mohcc.impilo.msikaflow.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TrustHeaderExtractorTest {

    @Test
    void tenantId_validUuid_returnsParsed() {
        UUID expected = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", expected.toString());

        UUID result = TrustHeaderExtractor.tenantId(request);
        assertEquals(expected, result);
    }

    @Test
    void tenantId_missing_throwsException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(IllegalArgumentException.class, () -> TrustHeaderExtractor.tenantId(request));
    }

    @Test
    void tenantId_blank_throwsException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Tenant-Id", "   ");
        assertThrows(IllegalArgumentException.class, () -> TrustHeaderExtractor.tenantId(request));
    }

    @Test
    void actorId_present_returnsTrimmed() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor-Id", "  actor-123  ");
        assertEquals("actor-123", TrustHeaderExtractor.actorId(request));
    }

    @Test
    void actorId_missing_throwsException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(IllegalArgumentException.class, () -> TrustHeaderExtractor.actorId(request));
    }

    @Test
    void actorType_present_returnsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor-Type", "PATIENT");
        assertEquals("PATIENT", TrustHeaderExtractor.actorType(request));
    }

    @Test
    void actorType_missing_defaultsToSystem() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("SYSTEM", TrustHeaderExtractor.actorType(request));
    }

    @Test
    void correlationId_present_returnsValue() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Correlation-Id", "corr-456");
        assertEquals("corr-456", TrustHeaderExtractor.correlationId(request));
    }

    @Test
    void correlationId_missing_generatesNewUuid() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String correlationId = TrustHeaderExtractor.correlationId(request);
        assertNotNull(correlationId);
        assertDoesNotThrow(() -> UUID.fromString(correlationId));
    }

    @Test
    void facilityId_present_returnsParsed() {
        UUID expected = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Facility-Id", expected.toString());
        assertEquals(expected, TrustHeaderExtractor.facilityId(request));
    }

    @Test
    void facilityId_missing_returnsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertNull(TrustHeaderExtractor.facilityId(request));
    }

    @Test
    void deviceFingerprint_missing_defaultsToUnknown() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("unknown", TrustHeaderExtractor.deviceFingerprint(request));
    }

    @Test
    void purposeOfUse_missing_defaultsToOperations() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertEquals("OPERATIONS", TrustHeaderExtractor.purposeOfUse(request));
    }
}
