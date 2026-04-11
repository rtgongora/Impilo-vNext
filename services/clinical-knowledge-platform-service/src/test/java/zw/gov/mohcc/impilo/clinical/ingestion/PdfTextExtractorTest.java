package zw.gov.mohcc.impilo.clinical.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTextExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsTextPerPage() throws Exception {
        Path pdf = tempDir.resolve("sample.pdf");
        try (PDDocument doc = new PDDocument()) {
            PDPage p1 = new PDPage();
            doc.addPage(p1);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p1)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Neonatal sepsis guidance");
                cs.endText();
            }
            PDPage p2 = new PDPage();
            doc.addPage(p2);
            try (PDPageContentStream cs = new PDPageContentStream(doc, p2)) {
                cs.beginText();
                cs.setFont(font, 12);
                cs.newLineAtOffset(50, 700);
                cs.showText("Page two content");
                cs.endText();
            }
            doc.save(pdf.toFile());
        }

        PdfTextExtractor extractor = new PdfTextExtractor();
        List<PdfTextExtractor.PageText> pages = extractor.extractPages(pdf);
        assertThat(pages).hasSize(2);
        assertThat(pages.get(0).text()).containsIgnoringCase("Neonatal");
        assertThat(pages.get(1).text()).containsIgnoringCase("Page two");
    }
}
