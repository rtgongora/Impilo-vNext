package zw.gov.mohcc.impilo.companion.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import zw.gov.mohcc.impilo.companion.context.CompanionHeaders;
import zw.gov.mohcc.impilo.companion.context.RequestContextHolder;
import zw.gov.mohcc.impilo.companion.error.ErrorCodes;
import zw.gov.mohcc.impilo.companion.error.ErrorEnvelopeWriter;

import java.io.IOException;

/**
 * Timeout enforcement filter for v1.1 endpoints.
 *
 * Reads {@code X-Client-Timeout-MS} header and sets a deadline.
 * If the downstream processing exceeds this deadline, the response
 * is replaced with a structured timeout error envelope (HTTP 504).
 *
 * Register at Order 12 (after V11HeaderFilter at 10, IdempotencyFilter at 11).
 *
 * Note: This filter enforces a wall-clock timeout on the servlet thread.
 * For async/reactive services, use {@link TimeoutInterceptor} instead.
 */
public class TimeoutEnforcementFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpRes = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();
        if (!V11HeaderFilter.isV11Path(path)) {
            chain.doFilter(request, response);
            return;
        }

        String timeoutHeader = httpReq.getHeader(CompanionHeaders.CLIENT_TIMEOUT_MS);
        if (timeoutHeader == null || timeoutHeader.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        long timeoutMs;
        try {
            timeoutMs = Long.parseLong(timeoutHeader);
        } catch (NumberFormatException e) {
            // Invalid header — proceed without enforcement
            chain.doFilter(request, response);
            return;
        }

        if (timeoutMs <= 0) {
            chain.doFilter(request, response);
            return;
        }

        long deadline = System.currentTimeMillis() + timeoutMs;

        // Store deadline on the request for downstream access
        httpReq.setAttribute("companion.deadline", deadline);

        long start = System.currentTimeMillis();
        chain.doFilter(request, response);
        long elapsed = System.currentTimeMillis() - start;

        // If the response has already been committed, we can't override it
        if (elapsed > timeoutMs && !httpRes.isCommitted()) {
            String requestId = httpReq.getHeader(CompanionHeaders.REQUEST_ID);
            String correlationId = httpReq.getHeader(CompanionHeaders.CORRELATION_ID);

            ErrorEnvelopeWriter.write(httpRes, 504,
                    ErrorCodes.CLIENT_TIMEOUT_EXCEEDED,
                    "Request processing exceeded client timeout of " + timeoutMs + "ms (took " + elapsed + "ms)",
                    requestId, correlationId);
        }
    }

    /**
     * Check if the current request has exceeded its deadline.
     *
     * @return true if the client timeout has been exceeded
     */
    public static boolean isDeadlineExceeded(HttpServletRequest request) {
        Object deadline = request.getAttribute("companion.deadline");
        if (deadline instanceof Long dl) {
            return System.currentTimeMillis() > dl;
        }
        return false;
    }

    /**
     * Get remaining time until deadline in milliseconds.
     *
     * @return remaining ms, or Long.MAX_VALUE if no deadline set
     */
    public static long remainingMs(HttpServletRequest request) {
        Object deadline = request.getAttribute("companion.deadline");
        if (deadline instanceof Long dl) {
            return Math.max(0, dl - System.currentTimeMillis());
        }
        return Long.MAX_VALUE;
    }
}
