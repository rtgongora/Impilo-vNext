package zw.gov.mohcc.impilo.experience.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProxyHeaderSanitizingResponseAdviceTest {

    private final ProxyHeaderSanitizingResponseAdvice advice = new ProxyHeaderSanitizingResponseAdvice();

    @Test
    void stripsTransferEncodingAndHopByHopHeaders() {
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse response = new ServletServerHttpResponse(servletResponse);
        HttpHeaders headers = response.getHeaders();
        headers.add(HttpHeaders.TRANSFER_ENCODING, "chunked");
        headers.add(HttpHeaders.CONNECTION, "keep-alive");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        advice.beforeBodyWrite(
                "[]",
                null,
                MediaType.APPLICATION_JSON,
                StringHttpMessageConverter.class,
                null,
                response);

        assertFalse(headers.containsKey(HttpHeaders.TRANSFER_ENCODING));
        assertFalse(headers.containsKey(HttpHeaders.CONNECTION));
        assertTrue(headers.containsKey(HttpHeaders.CONTENT_TYPE));
    }

    @Test
    void sanitizeHopByHopHeaders_isIdempotentWhenNoHopByHopPresent() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

        ProxyHeaderSanitizingResponseAdvice.sanitizeHopByHopHeaders(headers);

        assertTrue(headers.containsKey(HttpHeaders.CONTENT_TYPE));
        assertFalse(headers.containsKey(HttpHeaders.TRANSFER_ENCODING));
    }
}
