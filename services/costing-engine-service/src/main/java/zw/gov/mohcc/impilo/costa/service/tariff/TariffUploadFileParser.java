package zw.gov.mohcc.impilo.costa.service.tariff;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses tariff upload files into header-keyed row maps (string values, trimmed).
 */
public final class TariffUploadFileParser {

    private TariffUploadFileParser() {}

    public static List<Map<String, String>> parse(String fileType, byte[] rawBytes, ObjectMapper objectMapper)
            throws IOException {
        String ft = fileType == null ? "" : fileType.toLowerCase();
        if (ft.contains("json")) {
            return parseJson(rawBytes, objectMapper);
        }
        if (ft.contains("csv")) {
            return parseCsv(rawBytes);
        }
        if (ft.contains("xlsx") || ft.contains("xls")) {
            return parseXlsx(rawBytes);
        }
        throw new IllegalArgumentException("Unsupported file_type for parsing: " + fileType);
    }

    private static List<Map<String, String>> parseCsv(byte[] rawBytes) throws IOException {
        String text = new String(rawBytes, StandardCharsets.UTF_8);
        try (CSVReader reader = new CSVReader(new StringReader(text))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                return List.of();
            }
            String[] header = rows.get(0);
            List<String> names = new ArrayList<>();
            for (String h : header) {
                names.add(h != null ? h.trim() : "");
            }
            List<Map<String, String>> out = new ArrayList<>();
            for (int i = 1; i < rows.size(); i++) {
                String[] cells = rows.get(i);
                if (isEmptyRow(cells)) {
                    continue;
                }
                Map<String, String> m = new LinkedHashMap<>();
                for (int c = 0; c < names.size(); c++) {
                    String col = names.get(c);
                    if (col.isEmpty()) {
                        col = "column_" + c;
                    }
                    String v = c < cells.length && cells[c] != null ? cells[c].trim() : "";
                    m.put(col, v);
                }
                out.add(m);
            }
            return out;
        } catch (CsvException e) {
            throw new IOException("CSV parse failed", e);
        }
    }

    private static boolean isEmptyRow(String[] cells) {
        if (cells == null) {
            return true;
        }
        for (String c : cells) {
            if (c != null && !c.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static List<Map<String, String>> parseXlsx(byte[] rawBytes) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(rawBytes))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                return List.of();
            }
            List<String> names = new ArrayList<>();
            for (int c = 0; c < headerRow.getLastCellNum(); c++) {
                Cell cell = headerRow.getCell(c);
                String h = cell != null ? fmt.formatCellValue(cell).trim() : "";
                if (h.isEmpty()) {
                    h = "column_" + c;
                }
                names.add(h);
            }
            List<Map<String, String>> out = new ArrayList<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Map<String, String> m = new LinkedHashMap<>();
                boolean any = false;
                for (int c = 0; c < names.size(); c++) {
                    Cell cell = row.getCell(c);
                    String v = cell != null ? fmt.formatCellValue(cell).trim() : "";
                    if (!v.isEmpty()) {
                        any = true;
                    }
                    m.put(names.get(c), v);
                }
                if (any) {
                    out.add(m);
                }
            }
            return out;
        }
    }

    private static List<Map<String, String>> parseJson(byte[] rawBytes, ObjectMapper objectMapper) throws IOException {
        JsonNode root = objectMapper.readTree(rawBytes);
        JsonNode array;
        if (root.isArray()) {
            array = root;
        } else if (root.has("items") && root.get("items").isArray()) {
            array = root.get("items");
        } else if (root.has("rows") && root.get("rows").isArray()) {
            array = root.get("rows");
        } else {
            throw new IllegalArgumentException("JSON tariff upload must be an array or contain items[] / rows[]");
        }
        List<Map<String, String>> out = new ArrayList<>();
        for (JsonNode n : array) {
            if (!n.isObject()) {
                continue;
            }
            Map<String, String> m = new LinkedHashMap<>();
            n.fields().forEachRemaining(e -> m.put(e.getKey(),
                    e.getValue() != null && !e.getValue().isNull() ? e.getValue().asText().trim() : ""));
            out.add(m);
        }
        return out;
    }
}
