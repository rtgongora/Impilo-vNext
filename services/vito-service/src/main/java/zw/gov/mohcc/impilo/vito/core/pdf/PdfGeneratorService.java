package zw.gov.mohcc.impilo.vito.core.pdf;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.vito.core.qr.QrSigningService;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * PDF generator for patient-facing documents.
 *
 * Uses Apache PDFBox (Apache 2.0 license — safe for national health systems).
 * NOTE: PDFBox dependency must be added to pom.xml separately:
 *   <dependency>
 *       <groupId>org.apache.pdfbox</groupId>
 *       <artifactId>pdfbox</artifactId>
 *       <version>3.0.3</version>
 *   </dependency>
 *
 * 1) Emergency Capsule: minimal emergency health facts + signed QR + instructions
 * 2) Pickup Slip: delegated pickup QR + OTP instructions + expiry + facility info
 */
@Service
public class PdfGeneratorService {

    private final QrSigningService qrSigningService;

    public PdfGeneratorService(QrSigningService qrSigningService) {
        this.qrSigningService = qrSigningService;
    }

    /**
     * Generate Emergency Capsule PDF.
     * Contains: patient name, emergency flags, signed QR, instructions, expiry.
     * NO detailed PII dump — only what emergency responders need.
     */
    public byte[] generateEmergencyCapsule(String tenantId, String healthIdPointer,
                                             String patientName, boolean hasAllergies,
                                             boolean hasMajorConditions, String payerIndicator,
                                             long qrTtlSeconds) throws IOException {
        String qrToken = qrSigningService.sign(tenantId, "EMERGENCY", healthIdPointer, qrTtlSeconds);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float y = page.getMediaBox().getHeight() - 40;
                float margin = 30;

                // Title
                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("IMPILO EMERGENCY CAPSULE");
                cs.endText();
                y -= 30;

                // Warning box
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("This document contains minimal emergency health information.");
                cs.endText();
                y -= 14;
                cs.beginText();
                cs.setFont(fontRegular, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("Scan the QR code below with an authorized device to access details.");
                cs.endText();
                y -= 25;

                // Patient name
                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Patient: " + truncate(patientName, 40));
                cs.endText();
                y -= 22;

                // Emergency flags
                cs.beginText();
                cs.setFont(fontRegular, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Known Allergies: " + (hasAllergies ? "YES — check system" : "None on record"));
                cs.endText();
                y -= 18;

                cs.beginText();
                cs.setFont(fontRegular, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Major Conditions: " + (hasMajorConditions ? "YES — check system" : "None on record"));
                cs.endText();
                y -= 18;

                if (payerIndicator != null && !payerIndicator.isBlank()) {
                    cs.beginText();
                    cs.setFont(fontRegular, 11);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Payer: " + truncate(payerIndicator, 50));
                    cs.endText();
                    y -= 18;
                }

                y -= 15;

                // QR token (as text — actual QR image generation would use a QR library)
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Signed QR Token (scan with Impilo app):");
                cs.endText();
                y -= 14;

                // Break QR token into lines
                int lineLen = 70;
                for (int i = 0; i < qrToken.length(); i += lineLen) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 6);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(qrToken.substring(i, Math.min(i + lineLen, qrToken.length())));
                    cs.endText();
                    y -= 10;
                }

                y -= 15;

                // Instructions
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("INSTRUCTIONS FOR EMERGENCY RESPONDERS:");
                cs.endText();
                y -= 14;

                String[] instructions = {
                    "1. Scan the QR code above using an Impilo-authorized device.",
                    "2. The system will verify the token signature and expiry.",
                    "3. If valid, you will see the patient's emergency health summary.",
                    "4. This token expires — check the date below.",
                    "5. If expired, contact the nearest Impilo-registered facility."
                };
                for (String instr : instructions) {
                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(instr);
                    cs.endText();
                    y -= 12;
                }

                y -= 10;
                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated by Impilo National Health Operating System — Zimbabwe");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    /**
     * Generate Pickup Slip PDF.
     * Contains: QR token for delegate, OTP instructions, facility info, expiry.
     */
    public byte[] generatePickupSlip(String tenantId, String pickupToken, String otp,
                                       String delegateName, String facilityName,
                                       String expiresAt) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A5);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float y = page.getMediaBox().getHeight() - 40;
                float margin = 30;

                // Title
                cs.beginText();
                cs.setFont(fontBold, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("IMPILO PICKUP SLIP");
                cs.endText();
                y -= 30;

                // Delegate info
                cs.beginText();
                cs.setFont(fontBold, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Delegate: " + truncate(delegateName, 40));
                cs.endText();
                y -= 22;

                cs.beginText();
                cs.setFont(fontRegular, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Facility: " + truncate(facilityName, 50));
                cs.endText();
                y -= 18;

                cs.beginText();
                cs.setFont(fontRegular, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Expires: " + expiresAt);
                cs.endText();
                y -= 25;

                // OTP
                cs.beginText();
                cs.setFont(fontBold, 14);
                cs.newLineAtOffset(margin, y);
                cs.showText("One-Time Password: " + otp);
                cs.endText();
                y -= 25;

                // QR token
                cs.beginText();
                cs.setFont(fontBold, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("Pickup Token (present at facility):");
                cs.endText();
                y -= 14;

                int lineLen = 70;
                for (int i = 0; i < pickupToken.length(); i += lineLen) {
                    cs.beginText();
                    cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 7);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(pickupToken.substring(i, Math.min(i + lineLen, pickupToken.length())));
                    cs.endText();
                    y -= 10;
                }

                y -= 20;

                // Instructions
                cs.beginText();
                cs.setFont(fontBold, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("INSTRUCTIONS:");
                cs.endText();
                y -= 14;

                String[] instructions = {
                    "1. Take this slip to the facility listed above.",
                    "2. Present this slip and your national ID to the clerk.",
                    "3. The clerk will scan the QR code and ask for the OTP above.",
                    "4. You have a maximum of 3 attempts to enter the correct OTP.",
                    "5. After successful verification, you will receive the health card.",
                    "6. This slip expires on the date shown above."
                };
                for (String instr : instructions) {
                    cs.beginText();
                    cs.setFont(fontRegular, 8);
                    cs.newLineAtOffset(margin, y);
                    cs.showText(instr);
                    cs.endText();
                    y -= 12;
                }

                y -= 10;
                cs.beginText();
                cs.setFont(fontRegular, 8);
                cs.newLineAtOffset(margin, y);
                cs.showText("Generated by Impilo National Health Operating System — Zimbabwe");
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
