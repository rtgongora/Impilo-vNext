package zw.gov.mohcc.impilo.shared.visibility;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;
import zw.gov.mohcc.impilo.tshepo.contracts.headers.TrustHeaders;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Parses {@link VisibilityProfile} from flattened trust headers and optional {@code x-obligations} JSON.
 * When obligations carry a nested {@code visibilityProfile}, its non-null fields overlay flat headers.
 */
public final class VisibilityHeaderParser {

    private VisibilityHeaderParser() {
    }

    /**
     * Resolves visibility using {@link #parseFromObligationsJson} when a mapper is supplied
     * (nested {@code visibilityProfile} inside {@code x-obligations} overlays flat headers), otherwise flat headers only.
     */
    public static VisibilityProfile resolve(HttpServletRequest request, ObjectMapper mapper) {
        if (mapper == null) {
            return parseFlat(request);
        }
        return parseFromObligationsJson(mapper, request);
    }

    /**
     * Reads flat headers only (no Jackson dependency required for obligations blob).
     */
    public static VisibilityProfile parseFlat(HttpServletRequest request) {
        String tier = request.getHeader(TrustHeaders.VISIBILITY_TIER);
        String pii = request.getHeader(TrustHeaders.PII_ACCESS);
        String clin = request.getHeader(TrustHeaders.CLINICAL_ACCESS);
        String agg = request.getHeader(TrustHeaders.AGGREGATE_ONLY);
        String sens = request.getHeader(TrustHeaders.RESOURCE_SENSITIVITY);
        String grant = request.getHeader(TrustHeaders.ESCALATION_GRANT_ID);
        String export = request.getHeader(TrustHeaders.EXPORT_POLICY);
        String suppress = request.getHeader(TrustHeaders.SUPPRESS_FIELDS);
        String drill = request.getHeader(TrustHeaders.DRILL_DOWN_ALLOWED);

        if (tier == null && pii == null && clin == null && agg == null && sens == null
                && grant == null && export == null && suppress == null && drill == null) {
            return null;
        }

        Boolean aggregateOnly = agg == null ? null : Boolean.parseBoolean(agg);
        Boolean drillDown = drill == null ? null : Boolean.parseBoolean(drill);
        List<String> suppressFields = null;
        if (suppress != null && !suppress.isBlank()) {
            suppressFields = Arrays.stream(suppress.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toCollection(ArrayList::new));
        }

        return new VisibilityProfile(
                tier, pii, clin, aggregateOnly, sens, grant, null,
                suppressFields, null, export, drillDown);
    }

    public static VisibilityProfile parseFromObligationsJson(ObjectMapper mapper, HttpServletRequest request) {
        VisibilityProfile flat = parseFlat(request);
        String blob = request.getHeader(TrustHeaders.OBLIGATIONS);
        if (blob == null || blob.isBlank()) {
            return flat;
        }
        try {
            JsonNode root = mapper.readTree(blob);
            JsonNode vp = root.get("visibilityProfile");
            if (vp == null || vp.isNull()) {
                return flat;
            }
            VisibilityProfile fromJson = mapper.treeToValue(vp, VisibilityProfile.class);
            return mergeFlatAndJson(flat, fromJson);
        } catch (Exception e) {
            return flat;
        }
    }

    /**
     * Non-null fields from {@code json} replace those from {@code flat}; null/absent JSON fields keep flat values.
     */
    static VisibilityProfile mergeFlatAndJson(VisibilityProfile flat, VisibilityProfile json) {
        if (json == null) {
            return flat;
        }
        if (flat == null) {
            return json;
        }
        VisibilityProfile.Builder b = VisibilityProfile.builder(flat);
        if (json.visibilityTier() != null) {
            b.visibilityTier(json.visibilityTier());
        }
        if (json.piiAccess() != null) {
            b.piiAccess(json.piiAccess());
        }
        if (json.clinicalAccess() != null) {
            b.clinicalAccess(json.clinicalAccess());
        }
        if (json.aggregateOnly() != null) {
            b.aggregateOnly(json.aggregateOnly());
        }
        if (json.resourceSensitivityClass() != null) {
            b.resourceSensitivityClass(json.resourceSensitivityClass());
        }
        if (json.escalationGrantId() != null) {
            b.escalationGrantId(json.escalationGrantId());
        }
        if (json.workflowContext() != null) {
            b.workflowContext(json.workflowContext());
        }
        if (json.suppressFields() != null) {
            b.suppressFields(json.suppressFields());
        }
        if (json.pseudonymiseFields() != null) {
            b.pseudonymiseFields(json.pseudonymiseFields());
        }
        if (json.exportPolicy() != null) {
            b.exportPolicy(json.exportPolicy());
        }
        if (json.drillDownAllowed() != null) {
            b.drillDownAllowed(json.drillDownAllowed());
        }
        return b.build();
    }
}
