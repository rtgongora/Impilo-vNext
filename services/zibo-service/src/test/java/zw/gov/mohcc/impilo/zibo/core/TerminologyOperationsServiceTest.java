package zw.gov.mohcc.impilo.zibo.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactStatus;
import zw.gov.mohcc.impilo.zibo.domain.ArtifactType;
import zw.gov.mohcc.impilo.zibo.domain.AuthorityScope;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ArtifactEntity;
import zw.gov.mohcc.impilo.zibo.persistence.entity.ConceptEntity;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ArtifactRepository;
import zw.gov.mohcc.impilo.zibo.persistence.repository.ConceptRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * The operations that make ZIBO a terminology service rather than an artifact store.
 *
 * <p>13 of 15 ValueSets in the estate resolve to zero concepts today: they reference their
 * CodeSystem through {@code compose.include} without enumerating, which is valid FHIR and was
 * entirely unresolvable because there was no {@code $expand}.</p>
 *
 * <p>The tests that matter most here are the refusals. A ValueSet using {@code filter},
 * {@code exclude} or a nested reference must raise rather than return the subset the index
 * understands — a partial expansion that looks complete would silently omit codes from a
 * clinician's picker, and nothing downstream could tell.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TerminologyOperationsServiceTest {

    private static final UUID TENANT = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID NATIONAL = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String CS_URL = "https://impilo.gov.zw/fhir/CodeSystem/emergency-triage-priority";
    private static final String VS_URL = "https://impilo.gov.zw/fhir/ValueSet/emergency-triage-priority";

    @Mock private ConceptRepository conceptRepository;
    @Mock private ArtifactRepository artifactRepository;
    @Mock private MappingService mappingService;

    private TerminologyOperationsService operations;

    @BeforeEach
    void setUp() {
        operations = new TerminologyOperationsService(conceptRepository,
                new ArtifactResolutionService(artifactRepository, new SimpleMeterRegistry(),
                        NATIONAL.toString()),
                mappingService, new ObjectMapper());
    }

    private static ArtifactEntity artifact(ArtifactType type, String url, String json) {
        ArtifactEntity a = new ArtifactEntity();
        a.setArtifactId(UUID.randomUUID());
        a.setTenantId(TENANT);
        a.setFhirType(type);
        a.setCanonicalUrl(url);
        a.setVersion("1.0.0");
        a.setVersionSortKey("000001.000000.000000");
        a.setStatus(ArtifactStatus.PUBLISHED);
        a.setContentJson(json);
        return a;
    }

    private static ConceptEntity concept(String code, String display) {
        ConceptEntity c = new ConceptEntity();
        c.setConceptId(UUID.randomUUID());
        c.setAuthorityScope(AuthorityScope.TENANT);
        c.setTenantId(TENANT);
        c.setArtifactId(UUID.randomUUID());
        c.setSystem(CS_URL);
        c.setVersion("1.0.0");
        c.setCode(code);
        c.setDisplay(display);
        c.setActive(true);
        return c;
    }

    private void stubCodeSystem() {
        when(artifactRepository.findCandidatesAcrossPlanes(eq(CS_URL), any(), eq(ArtifactType.CODE_SYSTEM)))
                .thenReturn(List.of(artifact(ArtifactType.CODE_SYSTEM, CS_URL,
                        "{\"resourceType\":\"CodeSystem\",\"url\":\"" + CS_URL + "\",\"version\":\"1.0.0\"}")));
    }

    private void stubValueSet(String composeJson) {
        when(artifactRepository.findCandidatesAcrossPlanes(eq(VS_URL), any(), eq(ArtifactType.VALUE_SET)))
                .thenReturn(List.of(artifact(ArtifactType.VALUE_SET, VS_URL,
                        "{\"resourceType\":\"ValueSet\",\"url\":\"" + VS_URL
                        + "\",\"version\":\"1.0.0\",\"compose\":" + composeJson + "}")));
    }

    private <T> T withContext(java.util.function.Supplier<T> action) {
        try (MockedStatic<TrustContextHolder> holder = mockStatic(TrustContextHolder.class)) {
            TrustContext ctx = new TrustContext(TENANT, "picker", "PROVIDER", "TREATMENT", null,
                    UUID.randomUUID(), null, null, null, AccessMode.INTERNAL);
            holder.when(TrustContextHolder::require).thenReturn(ctx);
            holder.when(TrustContextHolder::get).thenReturn(ctx);
            return action.get();
        }
    }

    private static MockedStatic<TrustContextHolder> mockStatic(Class<TrustContextHolder> c) {
        return org.mockito.Mockito.mockStatic(c);
    }

    // ── $expand: the operation the 13 empty ValueSets need ────────────────────────────────────

    @Test
    void expandsAComposeOnlyValueSetFromItsCodeSystem() {
        // This is the exact shape of 12 of the 15 ValueSets in the estate: include a system,
        // enumerate nothing. They resolved to zero concepts before this existed.
        stubCodeSystem();
        stubValueSet("{\"include\":[{\"system\":\"" + CS_URL + "\"}]}");
        when(conceptRepository.countForExpansion(eq(CS_URL), eq("1.0.0"), any(), anyBoolean()))
                .thenReturn(4L);
        when(conceptRepository.findForExpansion(eq(CS_URL), eq("1.0.0"), any(), anyBoolean()))
                .thenReturn(List.of(concept("RED", "Immediate"), concept("ORANGE", "Very urgent"),
                        concept("YELLOW", "Urgent"), concept("GREEN", "Standard")));

        var expansion = withContext(() -> operations.expand(VS_URL, null, null, 20, 0, false));

        assertThat(expansion.total()).isEqualTo(4);
        assertThat(expansion.contains()).extracting("code")
                .containsExactly("RED", "ORANGE", "YELLOW", "GREEN");
    }

    @Test
    void anExplicitConceptListIsHonoured_notTheWholeSystem() {
        stubCodeSystem();
        stubValueSet("{\"include\":[{\"system\":\"" + CS_URL
                + "\",\"concept\":[{\"code\":\"RED\"},{\"code\":\"GREEN\"}]}]}");
        when(conceptRepository.findForExpansionOfCodes(eq(CS_URL), eq("1.0.0"), any(), any(), anyBoolean()))
                .thenReturn(List.of(concept("GREEN", "Standard"), concept("RED", "Immediate")));

        var expansion = withContext(() -> operations.expand(VS_URL, null, null, 20, 0, false));

        assertThat(expansion.contains()).extracting("code").containsExactlyInAnyOrder("RED", "GREEN");
    }

    // ── The refusals ──────────────────────────────────────────────────────────────────────────

    @Test
    void aFilteredValueSetIsRefused_notPartiallyExpanded() {
        // Returning the unfiltered set would hand a picker codes the ValueSet does not contain.
        stubValueSet("{\"include\":[{\"system\":\"" + CS_URL
                + "\",\"filter\":[{\"property\":\"concept\",\"op\":\"is-a\",\"value\":\"X\"}]}]}");

        assertThatThrownBy(() -> withContext(() -> operations.expand(VS_URL, null, null, 20, 0, false)))
                .isInstanceOf(TerminologyOperationsService.UnsupportedComposeException.class)
                .hasMessageContaining("filter");
    }

    @Test
    void anExcludeIsRefused_notSilentlyIncluded() {
        // The opposite failure: silently including codes the ValueSet excludes.
        stubValueSet("{\"include\":[{\"system\":\"" + CS_URL + "\"}],"
                + "\"exclude\":[{\"system\":\"" + CS_URL + "\",\"concept\":[{\"code\":\"RED\"}]}]}");

        assertThatThrownBy(() -> withContext(() -> operations.expand(VS_URL, null, null, 20, 0, false)))
                .isInstanceOf(TerminologyOperationsService.UnsupportedComposeException.class)
                .hasMessageContaining("exclude");
    }

    @Test
    void aNestedValueSetReferenceIsRefused_notOmitted() {
        stubValueSet("{\"include\":[{\"valueSet\":[\"https://example.org/ValueSet/other\"]}]}");

        assertThatThrownBy(() -> withContext(() -> operations.expand(VS_URL, null, null, 20, 0, false)))
                .isInstanceOf(TerminologyOperationsService.UnsupportedComposeException.class);
    }

    @Test
    void anUnregisteredCodeSystemExpandsToNothing_ratherThanFailing() {
        // The ValueSet is well-formed; its CodeSystem simply is not loaded yet. That is an empty
        // expansion with a reason, not a malformed request — and distinct from the refusals above.
        stubValueSet("{\"include\":[{\"system\":\"http://loinc.org\"}]}");
        when(artifactRepository.findCandidatesAcrossPlanes(eq("http://loinc.org"), any(), any()))
                .thenReturn(List.of());

        var expansion = withContext(() -> operations.expand(VS_URL, null, null, 20, 0, false));

        assertThat(expansion.contains()).isEmpty();
        assertThat(expansion.total()).isZero();
    }

    // ── $lookup and $validate-code ────────────────────────────────────────────────────────────

    @Test
    void lookupReturnsTheGovernedDisplay() {
        stubCodeSystem();
        when(conceptRepository.findForLookup(eq(CS_URL), eq("1.0.0"), eq("RED"), any()))
                .thenReturn(List.of(concept("RED", "Immediate")));

        var result = withContext(() -> operations.lookup(CS_URL, "RED", null));

        assertThat(result.found()).isTrue();
        assertThat(result.display()).isEqualTo("Immediate");
        assertThat(result.version()).isEqualTo("1.0.0");
    }

    @Test
    void lookupOfAnUnregisteredSystemIsNotFound_notAnError() {
        when(artifactRepository.findCandidatesAcrossPlanes(any(), any(), any())).thenReturn(List.of());

        var result = withContext(() -> operations.lookup("urn:zibo:order-codes", "FBC", null));

        assertThat(result.found()).isFalse();
    }

    @Test
    void aStaleDisplayIsReportedButTheCodeStaysValid() {
        // Rejecting a correct code because the caller's label is out of date would refuse good
        // clinical data over a cosmetic difference.
        stubCodeSystem();
        when(conceptRepository.findForLookup(eq(CS_URL), eq("1.0.0"), eq("RED"), any()))
                .thenReturn(List.of(concept("RED", "Immediate")));

        var result = withContext(() -> operations.validateCode(CS_URL, "RED", "Emergency", null));

        assertThat(result.result()).isTrue();
        assertThat(result.message()).contains("display differs");
        assertThat(result.display()).isEqualTo("Immediate");
    }

    @Test
    void anUnknownCodeIsInvalidAndSaysWhy() {
        stubCodeSystem();
        when(conceptRepository.findForLookup(any(), any(), any(), any())).thenReturn(List.of());

        var result = withContext(() -> operations.validateCode(CS_URL, "PUCE", null, null));

        assertThat(result.result()).isFalse();
        assertThat(result.message()).contains("PUCE");
    }
}
