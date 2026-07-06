package zw.gov.mohcc.impilo.governance.orgkey;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties;
import zw.gov.mohcc.impilo.governance.mirror.OrgMirrorProperties.ReadPreference;
import zw.gov.mohcc.impilo.governance.orgkey.OrgReadPreferenceResolver.OrgReadSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrgReadPreferenceResolverTest {

    private OrgReadPreferenceResolver resolver(ReadPreference pref) {
        OrgMirrorProperties props = new OrgMirrorProperties();
        props.setReadPreference(pref);
        return new OrgReadPreferenceResolver(props);
    }

    @Test
    void defaultPreferenceIsWgv() {
        assertThat(new OrgMirrorProperties().getReadPreference()).isEqualTo(ReadPreference.WGV);
    }

    @Test
    void wgvPreference_isStrictNoOp_evenWhenMappingExists() {
        UUID wgvId = UUID.randomUUID();
        UUID orgRegistryId = UUID.randomUUID();

        OrgReadPreferenceResolver.OrgReadResolution r = resolver(ReadPreference.WGV).resolve(wgvId, orgRegistryId);

        assertThat(r.source()).isEqualTo(OrgReadSource.WGV);
        assertThat(r.organisationId()).isEqualTo(wgvId);
        assertThat(r.wgvOrganisationId()).isEqualTo(wgvId);
    }

    @Test
    void orgRegistryPreference_withMapping_resolvesToOrgRegistry() {
        UUID wgvId = UUID.randomUUID();
        UUID orgRegistryId = UUID.randomUUID();

        OrgReadPreferenceResolver.OrgReadResolution r =
                resolver(ReadPreference.ORG_REGISTRY).resolve(wgvId, orgRegistryId);

        assertThat(r.source()).isEqualTo(OrgReadSource.ORG_REGISTRY);
        assertThat(r.organisationId()).isEqualTo(orgRegistryId);
        assertThat(r.wgvOrganisationId()).isEqualTo(wgvId);
    }

    @Test
    void orgRegistryPreference_withoutMapping_fallsBackToWgv() {
        UUID wgvId = UUID.randomUUID();

        OrgReadPreferenceResolver.OrgReadResolution r =
                resolver(ReadPreference.ORG_REGISTRY).resolve(wgvId, null);

        assertThat(r.source()).isEqualTo(OrgReadSource.WGV);
        assertThat(r.organisationId()).isEqualTo(wgvId);
    }
}
