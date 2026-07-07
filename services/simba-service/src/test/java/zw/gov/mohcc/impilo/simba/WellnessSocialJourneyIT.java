package zw.gov.mohcc.impilo.simba;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end wellness-social journey on the real stack (H2): create a public post → it appears in a
 * viewer's feed; a SELF_HARM report escalates to a persisted care linkage. Proves the sovereign
 * social layer + safety escalation seam wire together through the web tier.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WellnessSocialJourneyIT {

    private static final String TENANT = "00000000-0000-4000-8000-000000000001";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private MockMvc mockMvc;

    private MockHttpServletRequestBuilder trust(MockHttpServletRequestBuilder b, String cpid, String type) {
        return b.header("X-Tenant-ID", TENANT)
                .header("X-Pod-ID", "national-spine")
                .header("X-Request-ID", "req-social")
                .header("X-Correlation-ID", "corr-social")
                .header("X-Actor-ID", cpid)
                .header("X-Actor-Type", type)
                .header("Idempotency-Key", "idem-" + UUID.randomUUID());
    }

    @Test
    void publicPost_appearsInViewerFeed() throws Exception {
        String author = "it-social-author-" + UUID.randomUUID();
        String viewer = "it-social-viewer-" + UUID.randomUUID();

        MvcResult postRes = mockMvc.perform(trust(post("/internal/v1/wellness/social/posts"), author, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Finished my 30-day walking journey!\",\"visibility\":\"PUBLIC_WITHIN_IMPILO\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String postId = MAPPER.readTree(postRes.getResponse().getContentAsString())
                .get("data").get("postId").asText();

        // A different viewer sees the public post in the feed.
        MvcResult feedRes = mockMvc.perform(trust(get("/internal/v1/wellness/social/feed"), viewer, "CITIZEN")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode feed = MAPPER.readTree(feedRes.getResponse().getContentAsString()).get("data");
        boolean found = false;
        for (JsonNode p : feed) {
            if (postId.equals(p.get("postId").asText())) {
                found = true;
                break;
            }
        }
        assertThat(found).as("public post should appear in another viewer's feed").isTrue();
    }

    @Test
    void milestoneWithClinicalValue_isRejected() throws Exception {
        String author = "it-social-clin-" + UUID.randomUUID();
        mockMvc.perform(trust(post("/internal/v1/wellness/social/posts"), author, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"My BP is 150/95 today\",\"visibility\":\"PUBLIC_WITHIN_IMPILO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void groupJourney_createJoinPostAnnounceModerate() throws Exception {
        String owner = "it-grp-owner-" + UUID.randomUUID();
        String member = "it-grp-member-" + UUID.randomUUID();

        // Owner creates an OPEN group.
        MvcResult groupRes = mockMvc.perform(trust(post("/internal/v1/wellness/social/groups"), owner, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Morning Walkers\",\"join_policy\":\"OPEN\",\"visibility\":\"PUBLIC\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String groupId = MAPPER.readTree(groupRes.getResponse().getContentAsString())
                .get("data").get("groupId").asText();

        // A member joins (OPEN → immediately ACTIVE).
        mockMvc.perform(trust(post("/internal/v1/wellness/social/groups/" + groupId + "/join"), member, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        // The member can post to the group.
        mockMvc.perform(trust(post("/internal/v1/wellness/social/posts"), member, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Great walk today!\",\"visibility\":\"GROUP\",\"group_id\":\"" + groupId + "\"}"))
                .andExpect(status().isCreated());

        // A non-member is denied posting to the group.
        String stranger = "it-grp-stranger-" + UUID.randomUUID();
        mockMvc.perform(trust(post("/internal/v1/wellness/social/posts"), stranger, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"hi\",\"visibility\":\"GROUP\",\"group_id\":\"" + groupId + "\"}"))
                .andExpect(status().isForbidden());

        // The owner publishes an official announcement (pinned + official post).
        mockMvc.perform(trust(post("/internal/v1/wellness/social/groups/" + groupId + "/announcements"), owner, "PROVIDER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Group walk this Saturday 8am!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.official").value(true))
                .andExpect(jsonPath("$.data.pinned").value(true));

        // A plain member cannot announce.
        mockMvc.perform(trust(post("/internal/v1/wellness/social/groups/" + groupId + "/announcements"), member, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"unauthorised\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void selfHarmReport_escalatesToCareLinkage() throws Exception {
        String reporter = "it-social-reporter-" + UUID.randomUUID();
        String owner = "it-social-atrisk-" + UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        mockMvc.perform(trust(post("/internal/v1/wellness/social/moderation/reports"), reporter, "CITIZEN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject_type": "POST",
                                  "subject_id": "%s",
                                  "subject_owner_cpid": "%s",
                                  "reason": "SELF_HARM",
                                  "detail": "worried about them"
                                }
                                """.formatted(subjectId, owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.escalated").value(true))
                .andExpect(jsonPath("$.data.careLinkageId").isNotEmpty());

        // The care linkage is persisted for the at-risk person.
        mockMvc.perform(trust(get("/internal/v1/wellness/care-linkages"), owner, "CITIZEN")
                        .param("person_cpid", owner))
                .andExpect(status().isOk());
    }
}
