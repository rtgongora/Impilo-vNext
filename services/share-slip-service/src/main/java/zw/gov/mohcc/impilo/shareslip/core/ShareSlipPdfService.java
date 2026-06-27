package zw.gov.mohcc.impilo.shareslip.core;

import com.google.zxing.BarcodeFormat;
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
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.shareslip.persistence.entity.ShareLinkEntity;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

/**
 * PDF generator for share slip documents.
 *
 * Generates a physical share slip containing:
 * - QR code token (as text, for scanning with Impilo app)
 * - Instructions for the delegate on how to claim
 * - Expiry information
 * - Subject name (minimal safe information)
 *
 * Uses Apache PDFBox (Apache 2.0 license — safe for national health systems).
 */
@Service
public class ShareSlipPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");

    /**
     * Generate a share slip PDF for the given share link.
     *
     * @param entity the share link entity
     * @return PDF bytes
     * @throws IOException if PDF generation fails
     */
    /** Encode the share token as a real scannable QR PNG (G047 — was a text placeholder). */
    private static byte[] qrPng(String content, int sizePx) throws IOException {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IOException("QR generation failed", e);
        }
    }

    public byte[] generateShareSlipPdf(ShareLinkEntity entity) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontCourier = new PDType1Font(Standard14Fonts.FontName.COURIER);
                float y = page.getMediaBox().getHeight() - 40;
                float margin = 30;

                // Title
                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("IMPILO SHARE SLIP");
                cs.endText();
                y -= 30;

                // Subtitle
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("This slip authorizes the bearer to claim shared documents.");
                cs.endText();
                y -= 14;
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("Present this slip and the OTP at an authorized facility or scan with Impilo app.");
                cs.endText();
                y -= 25;

                // Subject info
                if (entity.getSubjectName() != null && !entity.getSubjectName().isBlank()) {
                    cs.beginText();
                    cs.setFont(fontBold, 12);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Subject: " + truncate(entity.getSubjectName(), 40));
                    cs.endText();
                    y -= 22;
                }

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Type: " + entity.getSubjectType() + "  |  ID: " + truncate(entity.getSubjectId(), 30));
                cs.endText();
                y -= 18;

                // Purpose
                if (entity.getPurpose() != null && !entity.getPurpose().isBlank()) {
                    cs.beginText();
                    cs.setFont(fontRegular, 10);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Purpose: " + truncate(entity.getPurpose(), 50));
                    cs.endText();
                    y -= 18;
                }

                // Expiry
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Expires: " + entity.getExpiresAt().format(DATE_FORMAT));
                cs.endText();
                y -= 22;

                // Verification method
                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Verification: " + entity.getVerificationMethod()
                        + "  |  Max Claims: " + entity.getMaxClaims());
                cs.endText();
                y -= 22;

                // Document count
                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y);
                int docCount = entity.getDocumentIds() != null ? entity.getDocumentIds().size() : 0;
                cs.showText("Documents: " + docCount + " document(s) attached");
                cs.endText();
                y -= 25;

                // Share token — real scannable QR (was a text placeholder)
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Share Token (scan with Impilo app):");
                cs.endText();
                y -= 14;

                String token = entity.getShareToken();

                // Render an actual QR of the token and embed it.
                PDImageXObject qrImage = PDImageXObject.createFromByteArray(doc, qrPng(token, 600), "share-token-qr");
                float qrSize = 120;
                cs.drawImage(qrImage, margin, y - qrSize, qrSize, qrSize);
                y -= (qrSize + 8);

                // Keep the raw token below for manual entry if scanning is unavailable.
                int lineLen = 64;
                for (int i = 0; i < token.length(); i += lineLen) {
                    cs.beginText();
                    cs.setFont(fontCourier, 7);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(token.substring(i, Math.min(i + lineLen, token.length())));
                    cs.endText();
                    y -= 10;
                }

                y -= 20;

                // Instructions
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("INSTRUCTIONS FOR CLAIMING:");
                cs.endText();
                y -= 14;

                String[] instructions = {
                    "1. Open the Impilo app or visit the claim portal.",
                    "2. Scan the share token above or enter it manually.",
                    "3. Enter the One-Time Password (OTP) provided separately.",
                    "4. You have a maximum of 3 attempts to enter the correct OTP.",
                    "5. On successful verification, the shared documents will be available.",
                    "6. This slip expires on the date shown above.",
                    "7. Keep this slip confidential. Do not share the OTP with anyone."
                };
                for (String instr : instructions) {
                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(instr);
                    cs.endText();
                    y -= 12;
                }

                y -= 15;

                // Footer
                cs.beginText();
                cs.setFont(fontRegular, 7);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated by Impilo National Health Operating System -- Zimbabwe");
                cs.endText();
                y -= 10;

                cs.beginText();
                cs.setFont(fontRegular, 7);
                cs.newLineAtOffset(margin, y);
                cs.showText("Link ID: " + entity.getLinkId());
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
