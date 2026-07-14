package zw.gov.mohcc.impilo.auditledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class AuditLedgerGoldenContractTest extends GoldenContractSuite {
    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/audit/records";
    }
    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/audit/records";
    }
}
