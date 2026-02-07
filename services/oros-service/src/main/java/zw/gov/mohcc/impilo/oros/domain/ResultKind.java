package zw.gov.mohcc.impilo.oros.domain;

/**
 * Classification of order results by domain.
 *
 * <ul>
 *   <li>{@code LAB} — laboratory result (haematology, chemistry, microbiology, etc.)</li>
 *   <li>{@code IMAGING} — radiology/imaging report with optional image references</li>
 *   <li>{@code PHARMACY} — dispensing confirmation or medication administration record</li>
 *   <li>{@code DOCUMENT} — general clinical document (procedure note, pathology report, etc.)</li>
 * </ul>
 */
public enum ResultKind {
    LAB,
    IMAGING,
    PHARMACY,
    DOCUMENT
}
