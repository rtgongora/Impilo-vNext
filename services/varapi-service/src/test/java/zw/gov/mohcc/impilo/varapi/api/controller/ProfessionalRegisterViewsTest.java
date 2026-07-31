package zw.gov.mohcc.impilo.varapi.api.controller;

import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProfessionalRegisterEntity;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Provenance must survive the operator view. A thin browse that only returned code/name/status
 * made CONFIG_PACK and MIGRATION_SEED indistinguishable — which is exactly the leak the PCZ
 * fixture traps.
 */
class ProfessionalRegisterViewsTest {

    @Test
    void carriesProvenanceIncludingNullableStatutoryTitle() {
        ProfessionalRegisterEntity register = new ProfessionalRegisterEntity();
        register.setRegisterCode("NCZ_STUDENT");
        register.setName("Student Register");
        register.setStatus("ACTIVE");
        register.setRegisterAxis("STANDING");
        register.setStatutoryTitle(null);
        register.setStatutoryTitleDecisionRef("NCZ-DEC-013");
        register.setSource("CONFIG_PACK");
        register.setConfigDefinitionKey("register-student");
        register.setConfigSemanticVersion("1.0.0");
        register.setConfigContentHash("sha256:abc");
        register.setMaterialisedAt(Instant.parse("2026-07-30T04:00:00Z"));

        Map<String, Object> view = ProfessionalRegisterViews.toOperatorView(register);

        assertThat(view)
                .containsEntry("registerCode", "NCZ_STUDENT")
                .containsEntry("source", "CONFIG_PACK")
                .containsEntry("registerAxis", "STANDING")
                .containsEntry("statutoryTitle", null)
                .containsEntry("statutoryTitleDecisionRef", "NCZ-DEC-013")
                .containsEntry("configDefinitionKey", "register-student")
                .containsEntry("configSemanticVersion", "1.0.0")
                .containsEntry("materialisedAt", "2026-07-30T04:00:00Z");
    }
}
