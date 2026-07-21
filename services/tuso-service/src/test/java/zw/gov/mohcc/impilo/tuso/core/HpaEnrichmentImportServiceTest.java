package zw.gov.mohcc.impilo.tuso.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityEntity;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityIdentifierEntity;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityContactRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityIdentifierRepository;
import zw.gov.mohcc.impilo.tuso.persistence.repository.FacilityRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for the HPA importer's match-or-create CLASSIFICATION (dry-run):
 * a candidate already carrying our HPA identifier is idempotent; a candidate
 * with a single credible name match ENRICHES; a candidate with no match CREATES;
 * a blank-name candidate is QUARANTINED. Dry-run performs no canonical writes,
 * so the enrich/create side effects are not exercised here — only the decision.
 */
@ExtendWith(MockitoExtension.class)
class HpaEnrichmentImportServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock FacilityMatchService matchService;
    @Mock FacilityRepository facilityRepository;
    @Mock FacilityIdentifierRepository identifierRepository;
    @Mock FacilityContactRepository contactRepository;
    @Mock PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final UUID TENANT = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private HpaEnrichmentImportService service() {
        // TransactionTemplate runs each chunk's callback inline against this mock
        // manager (getTransaction returns a real status; commit is a no-op).
        lenient().when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        return new HpaEnrichmentImportService(
                jdbc, objectMapper, matchService, facilityRepository, identifierRepository,
                contactRepository, transactionManager);
    }

    private static String record(long id, String name) {
        return "{\"source_record_key\":\"HPA_CURRENT_2026-07-17:" + id + "\","
                + "\"current_hpa\":{\"hpa_institution_id\":" + id + ",\"institution_name_raw\":\"" + name + "\","
                + "\"registration_number_normalized\":\"R" + id + "\",\"province\":\"Harare\","
                + "\"site_resolution_status\":\"RESOLVE_AGAINST_LIVE_TUSO\"}}";
    }

    @Test
    void classifiesIdempotentEnrichCreateAndQuarantine() throws Exception {
        // Feed: 100 = idempotent (prior HPA id), 200 = credible match (enrich),
        // 300 = no match (create), 400 = blank name (quarantine).
        Path feed = Files.createTempFile("hpa-feed", ".jsonl");
        Files.write(feed, List.of(
                record(100, "Alpha Clinic"),
                record(200, "Beta Hospital"),
                record(300, "Gamma Pharmacy"),
                record(400, "")));

        // Generic no-op DB writes (dry-run stages + decisions).
        lenient().when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        // Batch id + no-op field-assertion existence checks.
        lenient().when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        lenient().when(facilityRepository.countByTenantId(TENANT)).thenReturn(0L);

        // 100 already carries our HPA identifier → idempotent.
        FacilityEntity existing = mock(FacilityEntity.class, RETURNS_DEEP_STUBS);
        when(existing.getId()).thenReturn(1L);
        FacilityIdentifierEntity prior = mock(FacilityIdentifierEntity.class, RETURNS_DEEP_STUBS);
        when(prior.getFacility()).thenReturn(existing);
        lenient().when(identifierRepository.findBySystemAndValue(eq("HPA_INSTITUTION_ID"), any()))
                .thenReturn(Optional.empty());
        when(identifierRepository.findBySystemAndValue("HPA_INSTITUTION_ID", "HPA-100"))
                .thenReturn(Optional.of(prior));

        // 200 has one credible, name-corroborated live match → enrich.
        FacilityEntity match = mock(FacilityEntity.class);
        when(match.getId()).thenReturn(2L);
        when(matchService.matchForRegistration(eq(TENANT), eq("Beta Hospital"), any(), any(), any()))
                .thenReturn(new FacilityMatchService.MatchResult(true,
                        List.of(new FacilityMatchService.MatchCandidate(match, "FACILITY_CODE", 1.0))));
        // 300 has no credible match → create.
        when(matchService.matchForRegistration(eq(TENANT), eq("Gamma Pharmacy"), any(), any(), any()))
                .thenReturn(FacilityMatchService.MatchResult.none());

        HpaEnrichmentImportService.HpaImportSummary summary = service().importFeed(
                new HpaEnrichmentImportService.HpaImportRequest(feed.toString(), true, TENANT, "chk"));

        assertThat(summary.candidatesTotal()).isEqualTo(4);
        assertThat(summary.unchangedIdempotent()).isEqualTo(1);      // 100
        assertThat(summary.enrichedExisting()).isEqualTo(1);         // 200
        assertThat(summary.createdEstablishment()).isEqualTo(1);     // 300
        assertThat(summary.quarantined()).isEqualTo(1);              // 400
        assertThat(summary.countsByOutcome().get("CONFIRMED_EXISTING_ENRICH")).isEqualTo(2); // idempotent + enrich
        assertThat(summary.countsByOutcome().get("NEW_REGULATED_ESTABLISHMENT")).isEqualTo(1);
        assertThat(summary.countsByOutcome().get("QUARANTINED_DATA_QUALITY")).isEqualTo(1);
    }

    @Test
    void previouslyReviewedRecordIsNotReCreatedOnReRun() throws Exception {
        // A record with no live match that was ALREADY routed to a steward in a prior
        // committed run must stay in review, not silently materialise as a new facility.
        Path feed = Files.createTempFile("hpa-feed-rerun", ".jsonl");
        Files.write(feed, List.of(record(300, "Gamma Pharmacy")));

        lenient().when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        lenient().when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(1L);
        lenient().when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        // The prior-review guard query reports a pending steward decision for this record.
        lenient().when(jdbc.queryForObject(contains("decision_outcome IN"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        lenient().when(facilityRepository.countByTenantId(TENANT)).thenReturn(0L);
        lenient().when(identifierRepository.findBySystemAndValue(eq("HPA_INSTITUTION_ID"), any()))
                .thenReturn(Optional.empty());
        when(matchService.matchForRegistration(eq(TENANT), eq("Gamma Pharmacy"), any(), any(), any()))
                .thenReturn(FacilityMatchService.MatchResult.none());

        HpaEnrichmentImportService.HpaImportSummary summary = service().importFeed(
                new HpaEnrichmentImportService.HpaImportRequest(feed.toString(), false, TENANT, "chk"));

        assertThat(summary.createdEstablishment()).isZero();          // NOT re-created
        assertThat(summary.routedToReview()).isEqualTo(1);            // stays in the steward queue
        assertThat(summary.countsByOutcome().get("POSSIBLE_EXISTING_REVIEW")).isEqualTo(1);
    }
}
