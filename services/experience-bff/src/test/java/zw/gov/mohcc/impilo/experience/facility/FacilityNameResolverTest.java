package zw.gov.mohcc.impilo.experience.facility;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import zw.gov.mohcc.impilo.experience.client.TusoServiceClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacilityNameResolverTest {

    @Mock
    private TusoServiceClient tusoServiceClient;

    @InjectMocks
    private FacilityNameResolver resolver;

    @Test
    void resolve_seededUuid_returnsHospitalName() {
        assertEquals(
                "Harare Central Hospital",
                resolver.resolve("a1b2c3d4-0001-4000-8000-000000000001"));
    }

    @Test
    void resolve_unknownUuid_humanizesShortLabel() {
        assertEquals(
                "Issuance facility · B0000000",
                resolver.resolve("b0000000-0000-4000-8000-000000000001"));
    }

    @Test
    void resolve_numericTusoId_usesFacilityName() {
        when(tusoServiceClient.getFacility(42L))
                .thenReturn(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode().put("name", "Mutare Provincial Hospital"));

        assertEquals("Mutare Provincial Hospital", resolver.resolve("42"));
    }
}
