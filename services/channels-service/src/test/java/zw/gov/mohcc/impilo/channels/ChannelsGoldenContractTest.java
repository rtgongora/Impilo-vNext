package zw.gov.mohcc.impilo.channels;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class ChannelsGoldenContractTest extends GoldenContractSuite {

    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/channels/sessions";
    }

    @Override
    protected String getCommandEndpointOverride() {
        return "/internal/v1/channels/sessions";
    }
}
