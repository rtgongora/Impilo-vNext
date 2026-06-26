package zw.gov.mohcc.impilo.learning.context;

import java.util.Optional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * In-lane accessor for facility / workspace / learning-space trust headers.
 *
 * <p>The shared companion {@code RequestContext} record does not (yet) carry these
 * fields, and extending it is a cross-lane, high-blast-radius change. learning-service
 * reads them directly off the current HTTP request instead — additive and isolated.
 * Header names mirror {@code CompanionHeaders} constants.
 */
public final class LearningRequestContext {

    public static final String H_FACILITY_ID = "X-Facility-ID";
    public static final String H_TUSO_FACILITY_ID = "X-Tuso-Facility-Id";
    public static final String H_WORKSPACE_ID = "X-Workspace-ID";
    public static final String H_LEARNING_SPACE_ID = "X-Learning-Space-Id";

    private LearningRequestContext() {}

    public static Optional<String> facilityId() {
        return header(H_FACILITY_ID).or(() -> header(H_TUSO_FACILITY_ID));
    }

    public static Optional<String> workspaceId() {
        return header(H_WORKSPACE_ID);
    }

    /**
     * The learning-space (academy/org-unit) scope. Falls back to the workspace header
     * when an explicit learning-space header is absent.
     */
    public static Optional<String> learningSpaceId() {
        return header(H_LEARNING_SPACE_ID).or(() -> header(H_WORKSPACE_ID));
    }

    private static Optional<String> header(String name) {
        try {
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return Optional.empty();
            }
            String v = attrs.getRequest().getHeader(name);
            return (v == null || v.isBlank()) ? Optional.empty() : Optional.of(v);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
