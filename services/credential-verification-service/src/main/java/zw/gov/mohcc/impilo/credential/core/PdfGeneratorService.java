package zw.gov.mohcc.impilo.credential.core;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.credential.config.CredentialProperties;
import zw.gov.mohcc.impilo.credential.persistence.entity.CredentialEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * PDF certificate generator using Apache PDFBox 3.x and ZXing QR codes.
 *
 * Produces professional PDF certificates with:
 *   - Title, subject name, credential type, issuer, dates
 *   - QR code embedding verification URL and Ed25519 signature
 *   - Institutional branding
 */
@Service
public class PdfGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(PdfGeneratorService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm")
            .withZone(ZoneId.of("Africa/Harare"));

    private final CredentialProperties properties;

    public PdfGeneratorService(CredentialProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a professional PDF certificate for the given credential.
     *
     * @param entity the credential entity containing all certificate data
     * @return byte array of the generated PDF document
     */
    public byte[] generateCertificatePdf(CredentialEntity entity) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float pageHeight = page.getMediaBox().getHeight();
            float margin = 60;
            float contentWidth = pageWidth - 2 * margin;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float y = pageHeight - margin;

                // Draw border
                cs.setLineWidth(2);
                cs.addRect(30, 30, pageWidth - 60, pageHeight - 60);
                cs.stroke();

                // Inner border
                cs.setLineWidth(0.5f);
                cs.addRect(35, 35, pageWidth - 70, pageHeight - 70);
                cs.stroke();

                // Issuer name (header)
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontItalic = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

                y -= 20;
                y = drawCenteredText(cs, properties.getPdf().getIssuerName(), fontBold, 14, pageWidth, y);

                // Horizontal rule
                y -= 15;
                cs.setLineWidth(1);
                cs.moveTo(margin, y);
                cs.lineTo(pageWidth - margin, y);
                cs.stroke();

                // Certificate title
                y -= 35;
                y = drawCenteredText(cs, "CERTIFICATE", fontBold, 24, pageWidth, y);

                y -= 10;
                y = drawCenteredText(cs, "OF", fontRegular, 12, pageWidth, y);

                y -= 15;
                String credTypeDisplay = formatCredentialType(entity.getCredentialType());
                y = drawCenteredText(cs, credTypeDisplay, fontBold, 20, pageWidth, y);

                // Certificate title
                y -= 30;
                y = drawCenteredText(cs, entity.getTitle(), fontBold, 16, pageWidth, y);

                // "This is to certify that"
                y -= 30;
                y = drawCenteredText(cs, "This is to certify that", fontItalic, 12, pageWidth, y);

                // Subject name (large, bold)
                y -= 25;
                y = drawCenteredText(cs, entity.getSubjectName(), fontBold, 22, pageWidth, y);

                // Decorative line under name
                y -= 10;
                float nameLineWidth = 200;
                cs.setLineWidth(0.5f);
                cs.moveTo((pageWidth - nameLineWidth) / 2, y);
                cs.lineTo((pageWidth + nameLineWidth) / 2, y);
                cs.stroke();

                // Details section
                y -= 30;
                y = drawCenteredText(cs, "has been awarded this credential by", fontItalic, 11, pageWidth, y);

                y -= 20;
                y = drawCenteredText(cs, entity.getIssuedBy(), fontBold, 14, pageWidth, y);

                // Dates section
                y -= 35;
                String validFromStr = entity.getValidFrom().format(DATE_FORMATTER);
                String dateInfo = "Valid from: " + validFromStr;
                if (entity.getValidTo() != null) {
                    dateInfo += "   |   Valid to: " + entity.getValidTo().format(DATE_FORMATTER);
                }
                y = drawCenteredText(cs, dateInfo, fontRegular, 10, pageWidth, y);

                y -= 15;
                String issuedAtStr = DATETIME_FORMATTER.format(entity.getIssuedAt());
                y = drawCenteredText(cs, "Issued: " + issuedAtStr, fontRegular, 10, pageWidth, y);

                // Status
                y -= 15;
                y = drawCenteredText(cs, "Status: " + entity.getStatus(), fontBold, 10, pageWidth, y);

                // Credential ID
                y -= 15;
                y = drawCenteredText(cs, "Credential ID: " + entity.getCredentialId().toString(), fontRegular, 8, pageWidth, y);

                // QR code
                String verificationUrl = properties.getVerify().getBaseUrl() + "/" + entity.getVerificationToken();
                String qrPayload = verificationUrl + "|sig:" + entity.getSignatureHex();
                byte[] qrImageBytes = generateQrCodeImage(qrPayload, 150, 150);

                if (qrImageBytes != null) {
                    PDImageXObject qrImage = PDImageXObject.createFromByteArray(document, qrImageBytes, "qr");
                    float qrSize = 100;
                    float qrX = (pageWidth - qrSize) / 2;
                    float qrY = y - qrSize - 20;
                    cs.drawImage(qrImage, qrX, qrY, qrSize, qrSize);

                    // QR label
                    float labelY = qrY - 15;
                    drawCenteredText(cs, "Scan to verify", fontItalic, 8, pageWidth, labelY);

                    labelY -= 12;
                    drawCenteredText(cs, verificationUrl, fontRegular, 7, pageWidth, labelY);
                }

                // Footer
                float footerY = 55;
                drawCenteredText(cs, "This document is digitally signed using Ed25519.", fontItalic, 7, pageWidth, footerY);
                drawCenteredText(cs, "Verify authenticity by scanning the QR code above.", fontItalic, 7, pageWidth, footerY - 10);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            document.save(baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate PDF certificate for credential {}: {}",
                    entity.getCredentialId(), e.getMessage(), e);
            throw new RuntimeException("PDF generation failed", e);
        }
    }

    private float drawCenteredText(PDPageContentStream cs, String text,
                                   PDType1Font font, float fontSize,
                                   float pageWidth, float y) throws Exception {
        float textWidth = font.getStringWidth(text) / 1000 * fontSize;
        float x = (pageWidth - textWidth) / 2;
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
        return y;
    }

    private byte[] generateQrCodeImage(String content, int width, int height) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.MARGIN, 1,
                    EncodeHintType.CHARACTER_SET, "UTF-8"
            );
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, width, height, hints);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < width; x++) {
                for (int yPx = 0; yPx < height; yPx++) {
                    image.setRGB(x, yPx, bitMatrix.get(x, yPx) ? 0x000000 : 0xFFFFFF);
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();

        } catch (Exception e) {
            log.error("Failed to generate QR code: {}", e.getMessage());
            return null;
        }
    }

    private String formatCredentialType(String credentialType) {
        if (credentialType == null) return "CREDENTIAL";
        return switch (credentialType.toUpperCase()) {
            case "LICENSE" -> "PROFESSIONAL LICENSE";
            case "CERTIFICATE" -> "CERTIFICATE";
            case "REGISTRATION" -> "REGISTRATION";
            case "BADGE" -> "DIGITAL BADGE";
            default -> credentialType.toUpperCase();
        };
    }
}
