package zw.gov.mohcc.impilo.surv.core;

import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceBriefEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.IntelligenceBriefRepository;

@Service
public class IntelligenceBriefService {

    private final IntelligenceBriefRepository briefRepository;
    private final PublicHealthOperationsHomeService operationsHomeService;

    public IntelligenceBriefService(
            IntelligenceBriefRepository briefRepository,
            PublicHealthOperationsHomeService operationsHomeService) {
        this.briefRepository = briefRepository;
        this.operationsHomeService = operationsHomeService;
    }

    @Transactional(readOnly = true)
    public List<IntelligenceBriefEntity> listBriefs(UUID tenantId) {
        return briefRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public IntelligenceBriefEntity generateDraft(UUID tenantId, String actorId, String title, String briefType) {
        Map<String, Object> home = operationsHomeService.buildHome(tenantId);
        String summary = buildDeterministicSummary(home);

        IntelligenceBriefEntity brief = new IntelligenceBriefEntity();
        brief.setTenantId(tenantId);
        brief.setTitle(title != null && !title.isBlank() ? title : "Public Health Situation Brief");
        brief.setBriefType(briefType != null && !briefType.isBlank() ? briefType : "SITUATION");
        brief.setStatus(BriefStatus.DRAFT);
        brief.setSummaryMarkdown(summary);
        brief.setGeneratedBy(actorId);
        return briefRepository.save(brief);
    }

    @Transactional
    public IntelligenceBriefEntity publish(UUID tenantId, Long briefId) {
        IntelligenceBriefEntity brief = briefRepository.findById(briefId)
                .filter(b -> tenantId.equals(b.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));
        brief.setStatus(BriefStatus.PUBLISHED);
        brief.setPublishedAt(OffsetDateTime.now());
        return briefRepository.save(brief);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> export(UUID tenantId, Long briefId, String format) {
        IntelligenceBriefEntity brief = briefRepository.findById(briefId)
                .filter(b -> tenantId.equals(b.getTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Brief not found"));
        String requestedFormat = normalizeFormat(format);
        String fileName = "public-health-brief-" + briefId + "." + requestedFormat.toLowerCase();
        byte[] payload = switch (requestedFormat) {
            case "CSV" -> renderCsv(brief).getBytes(StandardCharsets.UTF_8);
            case "PDF" -> renderPdf(brief);
            default -> throw new IllegalArgumentException("Unsupported format");
        };
        return Map.of(
                "brief_id", briefId,
                "format", requestedFormat,
                "file_name", fileName,
                "content_type", requestedFormat.equals("CSV") ? "text/csv" : "application/pdf",
                "content_base64", Base64.getEncoder().encodeToString(payload),
                "generated_at", OffsetDateTime.now().toString(),
                "source", "DETERMINISTIC_BRIEF_EXPORT");
    }

    @SuppressWarnings("unchecked")
    private String buildDeterministicSummary(Map<String, Object> home) {
        Map<String, Object> kpis = (Map<String, Object>) home.getOrDefault("kpis", Map.of());
        StringBuilder sb = new StringBuilder();
        sb.append("## Situation summary (deterministic)\n\n");
        sb.append("- Active signals: ").append(kpis.getOrDefault("active_signals", 0)).append("\n");
        sb.append("- Open cases: ").append(kpis.getOrDefault("open_cases", 0)).append("\n");
        sb.append("- Unacknowledged alerts: ").append(kpis.getOrDefault("open_alerts", 0)).append("\n");
        sb.append("- Active outbreaks: ").append(kpis.getOrDefault("active_outbreaks", 0)).append("\n");
        sb.append("- Field tasks in progress: ").append(kpis.getOrDefault("field_tasks_in_progress", 0)).append("\n");
        sb.append("- Open investigations: ").append(kpis.getOrDefault("open_investigations", 0)).append("\n");
        sb.append("- Open environmental complaints: ").append(kpis.getOrDefault("open_complaints", 0)).append("\n");
        sb.append("\n_Generated from live surveillance operations home aggregate._\n");
        return sb.toString();
    }

    private String normalizeFormat(String raw) {
        if (raw == null || raw.isBlank()) {
            return "PDF";
        }
        String format = raw.trim().toUpperCase();
        return switch (format) {
            case "PDF", "CSV" -> format;
            default -> "PDF";
        };
    }

    private String renderCsv(IntelligenceBriefEntity brief) {
        String markdown = brief.getSummaryMarkdown() == null ? "" : brief.getSummaryMarkdown();
        String[] lines = markdown.split("\\R");
        StringBuilder csv = new StringBuilder("metric,value\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("- ")) {
                continue;
            }
            String body = trimmed.substring(2);
            int colon = body.indexOf(':');
            if (colon <= 0 || colon == body.length() - 1) {
                continue;
            }
            String metric = csvEscape(body.substring(0, colon).trim());
            String value = csvEscape(body.substring(colon + 1).trim());
            csv.append(metric).append(',').append(value).append('\n');
        }
        return csv.toString();
    }

    private byte[] renderPdf(IntelligenceBriefEntity brief) {
        StringBuilder body = new StringBuilder();
        body.append("BT\n/F1 11 Tf\n50 770 Td\n");
        appendPdfLine(body, "PUBLIC HEALTH INTELLIGENCE BRIEF");
        appendPdfLine(body, "Title: " + brief.getTitle());
        appendPdfLine(body, "Type: " + brief.getBriefType());
        appendPdfLine(body, "Status: " + brief.getStatus());
        appendPdfLine(body, "Generated by: " + brief.getGeneratedBy());
        appendPdfLine(body, "Published at: " + (brief.getPublishedAt() != null ? brief.getPublishedAt() : "N/A"));
        appendPdfLine(body, " ");
        String markdown = brief.getSummaryMarkdown() == null ? "" : brief.getSummaryMarkdown();
        for (String line : markdown.split("\\R")) {
            appendPdfLine(body, line);
        }
        body.append("ET\n");

        String stream = body.toString();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes("%PDF-1.4\n".getBytes(StandardCharsets.US_ASCII));
        int[] offsets = new int[6];

        offsets[1] = out.size();
        out.writeBytes("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        offsets[2] = out.size();
        out.writeBytes("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        offsets[3] = out.size();
        out.writeBytes((
                "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] " +
                        "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>\nendobj\n")
                .getBytes(StandardCharsets.US_ASCII));
        offsets[4] = out.size();
        out.writeBytes("4 0 obj\n<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>\nendobj\n"
                .getBytes(StandardCharsets.US_ASCII));
        offsets[5] = out.size();
        out.writeBytes(("5 0 obj\n<< /Length " + stream.getBytes(StandardCharsets.US_ASCII).length + " >>\nstream\n")
                .getBytes(StandardCharsets.US_ASCII));
        out.writeBytes(stream.getBytes(StandardCharsets.US_ASCII));
        out.writeBytes("endstream\nendobj\n".getBytes(StandardCharsets.US_ASCII));

        int xrefOffset = out.size();
        out.writeBytes("xref\n0 6\n".getBytes(StandardCharsets.US_ASCII));
        out.writeBytes("0000000000 65535 f \n".getBytes(StandardCharsets.US_ASCII));
        for (int i = 1; i <= 5; i++) {
            out.writeBytes(String.format("%010d 00000 n \n", offsets[i]).getBytes(StandardCharsets.US_ASCII));
        }
        out.writeBytes(("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n")
                .getBytes(StandardCharsets.US_ASCII));
        return out.toByteArray();
    }

    private void appendPdfLine(StringBuilder body, String raw) {
        if (raw == null) {
            return;
        }
        String sanitized = raw.replace('\\', '/')
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replaceAll("[^\\x20-\\x7E]", "?");
        if (sanitized.length() > 110) {
            sanitized = sanitized.substring(0, 110);
        }
        body.append("(").append(sanitized).append(") Tj\nT*\n");
    }

    private String csvEscape(String raw) {
        String value = raw.replace("\"", "\"\"");
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value + "\"";
        }
        return value;
    }
}
