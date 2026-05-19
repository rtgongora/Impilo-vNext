package zw.gov.mohcc.impilo.surv.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.surv.persistence.entity.IntelligenceBriefEntity;
import zw.gov.mohcc.impilo.surv.persistence.repository.IntelligenceBriefRepository;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntelligenceBriefServiceTest {

    @Mock
    private IntelligenceBriefRepository briefRepository;

    @Mock
    private PublicHealthOperationsHomeService operationsHomeService;

    @Test
    void exportPdfReturnsBinaryPdfPayload() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IntelligenceBriefEntity brief = brief(tenantId);
        when(briefRepository.findById(7L)).thenReturn(Optional.of(brief));

        IntelligenceBriefService service = new IntelligenceBriefService(briefRepository, operationsHomeService);
        Map<String, Object> exported = service.export(tenantId, 7L, "PDF");

        assertThat(exported.get("format")).isEqualTo("PDF");
        assertThat(exported.get("content_type")).isEqualTo("application/pdf");
        String b64 = String.valueOf(exported.get("content_base64"));
        byte[] bytes = Base64.getDecoder().decode(b64);
        assertThat(new String(bytes, 0, 8)).isEqualTo("%PDF-1.4");
    }

    @Test
    void exportCsvReturnsMetricRows() {
        UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        IntelligenceBriefEntity brief = brief(tenantId);
        when(briefRepository.findById(8L)).thenReturn(Optional.of(brief));

        IntelligenceBriefService service = new IntelligenceBriefService(briefRepository, operationsHomeService);
        Map<String, Object> exported = service.export(tenantId, 8L, "CSV");

        String b64 = String.valueOf(exported.get("content_base64"));
        String csv = new String(Base64.getDecoder().decode(b64));
        assertThat(csv).contains("metric,value");
        assertThat(csv).contains("Active signals,2");
    }

    private IntelligenceBriefEntity brief(UUID tenantId) {
        IntelligenceBriefEntity brief = new IntelligenceBriefEntity();
        brief.setId(7L);
        brief.setTenantId(tenantId);
        brief.setTitle("Situation brief");
        brief.setBriefType("SITUATION");
        brief.setStatus(BriefStatus.DRAFT);
        brief.setGeneratedBy("tester");
        brief.setSummaryMarkdown("""
                ## Situation summary (deterministic)

                - Active signals: 2
                - Open cases: 4
                """);
        return brief;
    }
}
