package zw.gov.mohcc.impilo.varapi.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderCouncilRegistrationRecordEntity;
import zw.gov.mohcc.impilo.varapi.persistence.entity.RegulatoryImportRowEntity;
import zw.gov.mohcc.impilo.varapi.persistence.repository.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RegulatoryBulkImportServiceTest {

    @Mock RegulatoryImportBatchRepository batchRepository;
    @Mock RegulatoryImportRowRepository rowRepository;
    @Mock ProviderCouncilRegistrationRecordRepository registrationRepository;
    @Mock CouncilRepository councilRepository;
    @Mock ProviderRepository providerRepository;

    private final UUID tenant = UUID.randomUUID();

    private RegulatoryBulkImportService service() {
        lenient().when(batchRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        return new RegulatoryBulkImportService(batchRepository, rowRepository, registrationRepository,
                councilRepository, providerRepository, new ObjectMapper());
    }

    @Test
    void stageClassifiesMatchedNewAndInvalidRegistrants() {
        RegulatoryBulkImportService svc = service();
        // one matches an existing registration, one is new, one is invalid (no reg number).
        when(registrationRepository.findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(tenant, 3L, "GN-1"))
                .thenReturn(Optional.of(new ProviderCouncilRegistrationRecordEntity()));
        when(registrationRepository.findFirstByTenantIdAndCouncil_IdAndRegistrationNumberIgnoreCase(tenant, 3L, "GN-2"))
                .thenReturn(Optional.empty());

        var captured = new java.util.ArrayList<RegulatoryImportRowEntity>();
        when(rowRepository.save(any())).thenAnswer(i -> { captured.add(i.getArgument(0)); return i.getArgument(0); });

        svc.stage(tenant, 3L, "REGISTRANT", "NCZ 2024", "ncz.csv", "steward", List.of(
                Map.of("registrationNumber", "GN-1", "registrationCategory", "GENERAL"),
                Map.of("registrationNumber", "GN-2"),
                Map.of("registrationCategory", "GENERAL"))); // no reg number → INVALID

        assertThat(captured).hasSize(3);
        assertThat(captured.get(0).getMatchStatus()).isEqualTo("MATCHED_EXISTING");
        assertThat(captured.get(1).getMatchStatus()).isEqualTo("NEW");
        assertThat(captured.get(2).getMatchStatus()).isEqualTo("INVALID");
    }

    @Test
    void applySkipsRowWithNoResolvableProvider_grantsNoAuthority() {
        var batch = new zw.gov.mohcc.impilo.varapi.persistence.entity.RegulatoryImportBatchEntity();
        batch.setTenantId(tenant); batch.setCouncilId(3L); batch.setRecordType("REGISTRANT");
        when(batchRepository.findById(9L)).thenReturn(Optional.of(batch));
        when(councilRepository.findById(3L)).thenReturn(Optional.of(new zw.gov.mohcc.impilo.varapi.persistence.entity.CouncilEntity()));

        RegulatoryImportRowEntity row = new RegulatoryImportRowEntity();
        row.setTenantId(tenant); row.setMatchStatus("NEW"); row.setReviewStatus("ACCEPTED");
        row.setRaw("{\"registrationNumber\":\"GN-9\"}"); // no healthId/providerPublicId → unresolvable
        when(rowRepository.findByBatchIdOrderByRowIndexAsc(9L)).thenReturn(List.of(row));

        service().apply(tenant, 9L);
        assertThat(row.getApplyStatus()).isEqualTo("SKIPPED"); // never silently granted
        assertThat(row.getMessage()).contains("grants no authority");
    }
}
