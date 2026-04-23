package zw.gov.mohcc.impilo.experience.intake;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RFC 4180–oriented CSV parsing (via Apache Commons CSV) for governed import preview.
 */
public final class CsvLineParser {

    private CsvLineParser() {
    }

    public static List<String[]> parseRows(String csvText) {
        List<String[]> rows = new ArrayList<>();
        if (csvText == null || csvText.isBlank()) {
            return rows;
        }
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setIgnoreEmptyLines(true)
                .setTrim(true)
                .build();
        try (CSVParser parser = new CSVParser(new StringReader(csvText), format)) {
            for (CSVRecord rec : parser) {
                int n = rec.size();
                String[] arr = new String[n];
                for (int i = 0; i < n; i++) {
                    arr[i] = rec.get(i);
                }
                rows.add(arr);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid CSV: " + e.getMessage(), e);
        }
        return rows;
    }

    public static Map<String, Integer> headerIndex(String[] header) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = header[i] == null ? "" : header[i].trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!key.isEmpty()) {
                map.put(key, i);
            }
        }
        return map;
    }

    public static String cell(String[] row, Map<String, Integer> idx, String... aliases) {
        for (String a : aliases) {
            String k = a.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            Integer i = idx.get(k);
            if (i != null && i < row.length) {
                String v = row[i].trim();
                if (!v.isEmpty()) {
                    return v;
                }
            }
        }
        return "";
    }
}
