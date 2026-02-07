package zw.gov.mohcc.impilo.cardprint.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
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
import zw.gov.mohcc.impilo.cardprint.entity.PrintJobEntity;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Service responsible for rendering print jobs into PDF documents.
 * Supports provider cards, client cards, share slips, and emergency capsules.
 * Uses Apache PDFBox for PDF generation and ZXing for QR code encoding.
 */
@Service
public class PdfRenderService {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderService.class);

    // CR-80 card dimensions in points (85.6mm x 53.98mm at 72 DPI)
    private static final float CARD_WIDTH = 242.65f;
    private static final float CARD_HEIGHT = 153.01f;

    // A5 dimensions in points (148mm x 210mm at 72 DPI)
    private static final float A5_WIDTH = 419.53f;
    private static final float A5_HEIGHT = 595.28f;

    private static final int QR_SIZE_PX = 150;

    private final QrAssertionService qrAssertionService;

    public PdfRenderService(QrAssertionService qrAssertionService) {
        this.qrAssertionService = qrAssertionService;
    }

    /**
     * Renders a provider smart card PDF with photo placeholder, QR code, and practitioner details.
     */
    public byte[] renderProviderCard(PrintJobEntity job) throws IOException {
        Map<String, Object> data = job.getPayload();
        String qrAssertion = qrAssertionService.generateSignedAssertion(
                job.getSubjectType(), job.getSubjectId(), job.getJobId().toString());
        job.setQrAssertion(qrAssertion);

        PDRectangle cardSize = new PDRectangle(CARD_WIDTH, CARD_HEIGHT);

        try (PDDocument doc = new PDDocument()) {
            // ---- Front of card ----
            PDPage front = new PDPage(cardSize);
            doc.addPage(front);

            try (PDPageContentStream cs = new PDPageContentStream(doc, front)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontMono = new PDType1Font(Standard14Fonts.FontName.COURIER);

                // Header block
                drawText(cs, fontBold, 8, 10, CARD_HEIGHT - 15, "REPUBLIC OF ZIMBABWE");
                drawText(cs, fontBold, 6, 10, CARD_HEIGHT - 25, "MINISTRY OF HEALTH AND CHILD CARE");
                drawText(cs, fontBold, 7, 10, CARD_HEIGHT - 37, "HEALTH PROVIDER SMART CARD");

                // Data fields
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 55, "Name:",
                        getStringValue(data, "fullName"));
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 67, "ID:",
                        getStringValue(data, "practitionerId"));
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 79, "Cadre:",
                        getStringValue(data, "cadre"));
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 91, "Facility:",
                        getStringValue(data, "facility"));

                // Photo placeholder
                cs.addRect(CARD_WIDTH - 55, CARD_HEIGHT - 60, 45, 50);
                cs.stroke();
                drawText(cs, fontRegular, 5, CARD_WIDTH - 48, CARD_HEIGHT - 38, "PHOTO");

                // QR code
                byte[] qrImage = generateQrCodeImage(qrAssertion);
                PDImageXObject qrPdImage = PDImageXObject.createFromByteArray(doc, qrImage, "qr.png");
                cs.drawImage(qrPdImage, 10, 8, 40, 40);

                // Footer
                drawText(cs, fontMono, 4, 55, 15, "Card #" + job.getJobId().toString().substring(0, 8));
            }

            // ---- Back of card ----
            PDPage back = new PDPage(cardSize);
            doc.addPage(back);

            try (PDPageContentStream cs = new PDPageContentStream(doc, back)) {
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                drawText(cs, fontRegular, 6, 10, CARD_HEIGHT - 15,
                        "This card is property of the Ministry of Health and Child Care.");
                drawText(cs, fontRegular, 6, 10, CARD_HEIGHT - 27,
                        "If found, please return to the nearest health facility.");
                drawText(cs, fontRegular, 5, 10, 15,
                        "Powered by Impilo National Health Operating System");
            }

            return toByteArray(doc);
        }
    }

    /**
     * Renders a client health smart card PDF.
     */
    public byte[] renderClientCard(PrintJobEntity job) throws IOException {
        Map<String, Object> data = job.getPayload();
        String qrAssertion = qrAssertionService.generateSignedAssertion(
                job.getSubjectType(), job.getSubjectId(), job.getJobId().toString());
        job.setQrAssertion(qrAssertion);

        PDRectangle cardSize = new PDRectangle(CARD_WIDTH, CARD_HEIGHT);

        try (PDDocument doc = new PDDocument()) {
            // ---- Front of card ----
            PDPage front = new PDPage(cardSize);
            doc.addPage(front);

            try (PDPageContentStream cs = new PDPageContentStream(doc, front)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontMono = new PDType1Font(Standard14Fonts.FontName.COURIER);

                // Header block
                drawText(cs, fontBold, 8, 10, CARD_HEIGHT - 15, "REPUBLIC OF ZIMBABWE");
                drawText(cs, fontBold, 7, 10, CARD_HEIGHT - 25, "NATIONAL HEALTH SMART CARD");

                // Data fields
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 45, "Name:",
                        getStringValue(data, "fullName"));
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 57, "CPID:",
                        getStringValue(data, "clientId"));
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 69, "DOB:",
                        getStringValue(data, "dateOfBirth"));
                drawLabelValue(cs, fontRegular, 6, 10, CARD_HEIGHT - 81, "Gender:",
                        getStringValue(data, "gender"));

                // Photo placeholder
                cs.addRect(CARD_WIDTH - 55, CARD_HEIGHT - 60, 45, 50);
                cs.stroke();
                drawText(cs, fontRegular, 5, CARD_WIDTH - 48, CARD_HEIGHT - 38, "PHOTO");

                // QR code
                byte[] qrImage = generateQrCodeImage(qrAssertion);
                PDImageXObject qrPdImage = PDImageXObject.createFromByteArray(doc, qrImage, "qr.png");
                cs.drawImage(qrPdImage, 10, 8, 40, 40);

                // Footer
                drawText(cs, fontMono, 4, 55, 15, "Card #" + job.getJobId().toString().substring(0, 8));
            }

            // ---- Back of card ----
            PDPage back = new PDPage(cardSize);
            doc.addPage(back);

            try (PDPageContentStream cs = new PDPageContentStream(doc, back)) {
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                drawText(cs, fontRegular, 6, 10, CARD_HEIGHT - 15,
                        "This card is property of the Ministry of Health and Child Care.");
                drawText(cs, fontRegular, 6, 10, CARD_HEIGHT - 27,
                        "If found, please return to the nearest health facility.");
                drawText(cs, fontRegular, 5, 10, 15,
                        "Powered by Impilo National Health Operating System");
            }

            return toByteArray(doc);
        }
    }

    /**
     * Renders a share slip PDF (A5 format) with QR code and sharing instructions.
     */
    public byte[] renderShareSlip(PrintJobEntity job) throws IOException {
        Map<String, Object> data = job.getPayload();
        String qrAssertion = qrAssertionService.generateSignedAssertion(
                job.getSubjectType(), job.getSubjectId(), job.getJobId().toString());
        job.setQrAssertion(qrAssertion);

        PDRectangle a5Size = new PDRectangle(A5_WIDTH, A5_HEIGHT);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(a5Size);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Header
                drawText(cs, fontBold, 14, 20, A5_HEIGHT - 40, "HEALTH RECORD SHARE SLIP");
                drawText(cs, fontRegular, 10, 20, A5_HEIGHT - 60, "Ministry of Health and Child Care");

                // Divider line
                cs.moveTo(20, A5_HEIGHT - 70);
                cs.lineTo(A5_WIDTH - 20, A5_HEIGHT - 70);
                cs.stroke();

                // Data fields
                float y = A5_HEIGHT - 100;
                drawLabelValue(cs, fontRegular, 10, 20, y, "Client Name:",
                        getStringValue(data, "clientName"));
                y -= 20;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Client ID:",
                        getStringValue(data, "clientId"));
                y -= 20;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Share Scope:",
                        getStringValue(data, "shareScope"));
                y -= 20;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Valid Until:",
                        getStringValue(data, "validUntil"));

                // QR code (centered, larger for slip)
                byte[] qrImage = generateQrCodeImage(qrAssertion);
                PDImageXObject qrPdImage = PDImageXObject.createFromByteArray(doc, qrImage, "qr.png");
                float qrSize = 120;
                float qrX = (A5_WIDTH - qrSize) / 2;
                cs.drawImage(qrPdImage, qrX, A5_HEIGHT - 320, qrSize, qrSize);

                // Instructions
                drawText(cs, fontRegular, 8, 20, A5_HEIGHT - 350,
                        "Scan this QR code at any participating health facility");
                drawText(cs, fontRegular, 8, 20, A5_HEIGHT - 365,
                        "to share your health records securely.");

                // Footer
                drawText(cs, fontRegular, 7, 20, 30,
                        "Generated by Impilo National Health Operating System");
            }

            return toByteArray(doc);
        }
    }

    /**
     * Renders an emergency capsule PDF (A5 format) with critical health information and QR.
     */
    public byte[] renderEmergencyCapsule(PrintJobEntity job) throws IOException {
        Map<String, Object> data = job.getPayload();
        String qrAssertion = qrAssertionService.generateSignedAssertion(
                job.getSubjectType(), job.getSubjectId(), job.getJobId().toString());
        job.setQrAssertion(qrAssertion);

        PDRectangle a5Size = new PDRectangle(A5_WIDTH, A5_HEIGHT);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(a5Size);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Header with warning
                drawText(cs, fontBold, 14, 20, A5_HEIGHT - 40, "EMERGENCY HEALTH INFORMATION");
                drawText(cs, fontBold, 10, 20, A5_HEIGHT - 60, "FOR EMERGENCY USE ONLY");

                // Divider line
                cs.moveTo(20, A5_HEIGHT - 70);
                cs.lineTo(A5_WIDTH - 20, A5_HEIGHT - 70);
                cs.stroke();

                // Data fields
                float y = A5_HEIGHT - 100;
                drawLabelValue(cs, fontRegular, 10, 20, y, "Client Name:",
                        getStringValue(data, "clientName"));
                y -= 22;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Date of Birth:",
                        getStringValue(data, "dateOfBirth"));
                y -= 22;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Blood Type:",
                        getStringValue(data, "bloodType"));
                y -= 22;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Known Allergies:",
                        getStringValue(data, "allergies"));
                y -= 22;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Chronic Conditions:",
                        getStringValue(data, "conditions"));
                y -= 22;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Current Medications:",
                        getStringValue(data, "medications"));
                y -= 22;
                drawLabelValue(cs, fontRegular, 9, 20, y, "Emergency Contact:",
                        getStringValue(data, "emergencyContact"));

                // QR code (centered)
                byte[] qrImage = generateQrCodeImage(qrAssertion);
                PDImageXObject qrPdImage = PDImageXObject.createFromByteArray(doc, qrImage, "qr.png");
                float qrSize = 120;
                float qrX = (A5_WIDTH - qrSize) / 2;
                cs.drawImage(qrPdImage, qrX, 80, qrSize, qrSize);

                // Footer
                drawText(cs, fontRegular, 7, 20, 30,
                        "Generated by Impilo National Health Operating System");
            }

            return toByteArray(doc);
        }
    }

    /**
     * Routes rendering to the appropriate method based on job type.
     */
    public byte[] render(PrintJobEntity job) throws IOException {
        return switch (job.getJobType()) {
            case PROVIDER_CARD -> renderProviderCard(job);
            case CLIENT_CARD -> renderClientCard(job);
            case SHARE_SLIP -> renderShareSlip(job);
            case EMERGENCY_CAPSULE -> renderEmergencyCapsule(job);
            case FACILITY_BADGE -> renderProviderCard(job); // Facility badge uses provider card layout as fallback
        };
    }

    // --- Helper methods ---

    private void drawText(PDPageContentStream cs, PDType1Font font, float fontSize,
                          float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(text != null ? text : "");
        cs.endText();
    }

    private void drawLabelValue(PDPageContentStream cs, PDType1Font font, float fontSize,
                                float x, float y, String label, String value) throws IOException {
        PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        cs.beginText();
        cs.setFont(fontBold, fontSize);
        cs.newLineAtOffset(x, y);
        cs.showText(label + " ");
        cs.setFont(font, fontSize);
        cs.showText(value != null ? value : "N/A");
        cs.endText();
    }

    private byte[] generateQrCodeImage(String content) throws IOException {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = Map.of(
                    EncodeHintType.CHARACTER_SET, "UTF-8",
                    EncodeHintType.MARGIN, 1
            );
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX, hints);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();
        } catch (WriterException e) {
            throw new IOException("Failed to generate QR code", e);
        }
    }

    private String getStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        return value != null ? value.toString() : "";
    }

    private byte[] toByteArray(PDDocument doc) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.save(baos);
        return baos.toByteArray();
    }
}
