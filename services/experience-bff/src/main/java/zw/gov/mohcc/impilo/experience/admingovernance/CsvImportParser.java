package zw.gov.mohcc.impilo.experience.admingovernance;

import java.util.*;

final class CsvImportParser {
    private CsvImportParser() {}

    static List<Map<String, String>> parseCsv(String content) {
        if (content == null || content.isBlank()) return List.of();
        String[] lines = content.replace("\r", "").split("\n");
        if (lines.length < 2) return List.of();
        String[] headers = Arrays.stream(lines[0].split(",")).map(h -> h.trim().toLowerCase(Locale.ROOT)).toArray(String[]::new);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            String[] values = lines[i].split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int c = 0; c < headers.length && c < values.length; c++) {
                row.put(headers[c], values[c].trim());
            }
            rows.add(row);
        }
        return rows;
    }
}
