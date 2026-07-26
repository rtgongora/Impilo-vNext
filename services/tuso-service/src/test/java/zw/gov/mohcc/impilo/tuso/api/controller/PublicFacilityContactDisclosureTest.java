package zw.gov.mohcc.impilo.tuso.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import zw.gov.mohcc.impilo.tuso.api.dto.FacilityContactDto;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityContactEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HAR W6 guard tests — publishing the facility's switchboard must not publish a person.
 *
 * <p>3,654 ingested contacts were rendered {@code null} on the public profile, so a citizen looking
 * at a facility with no map pin and no confirmed hours also had no way to ask whether it was open.
 * Emitting them is right. Emitting the wrong one is a disclosure incident: the register also holds
 * contacts for people, and a practitioner's personal mobile number on a public page cannot be
 * withdrawn once it is out.</p>
 */
class PublicFacilityContactDisclosureTest {

    private static final String PERSONAL_MOBILE = "+263771234567";
    private static final String FACILITY_LINE = "+263242700100";
    private static final String PERSON_NAME = "Dr Tariro Moyo";

    private static FacilityContactEntity contact(String type, String name, String phone, String role) {
        FacilityContactEntity c = new FacilityContactEntity();
        c.setContactType(type);
        c.setName(name);
        c.setPhone(phone);
        c.setRole(role);
        return c;
    }

    /** The allow-list is on FACILITY. Every other role is dropped, named or not. */
    @Test
    void onlyFacilityRoleContactsAreEverPublished() {
        List<FacilityContactDto> published = PublicFacilityController.toPublicContacts(List.of(
                contact("MOBILE", PERSON_NAME, PERSONAL_MOBILE, "PRACTITIONER_IN_CHARGE"),
                contact("MOBILE", PERSON_NAME, PERSONAL_MOBILE, "OWNER"),
                contact("MOBILE", PERSON_NAME, PERSONAL_MOBILE, null),
                contact("SWITCHBOARD", null, FACILITY_LINE, "FACILITY")));

        assertThat(published).hasSize(1);
        assertThat(published.get(0).phone()).isEqualTo(FACILITY_LINE);
    }

    /** Serialized, the projection must contain no personal number and no person's name at all. */
    @Test
    void noPersonalContactSurvivesSerialization() throws Exception {
        List<FacilityContactDto> published = PublicFacilityController.toPublicContacts(List.of(
                contact("MOBILE", PERSON_NAME, PERSONAL_MOBILE, "PRACTITIONER_IN_CHARGE"),
                contact("SWITCHBOARD", PERSON_NAME, FACILITY_LINE, "FACILITY")));

        String json = new ObjectMapper().writeValueAsString(published);
        assertThat(json).doesNotContain(PERSONAL_MOBILE);
        // Even on the FACILITY-role row, the contact person's name is dropped.
        assertThat(json).doesNotContain(PERSON_NAME);
        assertThat(json).contains(FACILITY_LINE);
    }

    /**
     * An unconfirmed HPA-legacy number is published but flagged, so the surface can say "call before
     * travelling" rather than implying the register confirmed it.
     */
    @Test
    void hpaLegacyNumbersArePublishedButNotClaimedAsVerified() {
        List<FacilityContactDto> published = PublicFacilityController.toPublicContacts(List.of(
                contact(PublicFacilityController.HPA_LEGACY_UNVERIFIED, null, FACILITY_LINE, "FACILITY"),
                contact("SWITCHBOARD", null, "+263242700200", "FACILITY")));

        assertThat(published).hasSize(2);
        assertThat(published.get(0).verified()).isFalse();
        assertThat(published.get(1).verified()).isTrue();
    }

    /** A row with neither phone nor email is noise on a public profile, not a contact. */
    @Test
    void emptyContactsAreOmitted() {
        assertThat(PublicFacilityController.toPublicContacts(List.of(
                contact("SWITCHBOARD", null, "   ", "FACILITY")))).isEmpty();
        assertThat(PublicFacilityController.toPublicContacts(List.of())).isEmpty();
        assertThat(PublicFacilityController.toPublicContacts(null)).isEmpty();
    }
}
