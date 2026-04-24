package zw.gov.mohcc.impilo.shared.visibility;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class VisibilityHeaderParserResolveTest {

    @Test
    void resolveReadsVisibilityProfileFromObligationsJson() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String obligations = "{\"visibilityProfile\":{\"visibilityTier\":\"PSEUDONYMISED_PERSON_LEVEL\","
                + "\"piiAccess\":\"MASKED\",\"clinicalAccess\":\"NONE\",\"aggregateOnly\":false,"
                + "\"exportPolicy\":\"REDACTED\",\"drillDownAllowed\":false}}";

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TrustHeaders.OBLIGATIONS, obligations);

        VisibilityProfile p = VisibilityHeaderParser.resolve(req, mapper);

        assertNotNull(p);
        assertEquals("PSEUDONYMISED_PERSON_LEVEL", p.visibilityTier());
        assertEquals("REDACTED", p.exportPolicy());
    }

    @Test
    void resolveFallsBackToFlatHeadersWhenNoObligations() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TrustHeaders.VISIBILITY_TIER, "AGGREGATE_ONLY");
        req.addHeader(TrustHeaders.EXPORT_POLICY, "AGGREGATE_ONLY");

        VisibilityProfile p = VisibilityHeaderParser.resolve(req, new ObjectMapper());

        assertNotNull(p);
        assertEquals("AGGREGATE_ONLY", p.visibilityTier());
    }

    @Test
    void resolveMergesObligationsJsonOverFlatHeaders() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String obligations = "{\"visibilityProfile\":{\"visibilityTier\":\"PSEUDONYMISED_PERSON_LEVEL\"}}";

        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader(TrustHeaders.OBLIGATIONS, obligations);
        req.addHeader(TrustHeaders.EXPORT_POLICY, "REDACTED");

        VisibilityProfile p = VisibilityHeaderParser.resolve(req, mapper);

        assertNotNull(p);
        assertEquals("PSEUDONYMISED_PERSON_LEVEL", p.visibilityTier());
        assertEquals("REDACTED", p.exportPolicy());
    }
}
