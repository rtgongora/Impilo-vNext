package zw.gov.mohcc.impilo.cardprint.generator;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Generates print-ready card payloads (PDF format).
 * Templates: STANDARD (CR-80 card size), MINI (half card).
 */
@Component
public class CardPayloadGenerator {

    // CR-80 card dimensions in points (85.6mm x 53.98mm)
    private static final float CARD_WIDTH = 242.65f;
    private static final float CARD_HEIGHT = 153.01f;

    public byte[] generate(long cardId, String template, String did) throws IOException {
        PDRectangle cardSize = new PDRectangle(CARD_WIDTH, CARD_HEIGHT);

        try (PDDocument doc = new PDDocument()) {
            // Front of card
            PDPage front = new PDPage(cardSize);
            doc.addPage(front);

            try (PDPageContentStream cs = new PDPageContentStream(doc, front)) {
                PDType1Font fontBold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                // Header
                cs.beginText();
                cs.setFont(fontBold, 8);
                cs.newLineAtOffset(10, CARD_HEIGHT - 15);
                cs.showText("REPUBLIC OF ZIMBABWE");
                cs.endText();

                cs.beginText();
                cs.setFont(fontBold, 7);
                cs.newLineAtOffset(10, CARD_HEIGHT - 25);
                cs.showText("NATIONAL HEALTH SMART CARD");
                cs.endText();

                // DID
                cs.beginText();
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.COURIER), 5);
                cs.newLineAtOffset(10, CARD_HEIGHT - 40);
                String shortDid = did.length() > 40 ? did.substring(0, 40) + "..." : did;
                cs.showText("DID: " + shortDid);
                cs.endText();

                // Card ID
                cs.beginText();
                cs.setFont(fontRegular, 6);
                cs.newLineAtOffset(10, 15);
                cs.showText("Card #" + cardId + " | Template: " + template);
                cs.endText();

                // Photo placeholder area
                cs.addRect(CARD_WIDTH - 55, CARD_HEIGHT - 60, 45, 50);
                cs.stroke();
                cs.beginText();
                cs.setFont(fontRegular, 5);
                cs.newLineAtOffset(CARD_WIDTH - 48, CARD_HEIGHT - 38);
                cs.showText("PHOTO");
                cs.endText();

                // QR placeholder area
                cs.addRect(10, 30, 40, 40);
                cs.stroke();
                cs.beginText();
                cs.setFont(fontRegular, 5);
                cs.newLineAtOffset(20, 48);
                cs.showText("QR");
                cs.endText();
            }

            // Back of card
            PDPage back = new PDPage(cardSize);
            doc.addPage(back);

            try (PDPageContentStream cs = new PDPageContentStream(doc, back)) {
                PDType1Font fontRegular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

                cs.beginText();
                cs.setFont(fontRegular, 6);
                cs.newLineAtOffset(10, CARD_HEIGHT - 15);
                cs.showText("This card is property of the Ministry of Health and Child Care.");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 6);
                cs.newLineAtOffset(10, CARD_HEIGHT - 27);
                cs.showText("If found, please return to the nearest health facility.");
                cs.endText();

                cs.beginText();
                cs.setFont(fontRegular, 5);
                cs.newLineAtOffset(10, 15);
                cs.showText("Powered by Impilo National Health Operating System");
                cs.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }
}
