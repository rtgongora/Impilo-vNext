package zw.gov.mohcc.impilo.learning.context;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class LearningRequestContextTest {

    @AfterEach
    void clear() {
        RequestContextHolder.resetRequestAttributes();
    }

    private void bind(MockHttpServletRequest req) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(req));
    }

    @Test
    void readsHeadersWhenPresent() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Facility-ID", "FAC-1");
        req.addHeader("X-Workspace-ID", "WS-1");
        req.addHeader("X-Learning-Space-Id", "SPACE-1");
        bind(req);

        assertThat(LearningRequestContext.facilityId()).contains("FAC-1");
        assertThat(LearningRequestContext.workspaceId()).contains("WS-1");
        assertThat(LearningRequestContext.learningSpaceId()).contains("SPACE-1");
    }

    @Test
    void learningSpaceFallsBackToWorkspace() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Workspace-ID", "WS-2");
        bind(req);
        assertThat(LearningRequestContext.learningSpaceId()).contains("WS-2");
    }

    @Test
    void facilityFallsBackToTusoHeader() {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("X-Tuso-Facility-Id", "TUSO-9");
        bind(req);
        assertThat(LearningRequestContext.facilityId()).contains("TUSO-9");
    }

    @Test
    void emptyWhenNoRequestBound() {
        assertThat(LearningRequestContext.facilityId()).isEmpty();
        assertThat(LearningRequestContext.learningSpaceId()).isEmpty();
    }
}
