package zw.gov.mohcc.impilo.madi.v11;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class MadiGoldenContractTest extends GoldenContractSuite {

    @MockBean
    private KafkaTemplate<String, String> kafkaTemplate;

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/health";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/test-command";
    }

    @Override
    protected boolean supportsClientTimeoutOnRead() {
        return false;
    }
}
