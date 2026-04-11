package zw.gov.mohcc.impilo.clinical.ingestion;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits long page text into bounded chunks for storage and retrieval.
 */
public final class TextChunker {

    private TextChunker() {
    }

    public static List<String> chunk(String text, int maxChars, int overlapChars) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String t = text.strip().replaceAll("\\R+", "\n");
        if (t.length() <= maxChars) {
            return List.of(t);
        }
        int overlap = Math.min(Math.max(overlapChars, 0), maxChars / 2);
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < t.length()) {
            int end = Math.min(i + maxChars, t.length());
            out.add(t.substring(i, end));
            if (end >= t.length()) {
                break;
            }
            i = Math.max(end - overlap, i + 1);
        }
        return out;
    }
}
