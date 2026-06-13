package zw.gov.mohcc.impilo.experience.web;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.List;

/**
 * Strips hop-by-hop and framing headers copied from upstream proxy responses before
 * Spring writes the outbound body.
 *
 * <p>When controllers return a passthrough {@code ResponseEntity} from RestTemplate,
 * upstream {@code Transfer-Encoding: chunked} can remain on the response headers while
 * Spring also chunks the body — producing duplicate {@code Transfer-Encoding} values
 * that reverse proxies (Traefik) reject as malformed.</p>
 *
 * <p>Aligns with the per-controller {@code stripHopByHop} convention used in
 * {@code MvumoServiceProxyController}, {@code WellnessServiceProxyController}, and
 * {@code CitizenMonitoringDevicesController}.</p>
 */
@RestControllerAdvice
public class ProxyHeaderSanitizingResponseAdvice implements ResponseBodyAdvice<Object> {

    private static final List<String> HOP_BY_HOP = List.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "host");

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        sanitizeHopByHopHeaders(response.getHeaders());
        return body;
    }

    static void sanitizeHopByHopHeaders(HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
            return;
        }
        for (String name : HOP_BY_HOP) {
            headers.remove(name);
        }
        headers.remove(HttpHeaders.TRANSFER_ENCODING);
    }
}
