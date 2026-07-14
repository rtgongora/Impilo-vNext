package zw.gov.mohcc.impilo.search;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.companion.harness.GoldenContractSuite;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
public class SearchGoldenContractTest extends GoldenContractSuite {

    /**
     * Search requires {@code q}; discovery alone yields {@code /internal/v1/search} which 400s without it.
     */
    @Override
    protected String getReadEndpointOverride() {
        return "/internal/v1/search?q=golden-contract&size=1";
    }

    @Override
    protected boolean supportsClientTimeoutOnRead() {
        return false;
    }
}
