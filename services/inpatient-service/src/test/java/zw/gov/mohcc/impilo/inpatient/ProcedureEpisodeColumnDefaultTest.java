package zw.gov.mohcc.impilo.inpatient;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureEpisodeEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.entity.ProcedureSpecimenEntity;
import zw.gov.mohcc.impilo.inpatient.persistence.repository.ProcedureEpisodeRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A NOT NULL column carrying a database DEFAULT is only defaulted when the INSERT omits it.
 * Hibernate names every mapped column on every insert, so the default never reaches a
 * JPA-created row and the write fails with a not-null violation.
 *
 * <p>This bit {@code procedure_episode.setting} (V300, wave P4): the column was added
 * {@code NOT NULL DEFAULT 'THEATRE'}, nothing ever called {@code setSetting}, and every theatre
 * case created through JPA failed with an HTTP 500 — the whole intake path, elective and
 * emergency alike. It survived because this module's tests build their schema from the entities
 * ({@code ddl-auto: create-drop}, {@code flyway.enabled: false}), so a constraint that exists
 * only in a migration is invisible to them. It was caught by the theatre runtime-proof rigs
 * against real Postgres, on the first assertion of the first rig.
 *
 * <p>{@code procedure_specimen.adequacy} (V301) is the same shape done correctly, and is pinned
 * here beside it so the pair reads as one rule rather than two isolated facts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class ProcedureEpisodeColumnDefaultTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private ProcedureEpisodeRepository repo;

    @Test
    @DisplayName("a fresh episode carries the V300 setting default, so an insert never sends null")
    void freshEpisode_hasSettingDefault() {
        assertEquals("THEATRE", new ProcedureEpisodeEntity().getSetting(),
                "procedure_episode.setting is NOT NULL DEFAULT 'THEATRE'; the entity must mirror it, "
                        + "because Hibernate always names the column and so the DB default never applies");
    }

    @Test
    @DisplayName("a fresh specimen carries the V301 adequacy default")
    void freshSpecimen_hasAdequacyDefault() {
        assertEquals("NOT_ASSESSED", new ProcedureSpecimenEntity().getAdequacy(),
                "procedure_specimen.adequacy is NOT NULL DEFAULT 'NOT_ASSESSED'");
    }

    @Test
    @DisplayName("clearing the setting falls back to the default rather than to null")
    void setSettingNull_fallsBackToDefault() {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setSetting(null);
        assertEquals("THEATRE", e.getSetting(), "there is no such thing as an episode with no setting");
        e.setSetting("   ");
        assertEquals("THEATRE", e.getSetting(), "blank is not a setting either");
        e.setSetting("BEDSIDE");
        assertEquals("BEDSIDE", e.getSetting(), "a real setting must still be honoured");
    }

    @Test
    @DisplayName("an episode created the way intake creates it persists and reads back a setting")
    void minimalEpisode_persistsWithSetting() {
        ProcedureEpisodeEntity e = new ProcedureEpisodeEntity();
        e.setTenantId(TENANT);
        e.setSubjectCpid("CPID-ZW-SETTING-1");
        e.setProcedureName("Elective inguinal hernia repair");
        e.setStatus("BOOKED");
        e.setTriagePriority("ELECTIVE");
        e.setConsentStatus("PENDING");

        UUID id = repo.saveAndFlush(e).getEpisodeId();

        assertEquals("THEATRE", repo.findById(id).orElseThrow().getSetting(),
                "intake sets no setting explicitly, so the entity default is what reaches the row");
    }
}
