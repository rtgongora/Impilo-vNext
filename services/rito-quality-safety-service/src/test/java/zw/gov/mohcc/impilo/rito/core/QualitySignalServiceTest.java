package zw.gov.mohcc.impilo.rito.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.rito.persistence.entity.CaseEntity;
import zw.gov.mohcc.impilo.rito.persistence.entity.QualitySignalEntity;
import zw.gov.mohcc.impilo.rito.persistence.repository.QualitySignalRepository;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class QualitySignalServiceTest {

    @Autowired private QualitySignalService qualitySignalService;
    @Autowired private CaseService caseService;
    @Autowired private QualitySignalRepository qualitySignalRepository;

    @Test
    void ingestThenConvertToCaseLinksAndMarksConverted() {
        UUID tenant = UUID.randomUUID();
        UUID facility = UUID.randomUUID();

        QualitySignalEntity signal = qualitySignalService.ingest(tenant, "EXTERNAL_FINDING", "TUSO",
                "insp-123", "MODERATE", facility, null, "Cold chain gap", Map.of("temp", 12));
        assertThat(signal.getStatus()).isEqualTo("NEW");

        CaseEntity created = qualitySignalService.convertToCase(tenant, signal.getId(),
                "QUALITY_FINDING", null, "supervisor-1", "OPERATOR");
        assertThat(created.getCaseType()).isEqualTo("QUALITY_FINDING");
        assertThat(caseService.links(tenant, created.getId())).isNotEmpty();

        QualitySignalEntity reread = qualitySignalRepository.findById(signal.getId()).orElseThrow();
        assertThat(reread.getStatus()).isEqualTo("CONVERTED_TO_CASE");
        assertThat(reread.getCaseId()).isEqualTo(created.getId());
    }
}
