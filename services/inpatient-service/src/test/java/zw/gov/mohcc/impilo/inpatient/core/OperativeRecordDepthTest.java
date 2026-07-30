package zw.gov.mohcc.impilo.inpatient.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SB-5 — the eleven operative-record fields the surgical audit (§13) named as absent, plus the
 * linkage to SB-4's specialty operative templates.
 *
 * <p>The point of a structured operative record is that a later reader — a reoperating surgeon, an
 * infection-surveillance officer, a coroner — can find a specific fact without reading prose. So
 * these tests check the fields survive a write and come back individually addressable, and that
 * the two values with a closed vocabulary are refused when wrong rather than accepted and left for
 * the database to reject as a 500.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("test")
class OperativeRecordDepthTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Autowired
    private TheatreService theatre;

    @BeforeEach
    void setTrustContext() {
        TrustContextHolder.set(new TrustContext(TENANT, "actor-surgeon", "PROVIDER", "TREATMENT",
                null, UUID.randomUUID(), UUID.randomUUID(), null, null, AccessMode.INTERNAL));
    }

    @AfterEach
    void clearTrustContext() {
        TrustContextHolder.clear();
    }

    private UUID anEpisodeInTheatre() {
        Map<String, Object> intake = new LinkedHashMap<>();
        intake.put("patientId", "CPID-ZW-SB5-" + UUID.randomUUID());
        intake.put("procedureName", "Elective inguinal hernia repair");
        intake.put("triagePriority", "ELECTIVE");
        Object id = theatre.intakeFromOrosOrder(intake).get("id");
        assertNotNull(id, "intake must return an episode id");
        return UUID.fromString(String.valueOf(id));
    }

    private Map<String, Object> fullOperativeNote() {
        Map<String, Object> body = new HashMap<>();
        body.put("performedProcedure", "Open right inguinal hernia repair with mesh");
        body.put("findings", "Indirect sac, no strangulation");
        body.put("patientPosition", "Supine, arms out at 70 degrees");
        body.put("skinPreparation", "Chlorhexidine 2% in alcohol, three coats, allowed to dry");
        body.put("incision", "Transverse right groin crease, 6 cm");
        body.put("operativeSteps", "Sac dissected free and reduced; mesh laid and fixed; layers closed");
        body.put("operativeTechnique", "OPEN");
        body.put("intraoperativeFluids", "Hartmann's 500 ml");
        body.put("drainsPlaced", "None");
        body.put("stomasFormed", "None");
        body.put("closureMethod", "Mass closure 1 PDS, skin 3/0 Monocryl subcuticular");
        body.put("woundClassification", "CLEAN");
        body.put("postoperativeInstructions", "No lifting over 5 kg for six weeks; remove dressing day 5");
        return body;
    }

    @Test
    @DisplayName("all eleven operative fields survive a draft and read back individually")
    void allElevenFieldsRoundTrip() {
        UUID episode = anEpisodeInTheatre();
        Map<String, Object> note = theatre.draftNote(episode, fullOperativeNote());

        assertEquals("Supine, arms out at 70 degrees", note.get("patient_position"));
        assertEquals("Chlorhexidine 2% in alcohol, three coats, allowed to dry", note.get("skin_preparation"));
        assertEquals("Transverse right groin crease, 6 cm", note.get("incision"));
        assertEquals("Sac dissected free and reduced; mesh laid and fixed; layers closed", note.get("operative_steps"));
        assertEquals("OPEN", note.get("operative_technique"));
        assertEquals("Hartmann's 500 ml", note.get("intraoperative_fluids"));
        assertEquals("None", note.get("drains_placed"));
        assertEquals("None", note.get("stomas_formed"));
        assertEquals("Mass closure 1 PDS, skin 3/0 Monocryl subcuticular", note.get("closure_method"));
        assertEquals("CLEAN", note.get("wound_classification"));
        assertEquals("No lifting over 5 kg for six weeks; remove dressing day 5",
                note.get("postoperative_instructions"));
    }

    @Test
    @DisplayName("the eleven are addressable in their own right, not buried in the note_json blob")
    void fieldsAreStructuredNotOnlyBlobbed() {
        UUID episode = anEpisodeInTheatre();
        Map<String, Object> note = theatre.draftNote(episode, fullOperativeNote());

        // The whole value of SB-5 is that a reader does not have to parse prose to find one fact.
        for (String key : new String[]{"patient_position", "skin_preparation", "incision", "operative_steps",
                "operative_technique", "intraoperative_fluids", "drains_placed", "stomas_formed",
                "closure_method", "wound_classification", "postoperative_instructions"}) {
            assertTrue(note.containsKey(key), "the note projection must expose " + key + " as its own field");
        }
    }

    @Test
    @DisplayName("a wound classification outside the CDC vocabulary is refused, not stored")
    void woundClassificationVocabularyIsClosed() {
        UUID episode = anEpisodeInTheatre();
        Map<String, Object> body = fullOperativeNote();
        body.put("woundClassification", "FAIRLY_CLEAN");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> theatre.draftNote(episode, body),
                "an invented wound class must be refused — surgical-site-infection surveillance "
                        + "depends on these four values meaning the same thing everywhere");
        assertEquals(400, ex.getStatusCode().value(),
                "the caller supplied a bad value, so this is a 400, not a 500 from the CHECK");
    }

    @Test
    @DisplayName("wound classification remains optional — an incomplete note is not an invalid one")
    void woundClassificationIsOptional() {
        UUID episode = anEpisodeInTheatre();
        Map<String, Object> body = fullOperativeNote();
        body.remove("woundClassification");

        Map<String, Object> note = theatre.draftNote(episode, body);
        assertNull(note.get("wound_classification"),
                "a surgeon part-way through a note must not be blocked by a field they have not reached");
    }

    @Test
    @DisplayName("a template reference is the id and the code together, or neither")
    void templateReferenceMustBeAPair() {
        UUID episode = anEpisodeInTheatre();

        Map<String, Object> idOnly = fullOperativeNote();
        idOnly.put("operativeTemplateRef", UUID.randomUUID().toString());
        ResponseStatusException noCode = assertThrows(ResponseStatusException.class,
                () -> theatre.draftNote(episode, idOnly),
                "an id with no code cannot be read when surgery-service is unreachable");
        assertEquals(400, noCode.getStatusCode().value());

        Map<String, Object> codeOnly = fullOperativeNote();
        codeOnly.put("operativeTemplateCode", "GS-HERNIA-OPEN");
        ResponseStatusException noId = assertThrows(ResponseStatusException.class,
                () -> theatre.draftNote(episode, codeOnly),
                "a code with no id cannot be resolved back to the template row");
        assertEquals(400, noId.getStatusCode().value());
    }

    @Test
    @DisplayName("a complete template reference is recorded, keeping the note readable without a cross-service join")
    void completeTemplateReferenceIsRecorded() {
        UUID episode = anEpisodeInTheatre();
        UUID templateId = UUID.randomUUID();
        Map<String, Object> body = fullOperativeNote();
        body.put("operativeTemplateRef", templateId.toString());
        body.put("operativeTemplateCode", "GS-HERNIA-OPEN");

        Map<String, Object> note = theatre.draftNote(episode, body);

        assertEquals(templateId.toString(), note.get("operative_template_ref"));
        assertEquals("GS-HERNIA-OPEN", note.get("operative_template_code"),
                "the code is denormalised on purpose: the note must stay auditable when the "
                        + "owning service is unreachable");
    }
}
