package zw.gov.mohcc.impilo.experience.shell;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.experience.client.ReportingServiceClient;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;
import zw.gov.mohcc.impilo.shared.visibility.ExportVisibilityGuard;
import zw.gov.mohcc.impilo.shared.visibility.VisibilityHeaderParser;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates vNext file-explorer rows from reporting runs and (optionally) facility regulatory certificates.
 */
@Service
public class ShellFileCatalogService {

    private static final Logger log = LoggerFactory.getLogger(ShellFileCatalogService.class);

    private final ReportingServiceClient reportingClient;
    private final TusoServiceClient tusoServiceClient;
    private final ObjectMapper objectMapper;

    public ShellFileCatalogService(
            ReportingServiceClient reportingClient,
            TusoServiceClient tusoServiceClient,
            ObjectMapper objectMapper) {
        this.reportingClient = reportingClient;
        this.tusoServiceClient = tusoServiceClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> buildCatalog(HttpServletRequest request, String facilityIdOrNull) {
        VisibilityProfile visibility = VisibilityHeaderParser.resolve(request, objectMapper);
        List<Map<String, Object>> reportRows = listReportRuns(visibility);
        List<Map<String, Object>> certRows = new ArrayList<>();
        if (facilityIdOrNull != null && !facilityIdOrNull.isBlank()) {
            certRows.addAll(listFacilityCertificates(facilityIdOrNull.trim()));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("reportRuns", reportRows);
        data.put("facilityCertificates", certRows);
        return data;
    }

    private List<Map<String, Object>> listReportRuns(VisibilityProfile visibility) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (ExportVisibilityGuard.deniesReportRun(visibility)) {
            return out;
        }
        try {
            JsonNode root = reportingClient.listTenantReportRuns(0, 25);
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return out;
            }
            for (JsonNode it : items) {
                if (!it.isObject()) {
                    continue;
                }
                String runId = text(it, "runId");
                String reportKey = text(it, "reportKey");
                if (runId.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "report-run:" + runId);
                row.put("resourceType", "generated_output");
                row.put("title", (reportKey.isEmpty() ? "Report" : reportKey) + " · " + runId.substring(0, 8));
                row.put("associatedApp", "reporting");
                row.put("category", reportKey);
                row.put("createdAt", text(it, "createdAt"));
                row.put("href", "/admin/data-export?highlight=" + runId);
                row.put("downloadAction", "navigate");
                out.add(row);
            }
        } catch (Exception e) {
            log.debug("shell file catalog: reporting tenant-runs skipped: {}", e.getMessage());
        }
        return out;
    }

    private List<Map<String, Object>> listFacilityCertificates(String facilityId) {
        List<Map<String, Object>> out = new ArrayList<>();
        try {
            JsonNode profile = tusoServiceClient.getFacilityRegulatoryProfile(facilityId);
            JsonNode certs = resolveCertificatesArray(profile);
            if (certs == null || !certs.isArray()) {
                return out;
            }
            for (JsonNode c : certs) {
                if (!c.isObject()) {
                    continue;
                }
                String certId = certificateIdOf(c);
                if (certId.isEmpty()) {
                    continue;
                }
                JsonNode attrs = c.path("attributes");
                String num = coalesce(text(c, "certificateNumber"), text(attrs, "certificateNumber"));
                String ctype = coalesce(text(c, "certificateType"), text(attrs, "certificateType"));
                String status = coalesce(text(c, "status"), text(attrs, "status"));
                String issue = coalesce(text(c, "issueDate"), text(attrs, "issueDate"));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", "facility-cert:" + facilityId + ":" + certId);
                row.put("resourceType", "registry_certificate");
                row.put("title", (num.isEmpty() ? certId : num) + " · " + (ctype.isEmpty() ? "certificate" : ctype));
                row.put("associatedApp", "registry");
                row.put("category", status);
                row.put("createdAt", issue);
                row.put("href", "/registry/facilities/" + facilityId + "?tab=certificates");
                row.put("downloadAction", "navigate");
                out.add(row);
            }
        } catch (Exception e) {
            log.debug("shell file catalog: facility certificates skipped for {}: {}", facilityId, e.getMessage());
        }
        return out;
    }

    private static final String[] CERTIFICATE_PATHS = {
            "certificates",
            "data/certificates",
            "data/attributes/certificates",
            "attributes/certificates",
            "facility/certificates",
            "facilityProfile/certificates",
            "facilityRegulatory/certificates",
            "regulatoryProfile/certificates",
            "regulatoryProfile/data/certificates",
            "profile/certificates",
            "result/certificates",
            "payload/certificates",
            "content/certificates",
            "data/attributes/facility/certificates",
            "attributes/facility/certificates",
            "entity/certificates",
            "regulatory/certificates",
            "relationships/certificates"
    };

    private JsonNode resolveCertificatesArray(JsonNode profile) {
        if (profile == null || profile.isNull()) {
            return null;
        }
        ArrayNode merged = objectMapper.createArrayNode();
        Set<String> seenIds = new LinkedHashSet<>();
        for (String path : CERTIFICATE_PATHS) {
            appendCertificateObjects(merged, seenIds, atSlashPath(profile, path));
        }
        appendCertificateObjectsFromIncluded(merged, seenIds, profile.path("included"));
        collectNestedCertificateArrays(merged, seenIds, profile, 0, 5);
        return merged.size() > 0 ? merged : null;
    }

    private static JsonNode atSlashPath(JsonNode root, String slashPath) {
        JsonNode cur = root;
        for (String seg : slashPath.split("/")) {
            if (seg.isEmpty()) {
                continue;
            }
            cur = cur.path(seg);
        }
        return cur;
    }

    private void appendCertificateObjects(ArrayNode merged, Set<String> seenIds, JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode el : node) {
                addOneCertificate(merged, seenIds, el);
            }
        }
    }

    private void appendCertificateObjectsFromIncluded(ArrayNode merged, Set<String> seenIds, JsonNode included) {
        if (!included.isArray()) {
            return;
        }
        for (JsonNode res : included) {
            if (!res.isObject()) {
                continue;
            }
            String type = text(res, "type").toLowerCase();
            if (type.contains("certificate") || !certificateIdOf(res).isEmpty()) {
                addOneCertificate(merged, seenIds, res);
            }
            appendCertificateObjects(merged, seenIds, res.path("attributes").path("certificates"));
        }
    }

    private void collectNestedCertificateArrays(ArrayNode merged, Set<String> seenIds, JsonNode node, int depth, int maxDepth) {
        if (node == null || !node.isObject() || depth > maxDepth) {
            return;
        }
        var it = node.fields();
        while (it.hasNext()) {
            var e = it.next();
            String key = e.getKey();
            JsonNode v = e.getValue();
            if ("certificates".equals(key) && v.isArray()) {
                appendCertificateObjects(merged, seenIds, v);
            } else if (v.isObject()) {
                collectNestedCertificateArrays(merged, seenIds, v, depth + 1, maxDepth);
            } else if (v.isArray() && !"included".equals(key)) {
                for (JsonNode el : v) {
                    if (el.isObject()) {
                        collectNestedCertificateArrays(merged, seenIds, el, depth + 1, maxDepth);
                    }
                }
            }
        }
    }

    private void addOneCertificate(ArrayNode merged, Set<String> seenIds, JsonNode c) {
        if (c == null || !c.isObject()) {
            return;
        }
        String id = certificateIdOf(c);
        if (id.isEmpty()) {
            return;
        }
        if (seenIds.add(id)) {
            merged.add(c);
        }
    }

    private static String certificateIdOf(JsonNode c) {
        String id = text(c, "certificateId");
        if (!id.isEmpty()) {
            return id;
        }
        id = text(c, "id");
        if (!id.isEmpty()) {
            return id;
        }
        JsonNode attrs = c.path("attributes");
        if (attrs.isObject()) {
            id = coalesce(text(attrs, "certificateId"), text(attrs, "id"));
        }
        return id;
    }

    private static String coalesce(String... parts) {
        if (parts == null) {
            return "";
        }
        for (String p : parts) {
            if (p != null && !p.isEmpty()) {
                return p;
            }
        }
        return "";
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.path(field);
        if (v.isMissingNode() || v.isNull()) {
            return "";
        }
        if (v.isTextual()) {
            return v.asText("").trim();
        }
        return v.toString().trim();
    }
}
