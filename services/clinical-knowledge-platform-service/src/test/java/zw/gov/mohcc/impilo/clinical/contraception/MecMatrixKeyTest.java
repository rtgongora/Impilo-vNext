package zw.gov.mohcc.impilo.clinical.contraception;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The cell index key.
 *
 * <p>Trivial-looking and worth pinning: a concatenated key with no separator makes
 * {@code AB + C} and {@code A + BC} the same entry, so the eligibility answer for one method would
 * be served for another. It would not throw, and it would not be visible in any test that used
 * comfortably-different codes.
 */
class MecMatrixKeyTest {

    @Test
    void adjacentCodesDoNotCollideInTheCellIndex() {
        MecMatrix matrix = MecMatrix.of("t", "v", "ENGINEERING_SEED", "TEST", "p", "c",
                List.of(new MecMatrix.Method("C", "Method C", "X"),
                        new MecMatrix.Method("BC", "Method BC", "X")),
                List.of(), List.of(),
                List.of(new MecMatrix.Cell("AB", "C", 4, 4, null, List.of()),
                        new MecMatrix.Cell("A", "BC", 1, 1, null, List.of())));

        assertEquals(4, matrix.cell("AB", "C").initiation());
        assertEquals(1, matrix.cell("A", "BC").initiation(),
                "AB+C and A+BC must be different cells");
    }

    @Test
    void anAbsentPairIsNullRatherThanADefault() {
        MecMatrix matrix = MecMatrix.of("t", "v", "ENGINEERING_SEED", "TEST", "p", "c",
                List.of(new MecMatrix.Method("POP", "Progestogen-only pill", "POP")),
                List.of(), List.of(), List.of());
        assertNull(matrix.cell("ANYTHING", "POP"));
    }
}
