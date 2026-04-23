package zw.gov.mohcc.impilo.reporting.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import zw.gov.mohcc.impilo.shared.visibility.JsonRepresentationShaper;
import zw.gov.mohcc.impilo.tshepo.contracts.dto.VisibilityProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Applies export-time redaction for {@link ExportFormat#JSON} and {@link ExportFormat#CSV}
 * when {@link zw.gov.mohcc.impilo.shared.visibility.ExportVisibilityGuard#requiresRedactedReportOutput}
 * is satisfied.
 */
public final class ExportReportOutputRedactor {

    private static final Pattern SENSITIVE_HEADER = Pattern.compile(
            "(?i).*(phone|mobile|e_?mail|firstname|lastname|family|surname|given|address|street|"
                    + "national|id_?number|dob|date_?of_?birth|birth|passport|ssn|patient_?name|full_?name).*");

    private ExportReportOutputRedactor() {
    }

    public static String apply(String payload, ExportFormat format, VisibilityProfile profile,
                               ObjectMapper mapper) {
        if (payload == null || payload.isBlank() || profile == null) {
            return payload;
        }
        try {
            if (format == ExportFormat.CSV) {
                return redactCsv(payload);
            }
            Object tree = mapper.readValue(payload, Object.class);
            JsonNode shaped = JsonRepresentationShaper.shape(mapper, tree, profile);
            return mapper.writeValueAsString(shaped);
        } catch (Exception e) {
            return payload;
        }
    }

    private static String redactCsv(String csv) {
        String[] lines = csv.split("\\r?\\n", -1);
        if (lines.length == 0) {
            return csv;
        }
        String headerLine = lines[0];
        if (headerLine.isBlank()) {
            return csv;
        }
        String[] headers = headerLine.split(",", -1);
        List<Integer> redactCols = new ArrayList<>();
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().replace("\"", "");
            if (SENSITIVE_HEADER.matcher(h).matches()) {
                redactCols.add(i);
            }
        }
        if (redactCols.isEmpty()) {
            return csv;
        }
        StringBuilder out = new StringBuilder();
        out.append(lines[0]).append('\n');
        for (int r = 1; r < lines.length; r++) {
            String line = lines[r];
            if (line.isEmpty()) {
                out.append('\n');
                continue;
            }
            List<String> cells = splitCsvLine(line);
            for (int c : redactCols) {
                if (c < cells.size()) {
                    cells.set(c, "***");
                }
            }
            out.append(String.join(",", cells)).append('\n');
        }
        return out.toString();
    }

    /**
     * Minimal CSV line split respecting quoted fields (commas inside quotes).
     */
    private static List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                    cur.append(ch);
                }
            } else if (ch == ',' && !inQuotes) {
                cells.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        cells.add(cur.toString());
        return cells;
    }
}
