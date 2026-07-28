package zw.gov.mohcc.impilo.procedures;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.procedures.api.dto.AnalyticsDtos.IndicatorSummary;
import zw.gov.mohcc.impilo.procedures.api.dto.AnalyticsDtos.IndicatorView;
import zw.gov.mohcc.impilo.procedures.core.AnalyticsIndicatorService;
import zw.gov.mohcc.impilo.procedures.core.ProceduresDomainException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * §26 — the pipeline analytics indicator catalogue. Read layer, engine-not-store: these tests
 * prove the service reads and summarises seeded governed content correctly; they do not (and must
 * not) assert any real number was computed — see {@code AnalyticsIndicatorService}'s own javadoc.
 *
 * <p>H2's {@code create-drop} schema does not enforce the Postgres CHECK constraints (the
 * gap-reason-required and executable-via-required rules); those are proven in
 * {@code procedures-analytics-journeys.sh} against real Postgres.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class AnalyticsIndicatorServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("00000000-0000-4000-8000-000000000002");

    @Autowired private AnalyticsIndicatorService service;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.update("DELETE FROM procedures.analytics_indicator_definition");
    }

    private void indicator(UUID tenantId, String code, String status, String executableVia,
                            String gapReason, boolean delegated, int order) {
        jdbc.update("""
                INSERT INTO procedures.analytics_indicator_definition
                  (id, tenant_id, indicator_code, indicator_name, numerator_description,
                   computation_status, executable_via, gap_reason, owning_service,
                   delegated_out_of_scope, display_order)
                VALUES (?,?,?,?, 'numerator desc', ?, ?, ?, 'some-service', ?, ?)
                """, UUID.randomUUID(), tenantId, code, "Name of " + code, status,
                executableVia, gapReason, delegated, order);
    }

    @Test
    void catalogueIsReturnedOrderedByDisplayOrder() {
        indicator(TENANT, "B_CODE", "COMPUTED", "reporting-service:x", null, false, 20);
        indicator(TENANT, "A_CODE", "COMPUTED", "reporting-service:x", null, false, 10);

        IndicatorSummary summary = service.indicatorCatalogue(TENANT);

        assertThat(summary.indicators()).extracting(IndicatorView::indicatorCode)
                .containsExactly("A_CODE", "B_CODE");
    }

    @Test
    void catalogueFromAnotherTenantIsInvisible() {
        indicator(OTHER_TENANT, "A_CODE", "COMPUTED", "reporting-service:x", null, false, 10);

        assertThat(service.indicatorCatalogue(TENANT).indicators()).isEmpty();
    }

    @Test
    void summaryCountsEachComputationStatusSeparately() {
        indicator(TENANT, "COMPUTED_ONE", "COMPUTED", "reporting-service:x", null, false, 10);
        indicator(TENANT, "COMPUTED_TWO", "COMPUTED", "reporting-service:y", null, false, 20);
        indicator(TENANT, "PARTIAL_ONE", "PARTIAL", null, "raw signal exists, no consumer", false, 30);
        indicator(TENANT, "GAP_ONE", "NOT_YET_INSTRUMENTED", null, "nothing exists yet", false, 40);
        indicator(TENANT, "GAP_TWO", "NOT_YET_INSTRUMENTED", null, "nothing exists yet", true, 50);

        IndicatorSummary summary = service.indicatorCatalogue(TENANT);

        assertThat(summary.total()).isEqualTo(5);
        assertThat(summary.computed()).isEqualTo(2);
        assertThat(summary.partial()).isEqualTo(1);
        assertThat(summary.notYetInstrumented()).isEqualTo(2);
    }

    /** A delegated-out-of-scope indicator is still counted, but flagged distinctly (§26 vs the ADR). */
    @Test
    void aDelegatedIndicatorCarriesTheFlagThrough() {
        indicator(TENANT, "COST", "NOT_YET_INSTRUMENTED", null, "delegated to costing-engine-service", true, 10);

        IndicatorView view = service.indicator(TENANT, "COST");

        assertThat(view.delegatedOutOfScope()).isTrue();
        assertThat(view.gapReason()).isNotBlank();
    }

    /** A COMPUTED indicator points at where the real number lives, without recomputing it here. */
    @Test
    void aComputedIndicatorCarriesItsExecutionReferenceButNoGapReason() {
        indicator(TENANT, "PROCEDURE_VOLUME", "COMPUTED", "reporting-service:theatre-utilisation", null, false, 10);

        IndicatorView view = service.indicator(TENANT, "PROCEDURE_VOLUME");

        assertThat(view.executableVia()).isEqualTo("reporting-service:theatre-utilisation");
        assertThat(view.gapReason()).isNull();
    }

    @Test
    void anUnknownIndicatorCodeIsNotFound() {
        assertThatThrownBy(() -> service.indicator(TENANT, "NOT_REAL"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getStatus()).isEqualTo(404));
    }

    @Test
    void anUnknownIndicatorCodeCarriesTheExpectedErrorCode() {
        assertThatThrownBy(() -> service.indicator(TENANT, "NOT_REAL"))
                .isInstanceOf(ProceduresDomainException.class)
                .satisfies(e -> assertThat(((ProceduresDomainException) e).getCode())
                        .isEqualTo("ANALYTICS_INDICATOR_NOT_FOUND"));
    }
}
