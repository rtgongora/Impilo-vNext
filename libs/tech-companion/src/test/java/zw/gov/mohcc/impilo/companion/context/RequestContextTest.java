package zw.gov.mohcc.impilo.companion.context;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RequestContextTest {

    @Test
    void ofGeneratesRequestIdWhenNull() {
        RequestContext ctx = RequestContext.of("t1", "pod1", null, null, null, null, null);
        assertNotNull(ctx.requestId());
        assertFalse(ctx.requestId().isBlank());
    }

    @Test
    void ofGeneratesCorrelationIdWhenBlank() {
        RequestContext ctx = RequestContext.of("t1", "pod1", "req1", "  ", null, null, null);
        assertNotNull(ctx.correlationId());
        assertNotEquals("  ", ctx.correlationId());
    }

    @Test
    void isNationalPodRecognizesNational() {
        RequestContext ctx = RequestContext.of("t1", "national", "r", "c", null, null, null);
        assertTrue(ctx.isNationalPod());
    }

    @Test
    void isNationalPodFalseForPrivatePod() {
        RequestContext ctx = RequestContext.of("t1", "private-harare", "r", "c", null, null, null);
        assertFalse(ctx.isNationalPod());
    }

    @Test
    void timeoutReturnsEmptyWhenNull() {
        RequestContext ctx = RequestContext.of("t1", "p", "r", "c", null, null, null);
        assertTrue(ctx.timeout().isEmpty());
    }

    @Test
    void timeoutReturnsPresentWhenSet() {
        RequestContext ctx = RequestContext.of("t1", "p", "r", "c", null, null, 5000L);
        assertTrue(ctx.timeout().isPresent());
        assertEquals(5000L, ctx.timeout().get());
    }
}
