package zw.gov.mohcc.impilo.varapi.core;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.varapi.persistence.entity.LicenseEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Renders a real, printable licence certificate PDF from the persisted licence
 * record. Replaces the {@code UnsupportedOperationException} that previously 500'd
 * the live download route (G018). Self-contained (Apache PDFBox); no external
 * reporting service required.
 */
@Component
public class LicenseCertificatePdfGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd MMMM yyyy");

    public byte[] generate(LicenseEntity license) {
        ProviderEntity provider = license.getProvider();
        String providerName = join(provider.getTitle(), provider.getGivenName(), provider.getFamilyName());
        String councilName = license.getCouncil() != null ? license.getCouncil().getName() : "—";

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float left = 70;
                float y = 770;

                y = line(cs, bold, 20, left, y, "Practitioner Licence Certificate");
                y -= 8;
                y = line(cs, regular, 11, left, y,
                        "Issued via the Impilo Health Operating System — Regulatory Plane (VARAPI)");
                y -= 18;

                y = field(cs, bold, regular, left, y, "Practitioner", providerName);
                y = field(cs, bold, regular, left, y, "Provider ID", provider.getProviderPublicId());
                y = field(cs, bold, regular, left, y, "Licence type", license.getLicenseType());
                y = field(cs, bold, regular, left, y, "Licence number", license.getLicenseNumber());
                y = field(cs, bold, regular, left, y, "Regulatory council", councilName);
                y = field(cs, bold, regular, left, y, "Status", license.getStatus());
                y = field(cs, bold, regular, left, y, "Valid from",
                        license.getValidFrom() != null ? license.getValidFrom().format(DATE) : "—");
                y = field(cs, bold, regular, left, y, "Valid to",
                        license.getValidTo() != null ? license.getValidTo().format(DATE) : "—");
                if (license.getConditions() != null && !license.getConditions().isBlank()) {
                    y = field(cs, bold, regular, left, y, "Conditions", license.getConditions());
                }
                y = field(cs, bold, regular, left, y, "Issued by",
                        license.getIssuedBy() != null ? license.getIssuedBy() : "—");
                y = field(cs, bold, regular, left, y, "Issued at",
                        license.getIssuedAt() != null ? license.getIssuedAt().toString() : "—");

                y -= 24;
                line(cs, regular, 9, left, y,
                        "This certificate is generated from the authoritative VARAPI licence record. "
                                + "Verify status against the issuing council before reliance.");
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render licence certificate PDF", e);
        }
    }

    private static float field(PDPageContentStream cs, PDType1Font labelFont, PDType1Font valueFont,
                               float x, float y, String label, String value) throws IOException {
        line(cs, labelFont, 11, x, y, sanitize(label) + ":");
        return line(cs, valueFont, 12, x + 140, y, sanitize(value)) - 6;
    }

    private static float line(PDPageContentStream cs, PDType1Font font, float size,
                              float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(sanitize(text));
        cs.endText();
        return y - (size + 8);
    }

    private static String join(String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                kept.add(p.trim());
            }
        }
        return kept.isEmpty() ? "-" : String.join(" ", kept);
    }

    /**
     * WinAnsi-safe: PDFBox Standard-14 fonts encode CP1252 only, so replace control
     * characters and anything outside the printable WinAnsi range with '?' to avoid a
     * runtime encoding exception on exotic names.
     */
    private static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (char c : text.replaceAll("[\\r\\n\\t]", " ").trim().toCharArray()) {
            if ((c >= 0x20 && c <= 0x7E) || (c >= 0xA0 && c <= 0xFF)) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.length() == 0 ? "-" : sb.toString();
    }
}
