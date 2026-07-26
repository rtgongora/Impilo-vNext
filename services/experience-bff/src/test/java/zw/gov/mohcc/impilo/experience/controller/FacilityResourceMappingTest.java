package zw.gov.mohcc.impilo.experience.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The BFF's facility list mapper is an explicit ALLOW-LIST, not a passthrough.
 *
 * <p>That is the right shape — it stops registry-admin fields reaching a citizen device by
 * accident. But it also means a field added in tuso does not arrive: {@code regulatoryStatus} was
 * added to tuso's summary and still came back null on the wire, because this mapper never listed
 * it. The mobile citizen app reads this endpoint, so without the field it cannot tell an operating
 * Ministry facility from one of the 5,509 HPA registry listings, and renders both with an address
 * and a phone number as if equally confirmed.</p>
 *
 * <p>Asserted against the source rather than a mock, because a mock of this mapper would have
 * happily returned whatever shape the test author imagined — which is precisely how the field went
 * missing between two correct services.</p>
 */
class FacilityResourceMappingTest {

    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/zw/gov/mohcc/impilo/experience/controller/FacilityController.java"));
    }

    @Test
    void theListMapperProjectsRegulatoryStatus() throws Exception {
        assertThat(source())
                .as("the citizen app cannot disclose an unconfirmed facility without this field")
                .contains("putTextIfPresent(attrs, \"regulatoryStatus\", n, \"regulatoryStatus\")");
    }

    /**
     * The allow-list must keep excluding the registry-admin contact projection, which carries a
     * contact person's name and role. A citizen device should never receive it, displayed or not.
     */
    @Test
    void theListMapperStillExcludesAdminOnlyContactFields() throws Exception {
        String mapper = source();
        int start = mapper.indexOf("mapTusoSummaryToFacilityResource");
        String body = mapper.substring(start, mapper.indexOf("private static Map<String, Object> mapTusoDetailToFacilityResource"));
        assertThat(body).doesNotContain("\"contacts\"");
        assertThat(body).doesNotContain("contactName");
    }
}
