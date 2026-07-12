package zw.gov.mohcc.impilo.tuso.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The demand signal is a supply-side aggregate: no facility/application
 * identifiers may ever appear, and small cells are suppressed in-query.
 */
@ExtendWith(MockitoExtension.class)
class RegulatoryDemandSignalServiceTest {

    @Mock private JdbcTemplate jdbcTemplate;

    @Test
    void responseShapeCarriesNoIdentifiers() {
        // Structural privacy assertion: no record component may reference a
        // facility/application/practitioner identifier or name.
        for (Class<?> record : List.of(
                RegulatoryDemandSignalService.DemandSignalResponse.class,
                RegulatoryDemandSignalService.CodeProvinceCount.class,
                RegulatoryDemandSignalService.TypeCount.class)) {
            for (RecordComponent component : record.getRecordComponents()) {
                String name = component.getName().toLowerCase(Locale.ROOT);
                assertThat(name)
                        .as(record.getSimpleName() + "." + component.getName())
                        .doesNotContain("facilityid", "applicationid", "facilityname",
                                "practitioner", "certificateid", "healthid");
            }
        }
    }

    @Test
    void aggregatesUseSmallCellSuppressionAndNonIdentifyingDimensionsOnly() {
        java.util.List<String> issuedSql = new java.util.ArrayList<>();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenAnswer(inv -> {
                    issuedSql.add(inv.getArgument(0));
                    return List.of();
                });
        RegulatoryDemandSignalService service = new RegulatoryDemandSignalService(jdbcTemplate);

        var response = service.demandSignal();

        assertThat(response.smallCellFloor()).isEqualTo(RegulatoryDemandSignalService.SMALL_CELL_FLOOR);
        assertThat(response.openFindings()).isEmpty();
        assertThat(response.openComplianceActions()).isEmpty();
        assertThat(response.openApplicationsByFacilityType()).isEmpty();

        assertThat(issuedSql).hasSize(3);
        assertThat(String.join(" ", issuedSql))
                .contains("tuso.inspection_finding f")
                .contains("tuso.compliance_action a")
                .contains("tuso.facility_application app");
        for (String sql : issuedSql) {
            assertThat(sql).as("small-cell floor").contains("HAVING count(*) >= ?");
            assertThat(sql.toLowerCase(Locale.ROOT))
                    .as("no identifier projection")
                    .doesNotContain("f.finding_id,")
                    .doesNotContain("fac.id as")
                    .doesNotContain("fac.name")
                    .doesNotContain("app.application_id");
        }
    }
}
