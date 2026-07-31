package zw.gov.mohcc.impilo.experience.workhome;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards against reintroducing fictional Work Home hrefs that 404 in one-ui-shell.
 */
class WorkHomeHrefContractTest {

    private static String source(String simpleName) throws Exception {
        Path path = Path.of("src/main/java/zw/gov/mohcc/impilo/experience/workhome/" + simpleName + ".java");
        return Files.readString(path);
    }

    @Test
    void adaptersDoNotEmitObsoleteWorkHomeHrefs() throws Exception {
        assertThat(source("DepartmentWorkHomeAdapter")).doesNotContain("\"/facility/departments/\"");
        assertThat(source("FacilityManagementWorkHomeAdapter")).doesNotContain("\"/facility/status\"");
        assertThat(source("FacilityManagementWorkHomeAdapter")).doesNotContain("\"/facility/admin-claims\"");
        assertThat(source("ProgrammeWorkHomeAdapter")).doesNotContain("\"/programme/profile\"");
        assertThat(source("OversightWorkHomeAdapter")).doesNotContain("\"/oversight/jurisdiction\"");
        assertThat(source("ProfessionalAlertsComposer")).doesNotContain("\"/my-professional/");
        assertThat(source("RegulatoryWorkHomeAdapter")).doesNotContain("\"/regulatory/appointments\"");

        assertThat(source("DepartmentWorkHomeAdapter")).contains("/facility/\" + facilityUuid + \"/departments");
        assertThat(source("FacilityManagementWorkHomeAdapter")).contains("/facility/\" + facilityUuid + \"/mode");
        assertThat(source("ProgrammeWorkHomeAdapter")).contains("\"/programme/\" + id");
        assertThat(source("OversightWorkHomeAdapter")).contains("\"/public-health/oversight\"");
        assertThat(source("ProfessionalAlertsComposer")).contains("\"/professional\"");
        assertThat(source("RegulatoryWorkHomeAdapter")).contains("\"/work/regulatory\"");
        assertThat(source("SupportWorkHomeAdapter")).contains("\"/support/tickets\"");
    }
}
