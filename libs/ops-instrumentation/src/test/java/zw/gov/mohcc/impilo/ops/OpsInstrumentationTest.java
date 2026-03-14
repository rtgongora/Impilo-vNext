package zw.gov.mohcc.impilo.ops;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import zw.gov.mohcc.impilo.ops.mdc.MdcFilter;
import zw.gov.mohcc.impilo.ops.otel.OtelPropagationFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests verifying MDC injection and OTel propagation from ops-instrumentation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpsInstrumentationTest {

    @Autowired
    private MockMvc mockMvc;

    @SpringBootApplication
    static class TestApp {

        @RestController
        static class MdcEchoController {

            @GetMapping("/mdc-echo")
            public Map<String, String> echoMdc() {
                Map<String, String> mdc = new HashMap<>();
                mdc.put("tenant_id", MDC.get(MdcFilter.MDC_TENANT_ID));
                mdc.put("pod_id", MDC.get(MdcFilter.MDC_POD_ID));
                mdc.put("request_id", MDC.get(MdcFilter.MDC_REQUEST_ID));
                mdc.put("correlation_id", MDC.get(MdcFilter.MDC_CORRELATION_ID));
                mdc.put("user_id", MDC.get(MdcFilter.MDC_USER_ID));
                mdc.put("trace_id", MDC.get(OtelPropagationFilter.MDC_TRACE_ID));
                mdc.put("span_id", MDC.get(OtelPropagationFilter.MDC_SPAN_ID));
                return mdc;
            }
        }
    }

    @Nested
    class MdcInjection {

        @Test
        void shouldInjectAllV11HeadersIntoMdc() throws Exception {
            String tenantId = "moh-zw";
            String podId = "national";
            String requestId = UUID.randomUUID().toString();
            String correlationId = UUID.randomUUID().toString();
            String userId = "user-123";

            mockMvc.perform(get("/mdc-echo")
                            .header("X-Tenant-ID", tenantId)
                            .header("X-Pod-ID", podId)
                            .header("X-Request-ID", requestId)
                            .header("X-Correlation-ID", correlationId)
                            .header("X-Actor-ID", userId)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenant_id").value(tenantId))
                    .andExpect(jsonPath("$.pod_id").value(podId))
                    .andExpect(jsonPath("$.request_id").value(requestId))
                    .andExpect(jsonPath("$.correlation_id").value(correlationId))
                    .andExpect(jsonPath("$.user_id").value(userId));
        }

        @Test
        void shouldHandleMissingHeadersGracefully() throws Exception {
            mockMvc.perform(get("/mdc-echo")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.tenant_id").isEmpty())
                    .andExpect(jsonPath("$.pod_id").isEmpty())
                    .andExpect(jsonPath("$.request_id").isEmpty())
                    .andExpect(jsonPath("$.correlation_id").isEmpty())
                    .andExpect(jsonPath("$.user_id").isEmpty());
        }

        @Test
        void shouldClearMdcAfterRequest() throws Exception {
            mockMvc.perform(get("/mdc-echo")
                            .header("X-Tenant-ID", "moh-zw")
                            .header("X-Pod-ID", "national")
                            .header("X-Request-ID", UUID.randomUUID().toString())
                            .header("X-Correlation-ID", UUID.randomUUID().toString())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk());

            // After the request completes, MDC should be clean
            assertThat(MDC.get(MdcFilter.MDC_TENANT_ID)).isNull();
            assertThat(MDC.get(MdcFilter.MDC_POD_ID)).isNull();
            assertThat(MDC.get(MdcFilter.MDC_REQUEST_ID)).isNull();
            assertThat(MDC.get(MdcFilter.MDC_CORRELATION_ID)).isNull();
        }
    }

    @Nested
    class OtelPropagation {

        @Test
        void shouldExtractTraceparentIntoMdc() throws Exception {
            String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
            String spanId = "00f067aa0ba902b7";
            String traceparent = "00-" + traceId + "-" + spanId + "-01";

            mockMvc.perform(get("/mdc-echo")
                            .header("traceparent", traceparent)
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trace_id").value(traceId))
                    .andExpect(jsonPath("$.span_id").value(spanId));
        }

        @Test
        void shouldHandleMissingTraceparentGracefully() throws Exception {
            mockMvc.perform(get("/mdc-echo")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.trace_id").isEmpty());
        }
    }

    @Nested
    class TraceparentParsing {

        @Test
        void shouldParseValidTraceparent() {
            var ctx = OtelPropagationFilter.parseTraceparent(
                    "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
            assertThat(ctx).isNotNull();
            assertThat(ctx.traceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
            assertThat(ctx.spanId()).isEqualTo("00f067aa0ba902b7");
        }

        @Test
        void shouldReturnNullForInvalidTraceparent() {
            assertThat(OtelPropagationFilter.parseTraceparent("invalid")).isNull();
            assertThat(OtelPropagationFilter.parseTraceparent(null)).isNull();
        }
    }
}
