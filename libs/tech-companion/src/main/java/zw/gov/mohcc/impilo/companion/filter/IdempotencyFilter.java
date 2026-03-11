package zw.gov.mohcc.impilo.companion.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelopeWriter;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyRecord;
import zw.gov.mohcc.impilo.companion.idempotency.IdempotencyService;

import java.io.*;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Idempotency enforcement filter for v1.1 command endpoints.
 *
 * Applies to POST/PUT/PATCH on {@code /internal/v1/**} and {@code /external/v1/**} paths.
 * Requires {@code Idempotency-Key} header on those requests.
 *
 * Behavior:
 * - same key + same request_hash => replay stored response (status + body)
 * - same key + different request_hash => 409 IDENTITY_CONFLICT
 * - new key => proceed, capture response, store for future replay
 *
 * Register at Order 11 (after V11HeaderFilter at Order 10).
 */
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

        // Only enforce on command methods to v1.1 API paths
        if (!COMMAND_METHODS.contains(method) || !V11HeaderFilter.isV11Path(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Require Idempotency-Key header
        String idempotencyKey = httpReq.getHeader(CompanionHeaders.IDEMPOTENCY_KEY);
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            String requestId = httpReq.getHeader(CompanionHeaders.REQUEST_ID);
            String correlationId = httpReq.getHeader(CompanionHeaders.CORRELATION_ID);
            ErrorEnvelopeWriter.write(httpRes, 400,
                    ErrorCodes.IDEMPOTENCY_KEY_REQUIRED,
                    "Idempotency-Key header is required for POST/PUT/PATCH on v1.1 internal endpoints",
                    requestId, correlationId);
            return;
        }

        String tenantId = httpReq.getHeader(CompanionHeaders.TENANT_ID);
        String podId = httpReq.getHeader(CompanionHeaders.POD_ID);

        // Wrap request to cache body for hashing
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
                String requestId = httpReq.getHeader(CompanionHeaders.REQUEST_ID);
                String correlationId = httpReq.getHeader(CompanionHeaders.CORRELATION_ID);
                ErrorEnvelopeWriter.write(httpRes, 409,
                        ErrorCodes.IDENTITY_CONFLICT,
                        "Idempotency-Key already used with a different request",
                        Map.of("idempotency_key", idempotencyKey),
                        requestId, correlationId);
                return;
            }
        }

        // New key — proceed with wrapped response to capture output
        ResponseCaptureWrapper capturedResponse = new ResponseCaptureWrapper(httpRes);
        chain.doFilter(cachedRequest, capturedResponse);

        int status = capturedResponse.getStatusCode();
        String body = capturedResponse.getCapturedBody();

        // Write captured body to actual response
        httpRes.setStatus(status);
        if (capturedResponse.getContentType() != null) {
            httpRes.setContentType(capturedResponse.getContentType());
        }
        httpRes.getOutputStream().write(capturedResponse.getCapturedBodyBytes());
        httpRes.getOutputStream().flush();

        // Store for idempotency replay
        idempotencyService.store(tenantId, podId, idempotencyKey, requestHash, status, body);
    }

    // ── Request wrapper that caches body for re-reading ─────────

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
                @Override public boolean isFinished() { return bais.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener l) { /* no-op */ }
                @Override public int read() { return bais.read(); }
                @Override public int read(byte[] b, int off, int len) { return bais.read(b, off, len); }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream()));
        }
    }

    // ── Response wrapper that captures the output ───────────────

    private static class ResponseCaptureWrapper extends HttpServletResponseWrapper {
        private final ByteArrayOutputStream capture = new ByteArrayOutputStream(1024);
        private ServletOutputStream outputStream;
        private PrintWriter writer;
        private int statusCode = 200;

        ResponseCaptureWrapper(HttpServletResponse response) { super(response); }

        @Override public void setStatus(int sc) { this.statusCode = sc; }
        @Override public void sendError(int sc, String msg) { this.statusCode = sc; }
        @Override public void sendError(int sc) { this.statusCode = sc; }
        int getStatusCode() { return statusCode; }

        @Override
        public ServletOutputStream getOutputStream() {
            if (outputStream == null) {
                outputStream = new ServletOutputStream() {
                    @Override public boolean isReady() { return true; }
                    @Override public void setWriteListener(WriteListener l) { /* no-op */ }
                    @Override public void write(int b) { capture.write(b); }
                    @Override public void write(byte[] b, int off, int len) { capture.write(b, off, len); }
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
