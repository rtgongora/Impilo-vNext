package zw.gov.mohcc.impilo.vito.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.vito.core.idempotency.IdempotencyRecord;
import zw.gov.mohcc.impilo.vito.core.idempotency.IdempotencyService;

import java.io.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Idempotency enforcement filter (RETIRED — superseded by Tech Companion library).
 *
 * The companion library's {@code IdempotencyFilter} is now authoritative for
 * POST/PUT/PATCH on /internal/v1/** paths, registered via CompanionV11Config.
 *
 * This class is retained for reference but is no longer auto-registered
 * (the @Component annotation has been removed).
 */
// @Component — RETIRED: companion library IdempotencyFilter is authoritative
// @Order(11)
public class IdempotencyFilter implements Filter {

    private static final Set<String> COMMAND_METHODS = Set.of("POST", "PUT", "PATCH");

    private final IdempotencyService idempotencyService;

    public IdempotencyFilter(IdempotencyService idempotencyService) {
        this.idempotencyService = idempotencyService;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();
        String method = httpReq.getMethod();

        // Only enforce on command methods to /internal/v1/**
        if (!COMMAND_METHODS.contains(method) || !path.startsWith("/internal/v1/")) {
            chain.doFilter(request, response);
            return;
        }

        // Require Idempotency-Key header
        String idempotencyKey = httpReq.getHeader("Idempotency-Key");
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            String requestId = httpReq.getHeader("X-Request-ID");
            String correlationId = httpReq.getHeader("X-Correlation-ID");
            V1_1ErrorWriter.writeError(httpRes, 400,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    "Idempotency-Key header is required for POST/PUT/PATCH on v1.1 internal endpoints",
                    requestId, correlationId);
            return;
        }

        String tenantId = httpReq.getHeader("X-Tenant-ID");
        String podId = httpReq.getHeader("X-Pod-ID");

        // Wrap request to cache body for hashing and re-reading
        CachingRequestWrapper cachedRequest = new CachingRequestWrapper(httpReq);
        byte[] bodyBytes = cachedRequest.getCachedBody();

        String requestHash = idempotencyService.computeHash(method, path, bodyBytes);

        // Check for existing record
        Optional<IdempotencyRecord> existing = idempotencyService.find(tenantId, podId, idempotencyKey);
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();
            if (record.requestHash().equals(requestHash)) {
                // Replay stored response
                httpRes.setStatus(record.responseStatus());
                httpRes.setContentType("application/json");
                httpRes.setCharacterEncoding("UTF-8");
                httpRes.getWriter().write(record.responseBody());
                httpRes.getWriter().flush();
                return;
            } else {
                // Same key, different request — conflict
                String requestId = httpReq.getHeader("X-Request-ID");
                String correlationId = httpReq.getHeader("X-Correlation-ID");
                V1_1ErrorWriter.writeError(httpRes, 409,
                        "IDENTITY_CONFLICT",
                        "Idempotency-Key already used with a different request",
                        Map.of("idempotency_key", idempotencyKey),
                        requestId, correlationId);
                return;
            }
        }

        // New key — proceed with wrapped request/response to capture output
        ResponseCaptureWrapper capturedResponse = new ResponseCaptureWrapper(httpRes);
        chain.doFilter(cachedRequest, capturedResponse);

        // Store the response for future replay
        int status = capturedResponse.getStatusCode();
        String body = capturedResponse.getCapturedBody();

        // Write the captured body to the actual response
        httpRes.setStatus(status);
        // Copy content type from captured response
        if (capturedResponse.getContentType() != null) {
            httpRes.setContentType(capturedResponse.getContentType());
        }
        httpRes.getOutputStream().write(capturedResponse.getCapturedBodyBytes());
        httpRes.getOutputStream().flush();

        // Store for idempotency replay (only for successful or expected responses)
        idempotencyService.store(tenantId, podId, idempotencyKey, requestHash, status, body);
    }

    /**
     * Request wrapper that caches the body for re-reading.
     */
    private static class CachingRequestWrapper extends HttpServletRequestWrapper {
        private final byte[] cachedBody;

        CachingRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = request.getInputStream().readAllBytes();
        }

        byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream bais = new ByteArrayInputStream(cachedBody);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return bais.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // no-op for sync processing
                }

                @Override
                public int read() {
                    return bais.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return bais.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    /**
     * Response wrapper that captures the response body.
     */
    private static class ResponseCaptureWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream capture = new ByteArrayOutputStream(1024);
        private ServletOutputStream outputStream;
        private PrintWriter writer;
        private int statusCode = 200;

        ResponseCaptureWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public void setStatus(int sc) {
            this.statusCode = sc;
            // Do NOT call super.setStatus() — we write status ourselves after capture
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.statusCode = sc;
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.statusCode = sc;
        }

        int getStatusCode() {
            return statusCode;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setWriteListener(WriteListener writeListener) {
                        // no-op for sync processing
                    }

                    @Override
                    public void write(int b) {
                        capture.write(b);
                    }

                    @Override
                    public void write(byte[] b, int off, int len) {
                        capture.write(b, off, len);
                    }
                };
            }
            return outputStream;
        }

        @Override
        public PrintWriter getWriter() {
            if (writer == null) {
                writer = new PrintWriter(new OutputStreamWriter(getOutputStream()));
            }
            return writer;
        }

        @Override
        public void flushBuffer() throws IOException {
            if (writer != null) writer.flush();
            if (outputStream != null) outputStream.flush();
        }

        String getCapturedBody() {
            if (writer != null) writer.flush();
            return capture.toString();
        }

        byte[] getCapturedBodyBytes() {
            if (writer != null) writer.flush();
            return capture.toByteArray();
        }
    }
}
