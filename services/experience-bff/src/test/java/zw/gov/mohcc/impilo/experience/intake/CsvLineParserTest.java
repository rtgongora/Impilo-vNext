package zw.gov.mohcc.impilo.experience.intake;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CsvLineParserTest {

    @Test
    void parsesHeaderAndRows() {
        String csv = "name,facilityCode,province\n" + "Clinic A,ZW-IMP-001,Harare\n";
        List<String[]> rows = CsvLineParser.parseRows(csv);
        assertThat(rows).hasSize(2);
        Map<String, Integer> idx = CsvLineParser.headerIndex(rows.get(0));
        assertThat(idx.get("name")).isZero();
        assertThat(CsvLineParser.cell(rows.get(1), idx, "name")).isEqualTo("Clinic A");
        assertThat(CsvLineParser.cell(rows.get(1), idx, "facilityCode", "code")).isEqualTo("ZW-IMP-001");
    }

    @Test
    void parsesQuotedFieldContainingCommas() {
        String csv = "name,facilityCode\n" + "\"Clinic, North\",ZW-IMP-002\n";
        List<String[]> rows = CsvLineParser.parseRows(csv);
        assertThat(rows).hasSize(2);
        Map<String, Integer> idx = CsvLineParser.headerIndex(rows.get(0));
        assertThat(CsvLineParser.cell(rows.get(1), idx, "name")).isEqualTo("Clinic, North");
        assertThat(CsvLineParser.cell(rows.get(1), idx, "facilityCode", "code")).isEqualTo("ZW-IMP-002");
    }
}
