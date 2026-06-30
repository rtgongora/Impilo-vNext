package zw.gov.mohcc.impilo.daidzai.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AffectedSiteEntity;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.EmergencyIncidentEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class DisasterServiceTest {

    @Autowired private DisasterService service;

    @Test
    void declareDisasterCreatesCommandIncidentWithSites() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = service.declareDisaster(tenant, "DISASTER", "FLOOD", "HIGH",
                "Tokwe flooding", "River burst banks", "Chivi DDC post", "commander-1", "AREA-CHIVI");
        assertThat(inc.getIncidentType()).isEqualTo("DISASTER");
        assertThat(inc.getStatus()).isEqualTo("RESPONDING");

        AffectedSiteEntity site = service.addAffectedSite(tenant, inc.getId(), "Ward 12",
                "INDAWO-12", "NDILA-AREA-12", -20.3, 30.5, 450);
        assertThat(site.getEstimatedAffected()).isEqualTo(450);
        assertThat(service.sites(tenant, inc.getId())).hasSize(1);
        assertThat(service.listDisasters(tenant)).extracting(EmergencyIncidentEntity::getId).contains(inc.getId());
    }

    @Test
    void closeRoutesAfterActionToRito() {
        UUID tenant = UUID.randomUUID();
        EmergencyIncidentEntity inc = service.declareDisaster(tenant, "MCI", "ROAD_ACCIDENT", "CRITICAL",
                "Bus crash", "30 casualties", "Scene command", "commander-2", null);
        EmergencyIncidentEntity closed = service.closeWithAfterAction(tenant, inc.getId(), "RITO-CASE-55");
        assertThat(closed.getStatus()).isEqualTo("CLOSED");
        assertThat(closed.getRitoCaseRef()).isEqualTo("RITO-CASE-55");
        assertThat(closed.getClosedAt()).isNotNull();
    }

    @Test
    void invalidDisasterTypeRejected() {
        UUID tenant = UUID.randomUUID();
        assertThatThrownBy(() -> service.declareDisaster(tenant, "INDIVIDUAL", "X", "HIGH",
                "t", "d", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
