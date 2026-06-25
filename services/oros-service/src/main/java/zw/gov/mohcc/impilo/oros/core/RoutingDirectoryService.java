package zw.gov.mohcc.impilo.oros.core;

import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.oros.api.dto.DestinationDto;
import zw.gov.mohcc.impilo.oros.integration.TusoClient;
import zw.gov.mohcc.impilo.oros.integration.VarapiClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Aggregates routing destinations for the diagnostic provider directory (spec §H / W3 routing):
 * internal facilities from TUSO and external providers from VARAPI. Both sources degrade to empty
 * when their registry is not configured/unreachable, so the directory is honest about what it knows.
 */
@Service
public class RoutingDirectoryService {

    private final TusoClient tusoClient;
    private final VarapiClient varapiClient;

    public RoutingDirectoryService(TusoClient tusoClient, VarapiClient varapiClient) {
        this.tusoClient = tusoClient;
        this.varapiClient = varapiClient;
    }

    public List<DestinationDto> destinations() {
        List<DestinationDto> out = new ArrayList<>();

        for (Map<String, Object> facility : tusoClient.searchFacilities()) {
            String id = str(facility.get("id"));
            String name = firstNonBlank(str(facility.get("name")), str(facility.get("facilityName")), id);
            if (id != null) {
                out.add(new DestinationDto("EXTERNAL_FACILITY", id, name, "TUSO"));
            }
        }

        for (Map<String, Object> provider : varapiClient.searchProviders()) {
            String id = str(provider.get("providerPublicId"));
            String name = composeName(provider);
            if (id != null) {
                out.add(new DestinationDto("EXTERNAL_PROVIDER", id, name, "VARAPI"));
            }
        }

        return out;
    }

    private static String composeName(Map<String, Object> provider) {
        String full = str(provider.get("fullName"));
        if (full != null) {
            return full;
        }
        String composed = (orEmpty(provider.get("givenName")) + " " + orEmpty(provider.get("familyName"))).trim();
        String profession = str(provider.get("profession"));
        if (composed.isBlank()) {
            composed = str(provider.get("providerPublicId"));
        }
        return profession != null && !profession.isBlank() ? composed + " (" + profession + ")" : composed;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static String orEmpty(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}
