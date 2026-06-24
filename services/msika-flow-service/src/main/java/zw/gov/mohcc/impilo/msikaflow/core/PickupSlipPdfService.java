package zw.gov.mohcc.impilo.msikaflow.core;

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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Order-native pickup-slip PDF generator (G034 — replaces the JSON stub that the
 * {@code /v1/orders/{id}/pickup/slip.pdf} endpoint used to return).
 *
 * <p>Renders a printable slip for a specific order's active pickup token: order ref,
 * token id, expiry, and — when the caller supplies the raw token it already holds from
 * the issue response — a real scannable QR of that token plus the token text for manual
 * entry. The raw token is a client-held secret: it is never persisted or logged here;
 * the controller validates it against the stored hash before passing it in.
 *
 * <p>This is presentation only. The pickup-token lifecycle remains owned by
 * {@link PickupTokenService}; VITO's generic delegated-share slip
 * ({@code /v1/slips/pickup.pdf}) is a separate, parameter-driven renderer.
 *
 * <p>Uses Apache PDFBox + ZXing (both Apache 2.0), mirroring the established slip
 * pattern in share-slip-service and vito-service.
 */
@Service
public class PickupSlipPdfService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX");

    /** Encode the pickup token as a real scannable QR PNG. */
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

    /**
     * @param orderId   the order this pickup slip is for
     * @param tokenId   the active pickup token's id (non-secret)
     * @param expiresAt token expiry
     * @param status    token status label
     * @param rawToken  the client-held raw token for the scannable QR, or {@code null} to
     *                  render a slip without the secret (e.g. a reprint without the token)
     * @return PDF bytes
     */
    public byte[] generatePickupSlipPdf(String orderId, String tokenId, OffsetDateTime expiresAt,
                                        String status, String rawToken) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                PDType1Font fontCourier = new PDType1Font(Standard14Fonts.FontName.COURIER);
                float y = page.getMediaBox().getHeight() - 40;
                float margin = 30;

                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("IMPILO MEDICATION PICKUP SLIP");
                cs.endText();
                y -= 28;

                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("Present this slip at the dispensary to collect the order below.");
                cs.endText();
                y -= 25;

                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Order: " + truncate(orderId, 40));
                cs.endText();
                y -= 18;

                cs.beginText();
                cs.setFont(fontRegular, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Token ID: " + truncate(tokenId, 36) + "  |  Status: " + status);
                cs.endText();
                y -= 18;

                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Expires: " + (expiresAt != null ? expiresAt.format(DATE_FORMAT) : "n/a"));
                cs.endText();
                y -= 24;

                if (rawToken != null && !rawToken.isBlank()) {
                    cs.beginText();
                    cs.setFont(fontBold, 10);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Pickup Token (scan at dispensary):");
                    cs.endText();
                    y -= 14;

                    PDImageXObject qrImage =
                            PDImageXObject.createFromByteArray(doc, qrPng(rawToken, 600), "pickup-token-qr");
                    float qrSize = 120;
                    cs.drawImage(qrImage, margin, y - qrSize, qrSize, qrSize);
                    y -= (qrSize + 8);

                    int lineLen = 64;
                    for (int i = 0; i < rawToken.length(); i += lineLen) {
                        cs.beginText();
                        cs.setFont(fontCourier, 7);
                        cs.newLineAtOffset(margin, y);
                        cs.showText(rawToken.substring(i, Math.min(i + lineLen, rawToken.length())));
                        cs.endText();
                        y -= 10;
                    }
                    y -= 14;
                } else {
                    cs.beginText();
                    cs.setFont(fontRegular, 9);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Token not included on this copy — present the OTP/token from the Impilo app.");
                    cs.endText();
                    y -= 24;
                }

                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("INSTRUCTIONS:");
                cs.endText();
                y -= 14;

                String[] instructions = {
                    "1. Bring this slip and the One-Time Password (OTP) provided separately.",
                    "2. At the dispensary, present the slip; staff will scan the token or enter it.",
                    "3. Enter the OTP to authorise the collection.",
                    "4. This slip expires on the date shown above.",
                    "5. Keep this slip and OTP confidential.",
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
                cs.beginText();
                cs.setFont(fontRegular, 7);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated by Impilo National Health Operating System -- Zimbabwe");
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
