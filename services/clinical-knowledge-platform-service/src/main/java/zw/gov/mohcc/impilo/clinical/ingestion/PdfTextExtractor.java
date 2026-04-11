package zw.gov.mohcc.impilo.clinical.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Page-wise text extraction from PDF (no OCR — text layer only).
 */
@Component
public class PdfTextExtractor {

    public List<PageText> extractPages(Path pdfFile) throws IOException {
        List<PageText> pages = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(pdfFile.toFile())) {
            int n = doc.getNumberOfPages();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int p = 1; p <= n; p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                pages.add(new PageText(p, text == null ? "" : text));
            }
        }
        return pages;
    }

    public record PageText(int pageNumber, String text) {
    }
}
